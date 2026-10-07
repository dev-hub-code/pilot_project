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
import com.sealease.backend.bankaccount.repository.BankAccountRepository;
import com.sealease.backend.common.crypto.FieldEncryptor;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.user.entity.KycStatus;
import com.sealease.backend.user.service.UserProfileService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Investor payout accounts. Owners add, remove and choose a primary account; holders of
 * BANK_ACCOUNT_VERIFY verify or reject them. Only verified accounts will be eligible for payouts.
 */
@Service
public class BankAccountService {

	static final String ACCOUNT_NUMBER_CONTEXT = "bank_account.account_number";
	static final String ROUTING_CODE_CONTEXT = "bank_account.routing_code";
	static final String FINGERPRINT_CONTEXT = "bank_account.fingerprint";
	private static final int MAX_ACTIVE_ACCOUNTS = 5;

	private final BankAccountRepository accounts;
	private final UserProfileService profiles;
	private final FieldEncryptor encryptor;
	private final AuditService audit;
	private final Clock clock;

	public BankAccountService(BankAccountRepository accounts, UserProfileService profiles, FieldEncryptor encryptor,
			AuditService audit, Clock clock) {
		this.accounts = accounts;
		this.profiles = profiles;
		this.encryptor = encryptor;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<BankAccountResponse> listFor(UUID userId) {
		return accounts.findByUserIdAndStatusNotOrderByCreatedAtAsc(userId, BankAccountStatus.REMOVED).stream()
			.map(BankAccountResponse::from)
			.toList();
	}

	@Transactional
	public BankAccountResponse add(UUID userId, AddBankAccountRequest request) {
		List<BankAccount> active = accounts.lockActiveForUser(userId);
		if (active.size() >= MAX_ACTIVE_ACCOUNTS) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"You can register at most " + MAX_ACTIVE_ACCOUNTS + " bank accounts");
		}
		String accountNumber = AccountNumbers.normalize(request.accountNumber());
		String routingCode = AccountNumbers.normalize(request.routingCode());
		if (AccountNumbers.looksLikeIban(accountNumber) && !AccountNumbers.isValidIban(accountNumber)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "The IBAN checksum is invalid; please check the number");
		}
		String fingerprint = encryptor.fingerprint(routingCode + "|" + accountNumber, FINGERPRINT_CONTEXT);
		if (accounts.existsByUserIdAndAccountFingerprintAndStatusNot(userId, fingerprint, BankAccountStatus.REMOVED)) {
			throw new BusinessException(ErrorCode.CONFLICT, "This bank account is already registered");
		}

		BankAccount account = new BankAccount(userId, request.accountHolderName().strip(), request.bankName().strip(),
				request.country(), request.currency(), encryptor.encrypt(accountNumber, ACCOUNT_NUMBER_CONTEXT),
				FieldEncryptor.lastChars(accountNumber, 4), fingerprint,
				encryptor.encrypt(routingCode, ROUTING_CODE_CONTEXT));
		account.setPrimary(active.stream().noneMatch(BankAccount::isPrimary));
		accounts.saveAndFlush(account);

		audit.record(AuditRecord.of(userId, AuditAction.BANK_ACCOUNT_ADDED, "BANK_ACCOUNT", account.getId())
			.withNewValue(Map.of("bankName", account.getBankName(), "country", account.getCountry(),
					"currency", account.getCurrency(), "accountNumberLast4", account.getAccountNumberLast4())));
		return BankAccountResponse.from(account);
	}

	@Transactional
	public void remove(UUID userId, UUID accountId) {
		accounts.lockActiveForUser(userId);
		BankAccount account = owned(userId, accountId);
		account.remove(clock.instant());
		audit.record(AuditRecord.of(userId, AuditAction.BANK_ACCOUNT_REMOVED, "BANK_ACCOUNT", accountId));
	}

	@Transactional
	public List<BankAccountResponse> makePrimary(UUID userId, UUID accountId) {
		List<BankAccount> active = accounts.lockActiveForUser(userId);
		BankAccount target = owned(userId, accountId);
		if (target.getStatus() == BankAccountStatus.REJECTED) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "A rejected account cannot be primary");
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

	@Transactional
	public AdminBankAccountResponse verify(UUID reviewerId, UUID accountId) {
		BankAccount account = pendingForReview(reviewerId, accountId);
		if (profiles.kycStatusOf(account.getUserId()) != KycStatus.APPROVED) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"The owner's identity must be verified (KYC approved) before their bank account");
		}
		account.verify(reviewerId, clock.instant());
		audit.record(AuditRecord.of(reviewerId, AuditAction.BANK_ACCOUNT_VERIFIED, "BANK_ACCOUNT", accountId)
			.withNewValue(Map.of("userId", account.getUserId().toString())));
		accounts.flush();
		return adminView(account);
	}

	@Transactional
	public AdminBankAccountResponse reject(UUID reviewerId, UUID accountId, String reason) {
		BankAccount account = pendingForReview(reviewerId, accountId);
		account.reject(reviewerId, reason.strip());
		audit.record(AuditRecord.of(reviewerId, AuditAction.BANK_ACCOUNT_REJECTED, "BANK_ACCOUNT", accountId)
			.withNewValue(Map.of("userId", account.getUserId().toString(), "reason", reason.strip())));
		accounts.flush();
		return adminView(account);
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

	private BankAccount pendingForReview(UUID reviewerId, UUID accountId) {
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
		return new AdminBankAccountResponse(account.getUserId(), BankAccountResponse.from(account),
				account.getVerifiedBy(),
				accounts.countOtherUsersWithFingerprint(account.getAccountFingerprint(), account.getUserId()));
	}

}
