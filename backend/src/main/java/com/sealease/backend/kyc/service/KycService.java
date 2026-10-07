package com.sealease.backend.kyc.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
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
import com.sealease.backend.user.entity.KycStatus;
import com.sealease.backend.user.service.UserAccountService;
import com.sealease.backend.user.service.UserProfileService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

	static final String DOCUMENT_NUMBER_CONTEXT = "kyc_submission.document_number";
	private static final int MINIMUM_AGE = 18;

	private final KycSubmissionRepository submissions;
	private final KycDocumentRepository kycDocuments;
	private final DocumentService documents;
	private final UserProfileService profiles;
	private final UserAccountService accounts;
	private final FieldEncryptor encryptor;
	private final AuditService audit;
	private final Clock clock;
	private final FixedWindowRateLimiter submissionsPerUser;

	public KycService(KycSubmissionRepository submissions, KycDocumentRepository kycDocuments,
			DocumentService documents, UserProfileService profiles, UserAccountService accounts,
			FieldEncryptor encryptor, AuditService audit, Clock clock) {
		this.submissions = submissions;
		this.kycDocuments = kycDocuments;
		this.documents = documents;
		this.profiles = profiles;
		this.accounts = accounts;
		this.encryptor = encryptor;
		this.audit = audit;
		this.clock = clock;
		this.submissionsPerUser = new FixedWindowRateLimiter(5, Duration.ofDays(1), clock);
	}

	@Transactional
	public KycSubmissionResponse submit(UUID userId, KycSubmissionRequest request, KycFiles files) {
		submissionsPerUser.acquire(userId.toString());
		KycStatus current = profiles.kycStatusOf(userId);
		if (current == KycStatus.PENDING) {
			throw new BusinessException(ErrorCode.CONFLICT, "A verification is already under review");
		}
		if (current == KycStatus.APPROVED) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Your identity is already verified");
		}
		LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
		if (request.dateOfBirth().isAfter(today.minusYears(MINIMUM_AGE))) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "You must be at least " + MINIMUM_AGE + " years old");
		}
		Map<DocumentPurpose, MultipartFile> evidence = evidence(request, files);

		String documentNumber = request.documentNumber().strip().toUpperCase();
		String compact = documentNumber.replace(" ", "").replace("-", "");
		KycSubmission submission = submissions.saveAndFlush(new KycSubmission(userId, request.legalFirstName().strip(),
				request.legalLastName().strip(), request.dateOfBirth(), request.nationality(), request.documentType(),
				encryptor.encrypt(documentNumber, DOCUMENT_NUMBER_CONTEXT), FieldEncryptor.lastChars(compact, 4),
				request.documentIssuingCountry(), request.documentExpiryDate(), clock.instant()));

		evidence.forEach((purpose, file) -> {
			UUID documentId = documents.store(userId, purpose, file);
			kycDocuments.save(new KycDocument(new KycDocumentId(submission.getId(), documentId), purpose));
		});
		profiles.markKycStatus(userId, KycStatus.PENDING);
		audit.record(AuditRecord.of(userId, AuditAction.KYC_SUBMITTED, "KYC_SUBMISSION", submission.getId())
			.withNewValue(Map.of("documentType", request.documentType(), "documents", evidence.keySet())));
		return response(submission).forOwner();
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

	@Transactional(readOnly = true)
	public KycReviewDetail detail(UUID submissionId) {
		KycSubmission submission = load(submissionId);
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

	@Transactional
	public KycSubmissionResponse approve(UUID reviewerId, UUID submissionId) {
		KycSubmission submission = pendingForReview(reviewerId, submissionId);
		submission.approve(reviewerId, clock.instant());
		profiles.markKycStatus(submission.getUserId(), KycStatus.APPROVED);
		audit.record(AuditRecord.of(reviewerId, AuditAction.KYC_APPROVED, "KYC_SUBMISSION", submissionId)
			.withNewValue(Map.of("userId", submission.getUserId().toString())));
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
		submissions.flush();
		return response(submission);
	}

	private KycSubmission pendingForReview(UUID reviewerId, UUID submissionId) {
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

	private static Map<DocumentPurpose, MultipartFile> evidence(KycSubmissionRequest request, KycFiles files) {
		Map<DocumentPurpose, MultipartFile> evidence = new LinkedHashMap<>();
		require(evidence, DocumentPurpose.KYC_IDENTITY_FRONT, files.identityFront(), "Front image of the document");
		if (request.documentType().requiresBackImage()) {
			require(evidence, DocumentPurpose.KYC_IDENTITY_BACK, files.identityBack(), "Back image of the document");
		}
		else if (present(files.identityBack())) {
			evidence.put(DocumentPurpose.KYC_IDENTITY_BACK, files.identityBack());
		}
		require(evidence, DocumentPurpose.KYC_SELFIE, files.selfie(), "A selfie holding the document");
		if (present(files.proofOfAddress())) {
			evidence.put(DocumentPurpose.KYC_PROOF_OF_ADDRESS, files.proofOfAddress());
		}
		return evidence;
	}

	private static void require(Map<DocumentPurpose, MultipartFile> evidence, DocumentPurpose purpose,
			MultipartFile file, String label) {
		if (!present(file)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, label + " is required");
		}
		evidence.put(purpose, file);
	}

	private static boolean present(MultipartFile file) {
		return file != null && !file.isEmpty();
	}

	private KycSubmission load(UUID submissionId) {
		return submissions.findById(submissionId)
			.orElseThrow(() -> new ResourceNotFoundException("KYC submission", submissionId));
	}

	private KycSubmissionResponse response(KycSubmission submission) {
		return KycSubmissionResponse.from(submission, kycDocuments.findByIdSubmissionId(submission.getId()));
	}

}
