package com.sealease.backend.withdrawal.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.bankaccount.dto.PayoutInstruction;
import com.sealease.backend.bankaccount.service.BankAccountService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.ledger.service.EarningsBalance;
import com.sealease.backend.user.dto.UserAccount;
import com.sealease.backend.user.service.UserAccountService;
import com.sealease.backend.user.service.UserProfileService;
import com.sealease.backend.withdrawal.dto.FinanceTaskResponse;
import com.sealease.backend.withdrawal.dto.FinanceTaskUpdateRequest;
import com.sealease.backend.withdrawal.entity.Withdrawal;
import com.sealease.backend.withdrawal.entity.WithdrawalBatch;
import com.sealease.backend.withdrawal.entity.WithdrawalKind;
import com.sealease.backend.withdrawal.entity.WithdrawalPayoutMethod;
import com.sealease.backend.withdrawal.entity.WithdrawalStatus;
import com.sealease.backend.withdrawal.repository.WithdrawalBatchRepository;
import com.sealease.backend.withdrawal.repository.WithdrawalRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Finance tasks: every withdrawal a withdrawal run makes is a task for finance - pay it into the investor's bank
 * account (or hand it over in cash at the office), then mark it paid, or failed with the reason. Open tasks show the
 * full account number to pay; listing them is audited, like downloading a bank sheet.
 */
@Service
public class FinanceTaskService {

	/** Open: still to pay. Done: paid, failed (held, with a ticket) or returned to the wallet. */
	public enum View {
		OPEN(List.of(WithdrawalStatus.BATCHED, WithdrawalStatus.PROCESSING)),
		DONE(List.of(WithdrawalStatus.PAID, WithdrawalStatus.FAILED, WithdrawalStatus.RETURNED));

		private final List<WithdrawalStatus> statuses;

		View(List<WithdrawalStatus> statuses) {
			this.statuses = statuses;
		}
	}

	private static final String ENTITY = "FINANCE_TASK";
	/** Investors a name or email search may match. */
	private static final int SEARCH_LIMIT = 500;

	private final WithdrawalRepository withdrawals;
	private final WithdrawalBatchRepository batches;
	private final PayoutBatchService payouts;
	private final BankAccountService bankAccounts;
	private final UserAccountService accounts;
	private final UserProfileService profiles;
	private final AuditService audit;

	public FinanceTaskService(WithdrawalRepository withdrawals, WithdrawalBatchRepository batches,
			PayoutBatchService payouts, BankAccountService bankAccounts, UserAccountService accounts,
			UserProfileService profiles, AuditService audit) {
		this.withdrawals = withdrawals;
		this.batches = batches;
		this.payouts = payouts;
		this.bankAccounts = bankAccounts;
		this.accounts = accounts;
		this.profiles = profiles;
		this.audit = audit;
	}

	/** Open tasks oldest first (in the order to pay them); done tasks latest first. Every filter but the view is optional. */
	@Transactional
	public Page<FinanceTaskResponse> search(UUID actorId, View view, WithdrawalPayoutMethod method, WithdrawalKind kind,
			UUID batchId, String query, Pageable pageable) {
		Sort sort = view == View.OPEN ? Sort.by("createdAt", "reference") : Sort.by(Sort.Direction.DESC, "closedAt", "createdAt");
		Page<Withdrawal> page = withdrawals.findAll(matching(view, method, kind, batchId).and(searching(query)),
				PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort));
		List<FinanceTaskResponse> tasks = responses(page.getContent());
		if (view == View.OPEN && tasks.stream().anyMatch(t -> t.payoutMethod() == WithdrawalPayoutMethod.BANK_TRANSFER)) {
			audit.record(AuditRecord.of(actorId, AuditAction.FINANCE_TASKS_VIEWED, ENTITY, null)
				.withNewValue(Map.of("tasks", tasks.size(), "page", pageable.getPageNumber())));
		}
		return new PageImpl<>(tasks, page.getPageable(), page.getTotalElements());
	}

	/** Marks the task paid (with the bank reference or cash receipt) or failed (with the reason). */
	@Transactional
	public FinanceTaskResponse update(UUID actorId, UUID withdrawalId, FinanceTaskUpdateRequest request) {
		Withdrawal withdrawal = withdrawals.findById(withdrawalId)
			.filter(w -> w.getBatchId() != null)
			.orElseThrow(() -> new ResourceNotFoundException("Finance task", withdrawalId));
		if (request.outcome() == FinanceTaskUpdateRequest.Outcome.PAID) {
			payouts.markPaid(actorId, withdrawal.getBatchId(), withdrawalId, request.reference());
		}
		else {
			if (request.reason() == null || request.reason().isBlank()) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Say why it could not be paid");
			}
			payouts.markFailed(actorId, withdrawal.getBatchId(), withdrawalId, request.reason());
		}
		return responses(List.of(withdrawals.findById(withdrawalId).orElseThrow())).getFirst();
	}

	private List<FinanceTaskResponse> responses(List<Withdrawal> items) {
		Map<UUID, String> batchReferences = batches.findAllById(items.stream().map(Withdrawal::getBatchId)
				.filter(Objects::nonNull).distinct().toList()).stream()
			.collect(Collectors.toMap(WithdrawalBatch::getId, WithdrawalBatch::getReference));
		Map<UUID, PayoutInstruction> payees = bankAccounts.payoutInstructions(items.stream()
			.filter(w -> !w.isOfficeCash() && w.getBankAccountId() != null).map(Withdrawal::getBankAccountId).distinct()
			.toList());
		Map<UUID, UserAccount> people = accounts.getAccounts(items.stream().map(Withdrawal::getUserId).distinct().toList());
		Map<UUID, String> phones = new HashMap<>();
		Function<UUID, String> phone = userId -> phones.computeIfAbsent(userId, id -> profiles.getProfile(id).phone());
		List<FinanceTaskResponse> tasks = new ArrayList<>();
		for (Withdrawal w : items) {
			UserAccount person = people.get(w.getUserId());
			PayoutInstruction payee = w.getBankAccountId() == null ? null : payees.get(w.getBankAccountId());
			boolean open = w.getStatus() == WithdrawalStatus.BATCHED || w.getStatus() == WithdrawalStatus.PROCESSING;
			String accountNumber = w.isOfficeCash() ? null
					: open && payee != null ? payee.accountNumber() : "•••• " + w.getBankAccountLast4();
			tasks.add(new FinanceTaskResponse(w.getId(), w.getReference(),
					w.getBalance() == EarningsBalance.REFERRAL ? WithdrawalKind.REFERRAL : WithdrawalKind.RENT,
					w.getPayoutMethod(), w.getBatchId(), batchReferences.get(w.getBatchId()), w.getUserId(),
					person == null ? null : person.userCode(),
					person == null ? null : (person.firstName() + " " + person.lastName()).strip(),
					w.isOfficeCash() ? phone.apply(w.getUserId()) : null, w.getBankName(), w.getBankHolderName(),
					accountNumber, payee == null ? null : payee.routingCode(), MoneyResponse.from(w.amount()), w.getStatus(),
					w.getPayoutReference(), w.getFailureReason(), w.getTicketId(), w.getCreatedAt(), w.getClosedAt()));
		}
		return tasks;
	}

	/**
	 * A search: the text in the withdrawal's reference ("WD-100001"), a batch's reference ("WB-00002"), or the
	 * investor (user code "SL03501", name or email). Nothing to search: everything.
	 */
	private Specification<Withdrawal> searching(String query) {
		if (query == null || query.isBlank()) {
			return (root, q, cb) -> null;
		}
		String text = query.strip();
		List<UUID> users = accounts.searchIds(text, SEARCH_LIMIT);
		Optional<UUID> batch = batches.findByReferenceIgnoreCase(text).map(WithdrawalBatch::getId);
		String like = "%" + text.toUpperCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
		return (root, q, cb) -> {
			List<Predicate> any = new ArrayList<>();
			any.add(cb.like(cb.upper(root.get("reference")), like, '\\'));
			if (!users.isEmpty()) {
				any.add(root.get("userId").in(users));
			}
			batch.ifPresent(id -> any.add(cb.equal(root.get("batchId"), id)));
			return cb.or(any.toArray(Predicate[]::new));
		};
	}

	private static Specification<Withdrawal> matching(View view, WithdrawalPayoutMethod method, WithdrawalKind kind,
			UUID batchId) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			predicates.add(cb.isNotNull(root.get("batchId")));
			predicates.add(root.get("status").in(view.statuses));
			if (method != null) {
				predicates.add(cb.equal(root.get("payoutMethod"), method));
			}
			if (kind != null) {
				predicates.add(root.get("balance").in(kind.balances()));
			}
			if (batchId != null) {
				predicates.add(cb.equal(root.get("batchId"), batchId));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

}
