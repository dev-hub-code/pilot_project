package com.sealease.backend.payment.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.payment.dto.CompanyBankAccountRequest;
import com.sealease.backend.payment.dto.CompanyBankAccountResponse;
import com.sealease.backend.payment.entity.CompanyBankAccount;
import com.sealease.backend.payment.repository.CompanyBankAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** The company bank accounts investors pay into, maintained by finance. */
@Service
public class CompanyBankAccountService {

	private static final String ENTITY = "COMPANY_BANK_ACCOUNT";

	private final CompanyBankAccountRepository accounts;
	private final AuditService audit;

	public CompanyBankAccountService(CompanyBankAccountRepository accounts, AuditService audit) {
		this.accounts = accounts;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public List<CompanyBankAccountResponse> list() {
		return accounts.findAllByOrderByActiveDescCreatedAtAsc().stream().map(CompanyBankAccountResponse::from).toList();
	}

	/** The accounts investors may pay into now. */
	@Transactional(readOnly = true)
	public List<CompanyBankAccountResponse> active() {
		return accounts.findByActiveTrueOrderByCreatedAtAsc().stream().map(CompanyBankAccountResponse::from).toList();
	}

	@Transactional
	public CompanyBankAccountResponse create(UUID actorId, CompanyBankAccountRequest request) {
		CompanyBankAccount.Details details = normalize(request);
		if (accounts.existsByIfscCodeAndAccountNumber(details.ifscCode(), details.accountNumber())) {
			throw duplicate();
		}
		CompanyBankAccount account = accounts.saveAndFlush(new CompanyBankAccount(actorId, details));
		audit.record(AuditRecord.of(actorId, AuditAction.COMPANY_BANK_ACCOUNT_CREATED, ENTITY, account.getId())
			.withNewValue(snapshot(details)));
		return CompanyBankAccountResponse.from(account);
	}

	@Transactional
	public CompanyBankAccountResponse update(UUID actorId, UUID accountId, CompanyBankAccountRequest request) {
		CompanyBankAccount account = load(accountId);
		CompanyBankAccount.Details details = normalize(request);
		if (accounts.existsByIfscCodeAndAccountNumberAndIdNot(details.ifscCode(), details.accountNumber(), accountId)) {
			throw duplicate();
		}
		CompanyBankAccount.Details before = account.details();
		account.update(details);
		accounts.flush();
		audit.record(AuditRecord.of(actorId, AuditAction.COMPANY_BANK_ACCOUNT_UPDATED, ENTITY, accountId)
			.withOldValue(snapshot(before))
			.withNewValue(snapshot(details)));
		return CompanyBankAccountResponse.from(account);
	}

	/** Inactive accounts are no longer offered to investors; payments already made into them are unaffected. */
	@Transactional
	public CompanyBankAccountResponse setActive(UUID actorId, UUID accountId, boolean active) {
		CompanyBankAccount account = load(accountId);
		if (account.isActive() != active) {
			account.setActive(active);
			accounts.flush();
			audit.record(AuditRecord.of(actorId, active ? AuditAction.COMPANY_BANK_ACCOUNT_ACTIVATED
					: AuditAction.COMPANY_BANK_ACCOUNT_DEACTIVATED, ENTITY, accountId));
		}
		return CompanyBankAccountResponse.from(account);
	}

	/** An account the investor may still pay into. */
	@Transactional(readOnly = true)
	public CompanyBankAccount requireActive(UUID accountId) {
		CompanyBankAccount account = accounts.findById(accountId)
			.orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Choose one of our bank accounts"));
		if (!account.isActive()) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"That bank account no longer accepts payments; choose another");
		}
		return account;
	}

	@Transactional(readOnly = true)
	public boolean anyActive() {
		return accounts.existsByActiveTrue();
	}

	@Transactional(readOnly = true)
	public Map<UUID, CompanyBankAccount> byId(Iterable<UUID> ids) {
		Map<UUID, CompanyBankAccount> found = new HashMap<>();
		accounts.findAllById(ids).forEach(a -> found.put(a.getId(), a));
		return found;
	}

	private CompanyBankAccount load(UUID accountId) {
		return accounts.findById(accountId).orElseThrow(() -> new ResourceNotFoundException("Company bank account", accountId));
	}

	private static CompanyBankAccount.Details normalize(CompanyBankAccountRequest r) {
		String accountNumber = r.accountNumber().replace(" ", "");
		if (accountNumber.length() < 6 || accountNumber.length() > 18) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Account number must be 6-18 digits");
		}
		return new CompanyBankAccount.Details(r.accountName().strip(), r.bankName().strip(), blankToNull(r.branch()),
				accountNumber, r.ifscCode().strip().toUpperCase(Locale.ROOT), blankToNull(r.upiId()));
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

	private static BusinessException duplicate() {
		return new BusinessException(ErrorCode.CONFLICT, "This bank account has already been added");
	}

	private static Map<String, Object> snapshot(CompanyBankAccount.Details d) {
		Map<String, Object> values = new HashMap<>();
		values.put("accountName", d.accountName());
		values.put("bankName", d.bankName());
		values.put("branch", d.branch());
		values.put("accountNumber", d.accountNumber());
		values.put("ifscCode", d.ifscCode());
		values.put("upiId", d.upiId());
		return values;
	}

}
