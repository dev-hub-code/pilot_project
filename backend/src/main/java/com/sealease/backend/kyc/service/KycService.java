package com.sealease.backend.kyc.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.api.Assignee;
import com.sealease.backend.common.crypto.FieldEncryptor;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.common.ratelimit.FixedWindowRateLimiter;
import com.sealease.backend.document.entity.DocumentPurpose;
import com.sealease.backend.document.service.DocumentContent;
import com.sealease.backend.document.service.DocumentService;
import com.sealease.backend.kyc.dto.KycFiles;
import com.sealease.backend.kyc.dto.KycReviewDetail;
import com.sealease.backend.kyc.dto.KycSubmissionRequest;
import com.sealease.backend.kyc.dto.KycSubmissionResponse;
import com.sealease.backend.kyc.entity.KycDocument;
import com.sealease.backend.kyc.entity.KycDocumentId;
import com.sealease.backend.kyc.entity.KycSubmission;
import com.sealease.backend.kyc.entity.KycSubmissionStatus;
import com.sealease.backend.kyc.repository.KycDocumentRepository;
import com.sealease.backend.kyc.repository.KycSubmissionRepository;
import com.sealease.backend.notification.service.NotificationService;
import com.sealease.backend.user.dto.ProfileResponse;
import com.sealease.backend.user.dto.UserAccount;
import com.sealease.backend.user.entity.KycStatus;
import com.sealease.backend.user.service.UserAccountService;
import com.sealease.backend.user.service.UserProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Identity verification: investors submit identity details and evidence; reviewers holding
 * KYC_REVIEW approve or reject. Reviewers can never decide their own submission, and every
 * document view and decision is audited.
 */
@Service
public class KycService {

	static final String AADHAAR_NUMBER_CONTEXT = "kyc_submission.aadhaar_number";
	static final String PAN_NUMBER_CONTEXT = "kyc_submission.pan_number";
	/** The single identity document number of submissions made before Aadhaar and PAN. */
	static final String LEGACY_DOCUMENT_NUMBER_CONTEXT = "kyc_submission.document_number";
	static final String AADHAAR_FINGERPRINT_CONTEXT = "kyc_submission.aadhaar_fingerprint";
	static final String PAN_FINGERPRINT_CONTEXT = "kyc_submission.pan_fingerprint";
	private static final int MINIMUM_AGE = 18;
	private static final Logger log = LoggerFactory.getLogger(KycService.class);

	private final KycSubmissionRepository submissions;
	private final KycDocumentRepository kycDocuments;
	private final DocumentService documents;
	private final UserProfileService profiles;
	private final UserAccountService accounts;
	private final FieldEncryptor encryptor;
	private final AuditService audit;
	private final NotificationService notifications;
	private final Clock clock;
	private final FixedWindowRateLimiter submissionsPerUser;
	private final TransactionTemplate transactions;
	private final KycFollowUpService followUps;

	public KycService(KycSubmissionRepository submissions, KycDocumentRepository kycDocuments,
			DocumentService documents, UserProfileService profiles, UserAccountService accounts,
			FieldEncryptor encryptor, AuditService audit, NotificationService notifications, Clock clock,
			TransactionTemplate transactions, KycFollowUpService followUps) {
		this.submissions = submissions;
		this.kycDocuments = kycDocuments;
		this.documents = documents;
		this.profiles = profiles;
		this.accounts = accounts;
		this.encryptor = encryptor;
		this.audit = audit;
		this.notifications = notifications;
		this.clock = clock;
		this.submissionsPerUser = new FixedWindowRateLimiter(5, Duration.ofDays(1), clock);
		this.transactions = transactions;
		this.followUps = followUps;
	}

	/**
	 * Uploads the Aadhaar card, the PAN card, or both, each with its number. They may come at different times: the
	 * first starts a draft, and the submission goes for review once it holds both. While a draft, a document uploaded
	 * again replaces the earlier one.
	 */
	@Transactional
	public KycSubmissionResponse submit(UUID userId, KycSubmissionRequest request, KycFiles files) {
		submissionsPerUser.acquire(userId.toString());
		accounts.requireEmailVerified(userId);
		KycStatus current = profiles.kycStatusOf(userId);
		if (current == KycStatus.PENDING) {
			throw new BusinessException(ErrorCode.CONFLICT, "A verification is already under review");
		}
		if (current == KycStatus.APPROVED) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Your identity is already verified");
		}
		boolean withAadhaar = given(request.aadhaarNumber(), files.aadhaarCard(), "Aadhaar number", "Aadhaar card");
		boolean withPan = given(request.panNumber(), files.panCard(), "PAN", "PAN card");
		if (!withAadhaar && !withPan) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Upload your Aadhaar card or your PAN card");
		}
		// Name (as registered), date of birth and nationality come from the profile, recorded with the submission.
		ProfileResponse profile = profiles.getProfile(userId);
		List<String> missing = profile.missingPersonalDetails();
		if (!missing.isEmpty()) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"Complete your personal details first (Profile, Personal details): " + String.join(", ", missing));
		}
		LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
		if (profile.dateOfBirth().isAfter(today.minusYears(MINIMUM_AGE))) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "You must be at least " + MINIMUM_AGE + " years old");
		}
		String aadhaar = null;
		String pan = null;
		if (withAadhaar) {
			aadhaar = IndianIds.normalizeAadhaar(request.aadhaarNumber());
			if (!IndianIds.isValidAadhaar(aadhaar)) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED, "An Aadhaar number has exactly 12 digits");
			}
			// Not revealing which account: only that the number is taken.
			if (submissions.aadhaarHeldByAnotherUser(aadhaarFingerprint(aadhaar), userId)) {
				throw new BusinessException(ErrorCode.CONFLICT,
						"This Aadhaar number is already registered with another account. Contact support if it is yours.");
			}
		}
		if (withPan) {
			pan = IndianIds.normalizePan(request.panNumber());
			if (!IndianIds.isValidPan(pan)) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED,
						"That PAN is not valid: it has 5 letters, 4 digits and a letter, e.g. ABCDE1234F");
			}
			if (submissions.panHeldByAnotherUser(panFingerprint(pan), userId)) {
				throw new BusinessException(ErrorCode.CONFLICT,
						"This PAN is already registered with another account. Contact support if it is yours.");
			}
		}

		// A new draft is saved once it holds its first document.
		KycSubmission submission = submissions.findDraftForUpdate(userId).orElseGet(() -> KycSubmission.draft(userId,
				profile.firstName(), profile.lastName(), profile.dateOfBirth(), profile.nationality(), clock.instant()));
		Map<DocumentPurpose, MultipartFile> uploaded = new LinkedHashMap<>();
		if (aadhaar != null) {
			submission.setAadhaar(encryptor.encrypt(aadhaar, AADHAAR_NUMBER_CONTEXT), FieldEncryptor.lastChars(aadhaar, 4),
					aadhaarFingerprint(aadhaar));
			uploaded.put(DocumentPurpose.KYC_AADHAAR_CARD, files.aadhaarCard());
		}
		if (pan != null) {
			submission.setPan(encryptor.encrypt(pan, PAN_NUMBER_CONTEXT), FieldEncryptor.lastChars(pan, 4), panFingerprint(pan));
			uploaded.put(DocumentPurpose.KYC_PAN_CARD, files.panCard());
		}
		submissions.saveAndFlush(submission);
		uploaded.forEach((purpose, file) -> {
			kycDocuments.deleteFromSubmission(submission.getId(), purpose);
			UUID documentId = documents.store(userId, purpose, file);
			kycDocuments.save(new KycDocument(new KycDocumentId(submission.getId(), documentId), purpose));
		});
		audit.record(AuditRecord.of(userId, AuditAction.KYC_DOCUMENT_UPLOADED, "KYC_SUBMISSION", submission.getId())
			.withNewValue(Map.of("documents", uploaded.keySet())));

		if (submission.hasAadhaar() && submission.hasPan()) {
			submission.submitForReview(clock.instant());
			try {
				submissions.flush();
			}
			catch (DataIntegrityViolationException ex) {
				// Another account's submission took one of the numbers since they were checked.
				throw new BusinessException(ErrorCode.CONFLICT,
						"This Aadhaar number or PAN is already registered with another account. Contact support if it is yours.");
			}
			profiles.markKycStatus(userId, KycStatus.PENDING);
			followUps.documentsSubmitted(userId);
			audit.record(AuditRecord.of(userId, AuditAction.KYC_SUBMITTED, "KYC_SUBMISSION", submission.getId())
				.withNewValue(Map.of("documents", List.of(DocumentPurpose.KYC_AADHAAR_CARD, DocumentPurpose.KYC_PAN_CARD))));
		}
		submissions.flush();
		return response(submission).forOwner();
	}

	/** Whether a document is being uploaded now: its number and its file come together, or neither. */
	private static boolean given(String number, MultipartFile file, String numberLabel, String fileLabel) {
		boolean hasNumber = number != null && !number.isBlank();
		if (hasNumber != present(file)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, hasNumber
					? "Upload your " + fileLabel + " with the " + numberLabel
					: "Enter your " + numberLabel + " with the " + fileLabel);
		}
		return hasNumber;
	}

	@Transactional(readOnly = true)
	public Optional<KycSubmissionResponse> latestFor(UUID userId) {
		return submissions.findFirstByUserIdOrderBySubmittedAtDesc(userId).map(s -> response(s).forOwner());
	}

	@Transactional(readOnly = true)
	public List<KycSubmissionResponse> historyFor(UUID userId) {
		return submissions.findByUserIdOrderBySubmittedAtDesc(userId).stream().map(this::response).toList();
	}

	@Transactional(readOnly = true)
	public Page<KycSubmissionResponse> queue(KycSubmissionStatus status, Pageable pageable) {
		return submissions.findByStatus(status, pageable).map(this::response);
	}

	/** A reviewer opens a submission, with its full identity numbers: every view is audited. */
	@Transactional
	public KycReviewDetail detail(UUID reviewerId, UUID submissionId) {
		KycSubmission submission = load(submissionId);
		audit.record(AuditRecord.of(reviewerId, AuditAction.KYC_SUBMISSION_VIEWED, "KYC_SUBMISSION", submissionId));
		return new KycReviewDetail(response(submission), accounts.getAccount(submission.getUserId()));
	}

	/** Decrypts one piece of evidence for a reviewer. Every access is audited. */
	@Transactional
	public DocumentContent viewDocument(UUID reviewerId, UUID submissionId, UUID documentId) {
		if (!kycDocuments.existsById(new KycDocumentId(submissionId, documentId))) {
			throw new ResourceNotFoundException("Document", documentId);
		}
		DocumentContent content = documents.load(documentId);
		audit.record(AuditRecord.of(reviewerId, AuditAction.KYC_DOCUMENT_VIEWED, "KYC_SUBMISSION", submissionId)
			.withNewValue(Map.of("documentId", documentId.toString(), "purpose", content.purpose())));
		return content;
	}

	/**
	 * A reviewer takes a submission waiting for review: from now on only they may approve or reject it. Taking one
	 * already assigned to you changes nothing; one assigned to another reviewer must be given back by them first.
	 */
	@Transactional
	public KycSubmissionResponse assign(UUID reviewerId, UUID submissionId) {
		KycSubmission submission = pending(reviewerId, submissionId);
		if (submission.isAssignedTo(reviewerId)) {
			return response(submission);
		}
		if (submission.getAssignedTo() != null) {
			throw new BusinessException(ErrorCode.CONFLICT,
					"This review is already assigned to " + assignee(submission).name());
		}
		submission.assignTo(reviewerId, clock.instant());
		audit.record(AuditRecord.of(reviewerId, AuditAction.KYC_ASSIGNED, "KYC_SUBMISSION", submissionId)
			.withNewValue(Map.of("userId", submission.getUserId().toString())));
		submissions.flush();
		return response(submission);
	}

	/** The assigned reviewer gives a submission back to the queue, undecided, for another reviewer to take. */
	@Transactional
	public KycSubmissionResponse unassign(UUID reviewerId, UUID submissionId) {
		KycSubmission submission = pendingForReview(reviewerId, submissionId);
		submission.unassign();
		audit.record(AuditRecord.of(reviewerId, AuditAction.KYC_UNASSIGNED, "KYC_SUBMISSION", submissionId)
			.withNewValue(Map.of("userId", submission.getUserId().toString())));
		submissions.flush();
		return response(submission);
	}

	@Transactional
	public KycSubmissionResponse approve(UUID reviewerId, UUID submissionId) {
		KycSubmission submission = pendingForReview(reviewerId, submissionId);
		submission.approve(reviewerId, clock.instant());
		profiles.markKycStatus(submission.getUserId(), KycStatus.APPROVED);
		audit.record(AuditRecord.of(reviewerId, AuditAction.KYC_APPROVED, "KYC_SUBMISSION", submissionId)
			.withNewValue(Map.of("userId", submission.getUserId().toString())));
		notifications.notify(submission.getUserId(), "KYC_APPROVED", "Your identity is verified",
				"Your Aadhaar and PAN were checked and approved. You can now add a bank account for payouts and invest.",
				"/profile/verification");
		submissions.flush();
		return response(submission);
	}

	@Transactional
	public KycSubmissionResponse reject(UUID reviewerId, UUID submissionId, String reason) {
		KycSubmission submission = pendingForReview(reviewerId, submissionId);
		submission.reject(reviewerId, reason.strip(), clock.instant());
		profiles.markKycStatus(submission.getUserId(), KycStatus.REJECTED);
		audit.record(AuditRecord.of(reviewerId, AuditAction.KYC_REJECTED, "KYC_SUBMISSION", submissionId)
			.withNewValue(Map.of("userId", submission.getUserId().toString(), "reason", reason.strip())));
		notifications.notify(submission.getUserId(), "KYC_REJECTED", "Your identity verification needs attention",
				"We could not approve your verification. Reason: " + sentence(reason) + " Please correct this and submit it again.",
				"/profile/verification");
		submissions.flush();
		return response(submission);
	}

	/** The reviewer's reason as one sentence, ending in a full stop. */
	private static String sentence(String reason) {
		return reason.strip().replaceAll("[.!?]+$", "") + ".";
	}

	/** A pending submission the reviewer has assigned to themselves: only they may decide it. */
	private KycSubmission pendingForReview(UUID reviewerId, UUID submissionId) {
		KycSubmission submission = pending(reviewerId, submissionId);
		if (!submission.isAssignedTo(reviewerId)) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, submission.getAssignedTo() == null
					? "Assign this review to yourself before deciding it"
					: "This review is assigned to " + assignee(submission).name());
		}
		return submission;
	}

	private KycSubmission pending(UUID reviewerId, UUID submissionId) {
		KycSubmission submission = submissions.findByIdForUpdate(submissionId)
			.orElseThrow(() -> new ResourceNotFoundException("KYC submission", submissionId));
		if (submission.getUserId().equals(reviewerId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "You cannot review your own verification");
		}
		if (submission.getStatus() != KycSubmissionStatus.PENDING) {
			throw new BusinessException(ErrorCode.CONFLICT, "Submission was already " + submission.getStatus());
		}
		return submission;
	}

	private static boolean present(MultipartFile file) {
		return file != null && !file.isEmpty();
	}

	private KycSubmission load(UUID submissionId) {
		return submissions.findById(submissionId)
			.orElseThrow(() -> new ResourceNotFoundException("KYC submission", submissionId));
	}

	/**
	 * Fingerprints submissions made before fingerprints were kept, so their numbers count as taken. Each in its own
	 * transaction; one whose number another active submission already holds is left as it is, and logged.
	 */
	public int fingerprintEarlierSubmissions() {
		int done = 0;
		for (UUID id : transactions.execute(status -> submissions.findUnfingerprinted().stream().map(KycSubmission::getId).toList())) {
			try {
				transactions.executeWithoutResult(status -> {
					KycSubmission s = submissions.findByIdForUpdate(id).orElseThrow();
					s.fingerprint(aadhaarFingerprint(encryptor.decrypt(s.getAadhaarNumberEncrypted(), AADHAAR_NUMBER_CONTEXT)),
							panFingerprint(encryptor.decrypt(s.getPanNumberEncrypted(), PAN_NUMBER_CONTEXT)));
					submissions.flush();
				});
				done++;
			}
			catch (DataIntegrityViolationException ex) {
				log.warn("KYC submission {} shares an Aadhaar number or PAN with another active submission; review it", id);
			}
		}
		return done;
	}

	private String aadhaarFingerprint(String normalizedAadhaar) {
		return encryptor.fingerprint(normalizedAadhaar, AADHAAR_FINGERPRINT_CONTEXT);
	}

	private String panFingerprint(String normalizedPan) {
		return encryptor.fingerprint(normalizedPan, PAN_FINGERPRINT_CONTEXT);
	}

	private KycSubmissionResponse response(KycSubmission submission) {
		return KycSubmissionResponse.from(submission, kycDocuments.findByIdSubmissionId(submission.getId()),
				new KycSubmissionResponse.Numbers(decrypt(submission.getAadhaarNumberEncrypted(), AADHAAR_NUMBER_CONTEXT),
						decrypt(submission.getPanNumberEncrypted(), PAN_NUMBER_CONTEXT),
						decrypt(submission.getDocumentNumberEncrypted(), LEGACY_DOCUMENT_NUMBER_CONTEXT)),
				assignee(submission));
	}

	private Assignee assignee(KycSubmission submission) {
		if (submission.getAssignedTo() == null) {
			return null;
		}
		UserAccount reviewer = accounts.getAccount(submission.getAssignedTo());
		return new Assignee(reviewer.id(), reviewer.firstName() + " " + reviewer.lastName(), submission.getAssignedAt());
	}

	private String decrypt(String encrypted, String context) {
		return encrypted == null ? null : encryptor.decrypt(encrypted, context);
	}

}
