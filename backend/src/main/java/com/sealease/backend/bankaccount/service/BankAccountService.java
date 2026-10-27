package com.sealease.backend.bankaccount.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.bankaccount.dto.AddBankAccountRequest;
import com.sealease.backend.bankaccount.dto.AdminBankAccountResponse;
import com.sealease.backend.bankaccount.dto.BankAccountResponse;
import com.sealease.backend.bankaccount.dto.PayoutAccount;
import com.sealease.backend.bankaccount.dto.PayoutInstruction;
import com.sealease.backend.bankaccount.entity.BankAccount;
import com.sealease.backend.bankaccount.entity.BankAccountStatus;
import com.sealease.backend.bankaccount.entity.BankProofType;
import com.sealease.backend.bankaccount.repository.BankAccountRepository;
import com.sealease.backend.common.api.Assignee;
import com.sealease.backend.common.crypto.FieldEncryptor;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.document.service.DocumentContent;
import com.sealease.backend.document.service.DocumentService;
import com.sealease.backend.notification.service.NotificationService;
import com.sealease.backend.user.dto.UserAccount;
import com.sealease.backend.user.entity.KycStatus;
import com.sealease.backend.user.service.UserAccountService;
import com.sealease.backend.user.service.UserProfileService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Investor payout accounts. Owners add and remove accounts and choose which verified account is primary; a
 * verified account's details cannot be changed (the owner adds a new account instead). Holders of
 * BANK_ACCOUNT_VERIFY verify or reject them. Only verified accounts are eligible for payouts. Change requests
 * made before changes were withdrawn may still be pending: verifying one retires the account it replaces and
 * moves primary to it.
 */
@Service
public class BankAccountService {

	static final String ACCOUNT_NUMBER_CONTEXT = "bank_account.account_number";
	static final String ROUTING_CODE_CONTEXT = "bank_account.routing_code";
	static final String FINGERPRINT_CONTEXT = "bank_account.fingerprint";
	private static final int MAX_ACTIVE_ACCOUNTS = 5;
	/** Accounts in use. Rejected ones are hidden from the owner and do not count towards the limit or as duplicates. */
	private static final List<BankAccountStatus> IN_USE = List.of(BankAccountStatus.PENDING_VERIFICATION,
			BankAccountStatus.VERIFIED);

	private final BankAccountRepository accounts;
	private final UserProfileService profiles;
	private final UserAccountService users;
	private final FieldEncryptor encryptor;
	private final AuditService audit;
	private final DocumentService documents;
	private final NotificationService notifications;
	private final Clock clock;

	public BankAccountService(BankAccountRepository accounts, UserProfileService profiles, UserAccountService users,
			FieldEncryptor encryptor,
			AuditService audit, DocumentService documents, NotificationService notifications,
			Clock clock) {
		this.accounts = accounts;
		this.profiles = profiles;
		this.users = users;
		this.encryptor = encryptor;
		this.audit = audit;
		this.documents = documents;
		this.notifications = notifications;
		this.clock = clock;
	}

	/** The owner's accounts in use; rejected ones are left out (the owner was told why, by notification and email). */
	@Transactional(readOnly = true)
	public List<BankAccountResponse> listFor(UUID userId) {
		return accounts.findByUserIdAndStatusInOrderByCreatedAtAsc(userId, IN_USE).stream()
			.map(BankAccountResponse::from)
			.toList();
	}

	@Transactional
	public BankAccountResponse add(UUID userId, AddBankAccountRequest request, BankProofType proofType, MultipartFile proof) {
		users.requireEmailVerified(userId);
		List<BankAccount> active = accounts.lockActiveForUser(userId);
		BankAccount account = register(userId, request, active);
		attachProof(userId, account, proofType, proof);
		account.setPrimary(active.stream().noneMatch(BankAccount::isPrimary));
		accounts.saveAndFlush(account);

		audit.record(AuditRecord.of(userId, AuditAction.BANK_ACCOUNT_ADDED, "BANK_ACCOUNT", account.getId())
			.withNewValue(Map.of("bankName", account.getBankName(), "country", account.getCountry(),
					"currency", account.getCurrency(), "accountNumberLast4", account.getAccountNumberLast4())));
		return BankAccountResponse.from(account);
	}

	/** A bank statement or cancelled cheque is required with every new account; stored encrypted. */
	private void attachProof(UUID userId, BankAccount account, BankProofType proofType, MultipartFile proof) {
		if (proofType == null) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Choose whether you are uploading a bank statement or a cancelled cheque");
		}
		if (proof == null || proof.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Upload a bank statement or a cancelled cheque for this account");
		}
		account.provedBy(proofType, documents.store(userId, proofType.purpose(), proof));
	}

	/** Validates a new account for the owner and builds it (unsaved, not primary). */
	private BankAccount register(UUID userId, AddBankAccountRequest request, List<BankAccount> active) {
		if (active.stream().filter(a -> IN_USE.contains(a.getStatus())).count() >= MAX_ACTIVE_ACCOUNTS) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"You can register at most " + MAX_ACTIVE_ACCOUNTS + " bank accounts");
		}
		String accountNumber = AccountNumbers.normalize(request.accountNumber());
		String routingCode = AccountNumbers.normalize(request.routingCode());
		String fingerprint = encryptor.fingerprint(routingCode + "|" + accountNumber, FINGERPRINT_CONTEXT);
		if (accounts.existsByUserIdAndAccountFingerprintAndStatusIn(userId, fingerprint, IN_USE)) {
			throw new BusinessException(ErrorCode.CONFLICT, "This bank account is already registered");
		}

		BankAccount account = new BankAccount(userId, holderName(userId), request.bankName().strip(),
				request.country(), request.currency(), encryptor.encrypt(accountNumber, ACCOUNT_NUMBER_CONTEXT),
				FieldEncryptor.lastChars(accountNumber, 4), fingerprint,
				encryptor.encrypt(routingCode, ROUTING_CODE_CONTEXT));
		return account;
	}

	/** The holder is always the investor's registered name (which cannot be changed), never what the client sends. */
	private String holderName(UUID userId) {
		UserAccount user = users.getAccount(userId);
		return (user.firstName().strip() + " " + user.lastName().strip()).strip();
	}

	/**
	 * Removes an account. An investor always keeps a verified account for payouts: the last verified one cannot
	 * be removed, nor can the primary one (another verified account is made primary first). Accounts waiting for
	 * verification can always be removed.
	 */
	@Transactional
	public void remove(UUID userId, UUID accountId) {
		List<BankAccount> active = accounts.lockActiveForUser(userId);
		BankAccount account = owned(userId, accountId);
		if (account.getStatus() == BankAccountStatus.VERIFIED) {
			long verified = active.stream().filter(a -> a.getStatus() == BankAccountStatus.VERIFIED).count();
			if (verified <= 1) {
				throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
						"You must keep at least one verified bank account; add another and have it verified first");
			}
			if (account.isPrimary()) {
				throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
						"This is your primary account; make another verified account primary before removing it");
			}
		}
		account.remove(clock.instant());
		audit.record(AuditRecord.of(userId, AuditAction.BANK_ACCOUNT_REMOVED, "BANK_ACCOUNT", accountId));
	}

	@Transactional
	public List<BankAccountResponse> makePrimary(UUID userId, UUID accountId) {
		List<BankAccount> active = accounts.lockActiveForUser(userId);
		BankAccount target = owned(userId, accountId);
		// Payouts go to the primary account, so it must be one finance has verified.
		if (target.getStatus() != BankAccountStatus.VERIFIED) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"Only a verified account can be made primary; wait for our finance team to verify it");
		}
		if (!target.isPrimary()) {
			// Clear the old primary first: the partial unique index allows only one per user.
			active.stream().filter(BankAccount::isPrimary).forEach(a -> a.setPrimary(false));
			accounts.flush();
			target.setPrimary(true);
			audit.record(AuditRecord.of(userId, AuditAction.BANK_ACCOUNT_PRIMARY_CHANGED, "BANK_ACCOUNT", accountId));
		}
		accounts.flush();
		return listFor(userId);
	}

	// ----------------------------------------------------------------------- staff side

	@Transactional(readOnly = true)
	public List<AdminBankAccountResponse> adminListFor(UUID userId) {
		return accounts.findByUserIdOrderByCreatedAtAsc(userId).stream().map(this::adminView).toList();
	}

	@Transactional(readOnly = true)
	public Page<AdminBankAccountResponse> queue(BankAccountStatus status, Pageable pageable) {
		return accounts.findByStatus(status, pageable).map(this::adminView);
	}

	/**
	 * A reviewer takes an account waiting for verification: from now on only they may verify or reject it. Taking one
	 * already assigned to you changes nothing; one assigned to another reviewer must be given back by them first.
	 */
	@Transactional
	public AdminBankAccountResponse assign(UUID reviewerId, UUID accountId) {
		BankAccount account = pending(reviewerId, accountId);
		if (account.isAssignedTo(reviewerId)) {
			return adminView(account);
		}
		if (account.getAssignedTo() != null) {
			throw new BusinessException(ErrorCode.CONFLICT,
					"This verification is already assigned to " + assignee(account).name());
		}
		account.assignTo(reviewerId, clock.instant());
		audit.record(AuditRecord.of(reviewerId, AuditAction.BANK_ACCOUNT_ASSIGNED, "BANK_ACCOUNT", accountId)
			.withNewValue(Map.of("userId", account.getUserId().toString())));
		accounts.flush();
		return adminView(account);
	}

	/** The assigned reviewer gives an account back to the queue, undecided, for another reviewer to take. */
	@Transactional
	public AdminBankAccountResponse unassign(UUID reviewerId, UUID accountId) {
		BankAccount account = pendingForReview(reviewerId, accountId);
		account.unassign();
		audit.record(AuditRecord.of(reviewerId, AuditAction.BANK_ACCOUNT_UNASSIGNED, "BANK_ACCOUNT", accountId)
			.withNewValue(Map.of("userId", account.getUserId().toString())));
		accounts.flush();
		return adminView(account);
	}

	@Transactional
	public AdminBankAccountResponse verify(UUID reviewerId, UUID accountId) {
		// Lock the owner's accounts first, in the same order as the owner's own changes.
		UUID ownerId = accounts.findById(accountId).map(BankAccount::getUserId)
			.orElseThrow(() -> new ResourceNotFoundException("Bank account", accountId));
		List<BankAccount> active = accounts.lockActiveForUser(ownerId);
		BankAccount account = pendingForReview(reviewerId, accountId);
		if (profiles.kycStatusOf(account.getUserId()) != KycStatus.APPROVED) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"The owner's identity must be verified (KYC approved) before their bank account");
		}
		if (account.getProofDocumentId() == null) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "This account has no bank statement or cancelled "
					+ "cheque; reject it so the investor can add it again with one");
		}
		account.verify(reviewerId, clock.instant());
		audit.record(AuditRecord.of(reviewerId, AuditAction.BANK_ACCOUNT_VERIFIED, "BANK_ACCOUNT", accountId)
			.withNewValue(Map.of("userId", account.getUserId().toString())));

		if (account.getReplacesAccountId() != null) {
			completeChange(reviewerId, account, active);
		}
		notifications.notify(account.getUserId(), "BANK_ACCOUNT_VERIFIED", "Your bank account is verified",
				label(account) + " was checked and verified. Your payouts and withdrawals can now be paid into it.",
				"/profile/bank-accounts");
		accounts.flush();
		return adminView(account);
	}

	/** A verified change: retire the replaced account and hand its primary role to the new one. */
	private void completeChange(UUID reviewerId, BankAccount replacement, List<BankAccount> active) {
		Optional<BankAccount> replaced = active.stream()
			.filter(a -> a.getId().equals(replacement.getReplacesAccountId()))
			.findFirst();
		boolean takesPrimary = replaced.map(BankAccount::isPrimary).orElse(false)
				|| active.stream().noneMatch(BankAccount::isPrimary);
		replaced.ifPresent(old -> {
			old.remove(clock.instant());
			audit.record(AuditRecord.of(reviewerId, AuditAction.BANK_ACCOUNT_CHANGED, "BANK_ACCOUNT", replacement.getId())
				.withNewValue(Map.of("userId", replacement.getUserId().toString(),
						"replacedAccountId", old.getId().toString())));
		});
		// Clear the old primary first: the partial unique index allows only one per user.
		accounts.flush();
		if (takesPrimary) {
			replacement.setPrimary(true);
		}
	}

	@Transactional
	public AdminBankAccountResponse reject(UUID reviewerId, UUID accountId, String reason) {
		BankAccount account = pendingForReview(reviewerId, accountId);
		account.reject(reviewerId, reason.strip());
		audit.record(AuditRecord.of(reviewerId, AuditAction.BANK_ACCOUNT_REJECTED, "BANK_ACCOUNT", accountId)
			.withNewValue(Map.of("userId", account.getUserId().toString(), "reason", reason.strip())));
		notifications.notify(account.getUserId(), "BANK_ACCOUNT_REJECTED", "Your bank account could not be verified",
				"We could not verify " + label(account) + ". Reason: " + reason.strip().replaceAll("[.!?]+$", "") + ". Please add it again with the right details.",
				"/profile/bank-accounts");
		accounts.flush();
		return adminView(account);
	}

	/** e.g. "HDFC Bank account ending 4321"; never the full number. */
	private static String label(BankAccount account) {
		return account.getBankName() + " account ending " + account.getAccountNumberLast4();
	}

	/** The account's proof document, for a verifier; every view is audited. */
	@Transactional
	public DocumentContent proof(UUID reviewerId, UUID accountId) {
		BankAccount account = accounts.findById(accountId)
			.filter(a -> a.getProofDocumentId() != null)
			.orElseThrow(() -> new ResourceNotFoundException("Bank account proof", accountId));
		audit.record(AuditRecord.of(reviewerId, AuditAction.BANK_ACCOUNT_PROOF_VIEWED, "BANK_ACCOUNT", accountId)
			.withNewValue(Map.of("proofType", account.getProofType())));
		return documents.load(account.getProofDocumentId());
	}

	/** Whether the user has at least one verified bank account (needed before investing). */
	@Transactional(readOnly = true)
	public boolean hasVerifiedAccount(UUID userId) {
		return accounts.findByUserIdAndStatusNotOrderByCreatedAtAsc(userId, BankAccountStatus.REMOVED).stream()
			.anyMatch(a -> a.getStatus() == BankAccountStatus.VERIFIED);
	}

	/** For the withdrawal module: the verified primary account to pay out to, if any. */
	@Transactional(readOnly = true)
	public Optional<BankAccountResponse> verifiedPrimaryAccount(UUID userId) {
		return accounts.findByUserIdAndStatusNotOrderByCreatedAtAsc(userId, BankAccountStatus.REMOVED).stream()
			.filter(a -> a.isPrimary() && a.getStatus() == BankAccountStatus.VERIFIED)
			.findFirst()
			.map(BankAccountResponse::from);
	}

	/** For the withdrawal module: an account's payout eligibility and display details (nothing secret). */
	@Transactional(readOnly = true)
	public Optional<PayoutAccount> payoutAccount(UUID accountId) {
		return accounts.findById(accountId).map(a -> new PayoutAccount(a.getId(), a.getUserId(),
				a.getAccountHolderName(), a.getBankName(), a.getCountry(), a.getCurrency(), a.getAccountNumberLast4(),
				a.getStatus()));
	}

	/**
	 * For payout files only: full, decrypted account details. Callers must audit every use and never
	 * log, store or return them through the API.
	 */
	@Transactional(readOnly = true)
	public Map<UUID, PayoutInstruction> payoutInstructions(Collection<UUID> accountIds) {
		return accounts.findAllById(accountIds).stream()
			.collect(Collectors.toMap(BankAccount::getId, a -> new PayoutInstruction(a.getId(),
					a.getAccountHolderName(), a.getBankName(), a.getCountry(), a.getCurrency(),
					encryptor.decrypt(a.getAccountNumberEncrypted(), ACCOUNT_NUMBER_CONTEXT),
					encryptor.decrypt(a.getRoutingCodeEncrypted(), ROUTING_CODE_CONTEXT))));
	}

	/** A pending account the reviewer has assigned to themselves: only they may decide it. */
	private BankAccount pendingForReview(UUID reviewerId, UUID accountId) {
		BankAccount account = pending(reviewerId, accountId);
		if (!account.isAssignedTo(reviewerId)) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, account.getAssignedTo() == null
					? "Assign this verification to yourself before deciding it"
					: "This verification is assigned to " + assignee(account).name());
		}
		return account;
	}

	private BankAccount pending(UUID reviewerId, UUID accountId) {
		BankAccount account = accounts.findByIdForUpdate(accountId)
			.orElseThrow(() -> new ResourceNotFoundException("Bank account", accountId));
		if (account.getUserId().equals(reviewerId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "You cannot review your own bank account");
		}
		if (account.getStatus() != BankAccountStatus.PENDING_VERIFICATION) {
			throw new BusinessException(ErrorCode.CONFLICT, "Bank account is " + account.getStatus());
		}
		return account;
	}

	/** Not-found for other users' accounts, so account ids cannot be probed. */
	private BankAccount owned(UUID userId, UUID accountId) {
		return accounts.findById(accountId)
			.filter(a -> a.getUserId().equals(userId) && a.isActive())
			.orElseThrow(() -> new ResourceNotFoundException("Bank account", accountId));
	}

	private AdminBankAccountResponse adminView(BankAccount account) {
		BankAccountResponse replaces = account.getReplacesAccountId() == null ? null
				: accounts.findById(account.getReplacesAccountId()).map(BankAccountResponse::from).orElse(null);
		return new AdminBankAccountResponse(account.getUserId(), BankAccountResponse.from(account),
				account.getVerifiedBy(),
				accounts.countOtherUsersWithFingerprint(account.getAccountFingerprint(), account.getUserId()), replaces,
				assignee(account), new AdminBankAccountResponse.Ifsc(
						encryptor.decrypt(account.getRoutingCodeEncrypted(), ROUTING_CODE_CONTEXT)));
	}

	private Assignee assignee(BankAccount account) {
		if (account.getAssignedTo() == null) {
			return null;
		}
		UserAccount reviewer = users.getAccount(account.getAssignedTo());
		return new Assignee(reviewer.id(), reviewer.firstName() + " " + reviewer.lastName(), account.getAssignedAt());
	}

}
