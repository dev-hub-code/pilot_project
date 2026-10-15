package com.sealease.backend.withdrawal.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.bankaccount.dto.PayoutAccount;
import com.sealease.backend.bankaccount.dto.PayoutInstruction;
import com.sealease.backend.bankaccount.service.BankAccountService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.common.money.Money;
import com.sealease.backend.helpdesk.entity.RelatedType;
import com.sealease.backend.helpdesk.entity.TicketCategory;
import com.sealease.backend.helpdesk.entity.TicketPriority;
import com.sealease.backend.helpdesk.service.SupportService;
import com.sealease.backend.kafka.KafkaTopics;
import com.sealease.backend.ledger.service.AccountType;
import com.sealease.backend.ledger.service.LedgerService;
import com.sealease.backend.ledger.service.Posting;
import com.sealease.backend.ledger.service.TransactionType;
import com.sealease.backend.outbox.DomainEvent;
import com.sealease.backend.outbox.OutboxPublisher;
import com.sealease.backend.user.dto.UserAccount;
import com.sealease.backend.user.service.UserAccountService;
import com.sealease.backend.user.service.UserProfileService;
import com.sealease.backend.withdrawal.dto.BatchDetail;
import com.sealease.backend.withdrawal.dto.BatchFileResponse;
import com.sealease.backend.withdrawal.dto.BatchResponse;
import com.sealease.backend.withdrawal.dto.WithdrawalResponse;
import com.sealease.backend.withdrawal.entity.BatchStatus;
import com.sealease.backend.withdrawal.entity.Withdrawal;
import com.sealease.backend.withdrawal.entity.WithdrawalBatch;
import com.sealease.backend.withdrawal.entity.WithdrawalKind;
import com.sealease.backend.withdrawal.entity.WithdrawalPayoutMethod;
import com.sealease.backend.withdrawal.entity.WithdrawalStatus;
import com.sealease.backend.withdrawal.repository.WithdrawalBatchRepository;
import com.sealease.backend.withdrawal.repository.WithdrawalRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Paying withdrawal runs: each run's bank withdrawals form one batch and its cash withdrawals another, each with an
 * Excel sheet made and kept (encrypted) with it - one row per investor with their rent, referral and total, and their
 * bank account (bank sheet) or phone (cash sheet). Finance pays a sheet by hand and uploads it back with a Status per
 * row, which completes or fails that investor's withdrawals in the batch; items can also be settled one by one.
 *
 * <p>A failed payment is held: the withdrawal stays FAILED with its money in transit, and a support ticket is opened
 * for the investor and assigned to the finance user who recorded the failure. Finance resolves it by marking it
 * paid (paid by hand) or returned (the money goes back to the investor's balance), which resolves the ticket.
 *
 * <p>Lock order is <em>batch, then its withdrawals in id order</em>. A batch closes once no item is in flight.
 */
@Service
public class PayoutBatchService {

	private static final String ENTITY = "WITHDRAWAL_BATCH";
	/** Rows to report when an uploaded file is refused. */
	private static final String DEFAULT_FAILURE = "The bank did not pay it";
	private static final String CASH_NOT_COLLECTED = "The cash was not collected at the office";

	private final WithdrawalBatchRepository batches;
	private final WithdrawalRepository withdrawals;
	private final WithdrawalService withdrawalService;
	private final BankAccountService bankAccounts;
	private final LedgerService ledger;
	private final SupportService support;
	private final OutboxPublisher outbox;
	private final AuditService audit;
	private final BatchFileStore files;
	private final UserAccountService accounts;
	private final UserProfileService profiles;
	private final Clock clock;

	public PayoutBatchService(WithdrawalBatchRepository batches, WithdrawalRepository withdrawals,
			WithdrawalService withdrawalService, BankAccountService bankAccounts, LedgerService ledger,
			SupportService support, OutboxPublisher outbox, AuditService audit, BatchFileStore files,
			UserAccountService accounts, UserProfileService profiles, Clock clock) {
		this.accounts = accounts;
		this.profiles = profiles;
		this.batches = batches;
		this.withdrawals = withdrawals;
		this.withdrawalService = withdrawalService;
		this.bankAccounts = bankAccounts;
		this.ledger = ledger;
		this.support = support;
		this.outbox = outbox;
		this.audit = audit;
		this.files = files;
		this.clock = clock;
	}

	/**
	 * Makes the bank or cash batch of a rent or referral withdrawal run. A bank batch gets its Excel sheet
	 * ("WR-00002-Rent-Bank.xlsx") for finance to pay from; a cash batch, paid by hand at the office, has none. Either way
	 * each withdrawal is a finance task from the start, marked paid (collected) or failed on its own.
	 */
	WithdrawalBatch createForRun(WithdrawalKind kind, WithdrawalPayoutMethod method, List<Withdrawal> items, String run,
			UUID actorId) {
		Money total = items.stream().map(Withdrawal::amount).reduce(Money::plus).orElseThrow();
		WithdrawalBatch batch = batches.saveAndFlush(WithdrawalBatch.forRun(reference(), total, items.size(), kind, method,
				actorId));
		items.forEach(w -> w.addToBatch(batch.getId()));
		withdrawals.flush();
		boolean cash = method == WithdrawalPayoutMethod.OFFICE_CASH;
		if (!cash) {
			files.save(batch.getId(), BatchFileStore.PAYMENT, run + "-" + kind.label() + "-Bank.xlsx", paymentFile(items),
					null, null, null);
		}
		audit.record(AuditRecord.of(actorId, AuditAction.WITHDRAWAL_BATCH_CREATED, ENTITY, batch.getId())
			.withNewValue(new LinkedHashMap<>(Map.of("reference", batch.getReference(), "run", run, "kind", kind,
					"payoutMethod", method, "items", items.size(), "total", batch.total().toString()))));
		publishBatch(KafkaTopics.WITHDRAWAL_BATCHED, "WithdrawalBatchCreated", batch);
		// Each withdrawal is now a finance task, to be marked paid (or failed) on its own.
		send(actorId, batch);
		return batch;
	}

	private String reference() {
		return "WB-" + String.format("%05d", batches.nextNumber());
	}

	/**
	 * The batch's Excel file with full account details: for a withdrawal run, its sheet in the current columns (see
	 * {@link #current}); for an older monthly batch, the file kept when it was made. Every download is audited. Refused before payment while
	 * any payee account is no longer verified.
	 */
	@Transactional
	public PayoutFile file(UUID actorId, UUID batchId) {
		WithdrawalBatch batch = load(batchId);
		List<Withdrawal> items = withdrawals.findByBatchIdOrderByCreatedAtAscIdAsc(batchId);
		if (batch.getStatus() == BatchStatus.CREATED && !batch.isOfficeCash()) {
			List<String> unverified = items.stream()
				.filter(w -> !bankAccounts.payoutAccount(w.getBankAccountId()).map(PayoutAccount::isVerified).orElse(false))
				.map(Withdrawal::getReference)
				.toList();
			if (!unverified.isEmpty()) {
				throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Bank accounts are no longer verified for "
						+ String.join(", ", unverified) + ": pay the others, and mark these failed when uploading the file");
			}
		}
		if (batch.isOfficeCash() && batch.getPeriod() == null) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"Cash at the office has no sheet: mark each withdrawal collected or not on the batch's page");
		}
		BatchFileStore.StoredFile file = files.payment(batchId)
			.orElseThrow(() -> new ResourceNotFoundException("Batch file", batchId));
		audit.record(AuditRecord.of(actorId, AuditAction.WITHDRAWAL_BATCH_EXPORTED, ENTITY, batchId)
			.withNewValue(Map.of("reference", batch.getReference(), "items", items.size(), "file", file.filename())));
		return new PayoutFile(file.filename(), current(batch, file, items));
	}

	/** One of the files kept with a batch (the payment file or an uploaded one). Audited. */
	@Transactional
	public PayoutFile storedFile(UUID actorId, UUID batchId, UUID fileId) {
		WithdrawalBatch batch = load(batchId);
		BatchFileStore.StoredFile file = files.content(batchId, fileId);
		audit.record(AuditRecord.of(actorId, AuditAction.WITHDRAWAL_BATCH_EXPORTED, ENTITY, batchId)
			.withNewValue(Map.of("reference", batch.getReference(), "file", file.filename())));
		return new PayoutFile(file.filename(), file.isPayment()
				? current(batch, file, withdrawals.findByBatchIdOrderByCreatedAtAscIdAsc(batchId)) : file.content());
	}

	/**
	 * A withdrawal run's sheet in the current columns (readable User ID, Withdrawal ID): built from its withdrawals,
	 * so a run made before a column was added gets it too. Batches of the monthly runs before withdrawal runs keep the
	 * file they were made with.
	 */
	private byte[] current(WithdrawalBatch batch, BatchFileStore.StoredFile file, List<Withdrawal> items) {
		// Monthly batches have a period; withdrawal runs' do not.
		if (batch.getPeriod() != null || !file.filename().toLowerCase(Locale.ROOT).endsWith(".xlsx") || items.isEmpty()) {
			return file.content();
		}
		return batch.isOfficeCash() ? file.content() : paymentFile(items);
	}

	@Transactional(readOnly = true)
	public List<BatchFileResponse> files(UUID batchId) {
		load(batchId);
		return files.list(batchId);
	}


	/** The bank sheet: one row per investor with their account and what the batch pays them, and an empty Status. */
	private byte[] paymentFile(List<Withdrawal> items) {
		Map<UUID, List<Withdrawal>> byInvestor = new LinkedHashMap<>();
		items.stream().sorted(Comparator.comparing(Withdrawal::getReference))
			.forEach(w -> byInvestor.computeIfAbsent(w.getUserId(), k -> new ArrayList<>()).add(w));
		Map<UUID, PayoutInstruction> payees = bankAccounts.payoutInstructions(
				items.stream().map(Withdrawal::getBankAccountId).distinct().toList());
		Map<UUID, UserAccount> people = accounts.getAccounts(byInvestor.keySet());
		List<WithdrawalSheet.Line> lines = new ArrayList<>();
		byInvestor.forEach((userId, own) -> {
			PayoutInstruction payee = payees.get(own.getFirst().getBankAccountId());
			lines.add(new WithdrawalSheet.Line(userCode(people, userId), payee.holderName(), payee.accountNumber(),
					payee.routingCode(), sum(own, w -> true)));
		});
		return WithdrawalSheet.write(lines);
	}

	/** The user's readable ID ("SL03500") for the sheet. */
	private static String userCode(Map<UUID, UserAccount> people, UUID userId) {
		UserAccount person = people.get(userId);
		return person == null || person.userCode() == null ? userId.toString() : person.userCode();
	}

	private static BigDecimal sum(List<Withdrawal> items, Predicate<Withdrawal> which) {
		return items.stream().filter(which).map(w -> w.amount().toMinorUnitScale()).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private static String defaultFailure(WithdrawalBatch batch) {
		return batch.isOfficeCash() ? CASH_NOT_COLLECTED : DEFAULT_FAILURE;
	}

	private void send(UUID actorId, WithdrawalBatch batch) {
		withdrawals.lockBatchItems(batch.getId()).stream()
			.filter(w -> w.getStatus() == WithdrawalStatus.BATCHED)
			.forEach(Withdrawal::markProcessing);
		batch.markSent(actorId, clock.instant());
		withdrawals.flush();
		audit.record(AuditRecord.of(actorId, AuditAction.WITHDRAWAL_BATCH_SENT, ENTITY, batch.getId())
			.withNewValue(Map.of("reference", batch.getReference())));
		publishBatch(KafkaTopics.WITHDRAWAL_PROCESSING, "WithdrawalBatchSent", batch);
	}

	/** One item: the bank paid it. */
	@Transactional
	public BatchDetail markPaid(UUID actorId, UUID batchId, UUID withdrawalId, String payoutReference) {
		WithdrawalBatch batch = lockSent(batchId);
		pay(actorId, batch, item(batchId, withdrawalId), blankToNull(payoutReference));
		closeIfDone(batch);
		return detail(batchId);
	}

	/** One item: the bank did not pay it. It is held, with a ticket, until finance resolves it. */
	@Transactional
	public BatchDetail markFailed(UUID actorId, UUID batchId, UUID withdrawalId, String reason) {
		WithdrawalBatch batch = lockSent(batchId);
		fail(actorId, batch, item(batchId, withdrawalId), reason.strip());
		closeIfDone(batch);
		return detail(batchId);
	}

	/**
	 * Settles a failed payment once its ticket is sorted out: {@code paid} when finance has paid it by hand (with the
	 * bank reference), otherwise the money is returned to the investor's balance for a later month. The ticket is
	 * resolved with a note to the investor.
	 */
	@Transactional
	public WithdrawalResponse resolveFailed(UUID actorId, UUID withdrawalId, boolean paid, String note) {
		Withdrawal withdrawal = withdrawals.findByIdForUpdate(withdrawalId)
			.orElseThrow(() -> new ResourceNotFoundException("Withdrawal", withdrawalId));
		if (withdrawal.getUserId().equals(actorId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "You cannot decide on your own withdrawal");
		}
		if (withdrawal.getStatus() != WithdrawalStatus.FAILED) {
			throw new BusinessException(ErrorCode.CONFLICT, "Only failed withdrawals are resolved; this one is "
					+ withdrawal.getStatus());
		}
		WithdrawalBatch batch = load(withdrawal.getBatchId());
		String text = blankToNull(note);
		String message;
		if (paid) {
			pay(actorId, batch, withdrawal, text);
			message = "Your withdrawal " + withdrawal.getReference() + " of " + withdrawal.amount().display()
					+ " has now been paid into your bank account" + (text == null ? "." : " (reference " + text + ").");
		}
		else {
			withdrawal.markReturned(clock.instant());
			withdrawals.flush();
			withdrawalService.release(withdrawal, actorId, "returned after a failed payment");
			audit.record(AuditRecord.of(actorId, AuditAction.WITHDRAWAL_RETURNED, WithdrawalService.ENTITY, withdrawalId)
				.withNewValue(Map.of("amount", withdrawal.amount().toString(), "note", text == null ? "" : text)));
			withdrawalService.publish(KafkaTopics.WITHDRAWAL_FAILED, "WithdrawalReturned", withdrawal, Map.of());
			message = withdrawal.amount().display() + " from withdrawal " + withdrawal.getReference()
					+ " is back in your balance and will be paid with your next monthly withdrawal."
					+ (text == null ? "" : " " + text);
		}
		if (withdrawal.getTicketId() != null) {
			support.resolveWithNote(actorId, withdrawal.getTicketId(), message);
		}
		return WithdrawalResponse.forStaff(withdrawal);
	}

	// ------------------------------------------------------------------------------ queries

	@Transactional(readOnly = true)
	public Page<BatchResponse> search(BatchStatus status, Pageable pageable) {
		Page<WithdrawalBatch> page = status == null ? batches.findAll(pageable) : batches.findByStatus(status, pageable);
		return page.map(b -> summary(b, withdrawals.findByBatchIdOrderByCreatedAtAscIdAsc(b.getId())));
	}

	@Transactional(readOnly = true)
	public BatchDetail detail(UUID batchId) {
		WithdrawalBatch batch = load(batchId);
		List<Withdrawal> items = withdrawals.findByBatchIdOrderByCreatedAtAscIdAsc(batchId);
		List<UUID> userIds = items.stream().map(Withdrawal::getUserId).distinct().toList();
		Map<UUID, UserAccount> people = accounts.getAccounts(userIds);
		List<BatchDetail.Investor> investors = userIds.stream().map(userId -> {
			UserAccount person = people.get(userId);
			return new BatchDetail.Investor(userId, person == null ? null : person.userCode(),
					person == null ? null : (person.firstName() + " " + person.lastName()).strip(),
					batch.isOfficeCash() ? profiles.getProfile(userId).phone() : null);
		}).toList();
		return new BatchDetail(summary(batch, items), items.stream().map(WithdrawalResponse::forStaff).toList(), investors);
	}

	// ----------------------------------------------------------------------------- internal

	private void pay(UUID actorId, WithdrawalBatch batch, Withdrawal withdrawal, String payoutReference) {
		Money amount = withdrawal.amount();
		withdrawal.markPaid(payoutReference, clock.instant());
		withdrawals.flush();
		ledger.post(TransactionType.WITHDRAWAL_PAYOUT, withdrawal.getId().toString(),
				"Withdrawal " + withdrawal.getReference() + " paid (" + batch.getReference() + ")", actorId,
				List.of(Posting.debit(AccountType.WITHDRAWALS_IN_TRANSIT, null, amount),
						Posting.credit(AccountType.RENTAL_CASH, null, amount)));
		audit.record(AuditRecord.of(actorId, AuditAction.WITHDRAWAL_PAID, WithdrawalService.ENTITY, withdrawal.getId())
			.withNewValue(Map.of("batch", batch.getReference(), "amount", amount.toString(),
					"payoutReference", payoutReference == null ? "" : payoutReference)));
		withdrawalService.publish(KafkaTopics.WITHDRAWAL_COMPLETED, "WithdrawalPaid", withdrawal, Map.of());
	}

	/** Holds a payment the bank did not make and opens a ticket for it, assigned to the finance user recording it. */
	private void fail(UUID actorId, WithdrawalBatch batch, Withdrawal withdrawal, String reason) {
		withdrawal.markFailed(reason);
		withdrawals.flush();
		UUID ticketId = support.openOnBehalf(actorId, withdrawal.getUserId(), TicketCategory.WITHDRAWAL,
				RelatedType.WITHDRAWAL, withdrawal.getId(), withdrawal.getReference(),
				"Withdrawal " + withdrawal.getReference() + " could not be paid",
				"Your withdrawal " + withdrawal.getReference() + " of " + withdrawal.amount().display()
						+ (withdrawal.isOfficeCash() ? " in cash at our office"
								: " to " + withdrawal.getBankName() + " •••• " + withdrawal.getBankAccountLast4())
						+ " could not be paid: " + reason + ". The amount is held safely while our finance team sorts it out;"
						+ " we will update you here.",
				TicketPriority.HIGH, actorId);
		withdrawal.linkTicket(ticketId);
		withdrawals.flush();
		audit.record(AuditRecord.of(actorId, AuditAction.WITHDRAWAL_FAILED, WithdrawalService.ENTITY, withdrawal.getId())
			.withNewValue(Map.of("batch", batch.getReference(), "reason", reason, "ticketId", ticketId.toString())));
		withdrawalService.publish(KafkaTopics.WITHDRAWAL_FAILED, "WithdrawalFailed", withdrawal, Map.of("reason", reason));
	}

	private void closeIfDone(WithdrawalBatch batch) {
		boolean inFlight = withdrawals.findByBatchIdOrderByCreatedAtAscIdAsc(batch.getId()).stream()
			.anyMatch(w -> w.getStatus() == WithdrawalStatus.PROCESSING);
		if (!inFlight && batch.getStatus() == BatchStatus.SENT) {
			batch.close(clock.instant());
			batches.flush();
			audit.record(AuditRecord.of(null, AuditAction.WITHDRAWAL_BATCH_CLOSED, ENTITY, batch.getId())
				.withNewValue(Map.of("reference", batch.getReference())));
		}
	}

	private Withdrawal item(UUID batchId, UUID withdrawalId) {
		Withdrawal withdrawal = withdrawals.findByIdForUpdate(withdrawalId)
			.filter(w -> batchId.equals(w.getBatchId()))
			.orElseThrow(() -> new ResourceNotFoundException("Withdrawal in batch", withdrawalId));
		if (withdrawal.getStatus() != WithdrawalStatus.PROCESSING) {
			throw new BusinessException(ErrorCode.CONFLICT, "This withdrawal is already " + withdrawal.getStatus());
		}
		return withdrawal;
	}

	private WithdrawalBatch lockSent(UUID batchId) {
		WithdrawalBatch batch = lock(batchId);
		requireStatus(batch, BatchStatus.SENT, batch.getStatus() == BatchStatus.CREATED
				? "Mark the batch as being paid first" : "This batch is " + batch.getStatus());
		return batch;
	}

	private static void requireStatus(WithdrawalBatch batch, BatchStatus expected, String message) {
		if (batch.getStatus() != expected) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, message);
		}
	}

	private BatchResponse summary(WithdrawalBatch batch, List<Withdrawal> items) {
		long paid = items.stream().filter(w -> w.getStatus() == WithdrawalStatus.PAID).count();
		long failed = items.stream()
			.filter(w -> w.getStatus() == WithdrawalStatus.FAILED || w.getStatus() == WithdrawalStatus.RETURNED).count();
		long outstanding = items.stream()
			.filter(w -> w.getStatus() == WithdrawalStatus.BATCHED || w.getStatus() == WithdrawalStatus.PROCESSING).count();
		return BatchResponse.of(batch, paid, failed, outstanding);
	}

	private void publishBatch(String topic, String type, WithdrawalBatch batch) {
		outbox.publish(DomainEvent.of(topic, type, ENTITY, batch.getId(),
				Map.of("batchId", batch.getId().toString(), "reference", batch.getReference(),
						"currency", batch.getCurrency(), "items", batch.getItemCount(),
						"total", batch.total().amount().toPlainString())));
	}

	private WithdrawalBatch load(UUID batchId) {
		return batches.findById(batchId).orElseThrow(() -> new ResourceNotFoundException("Withdrawal batch", batchId));
	}

	private WithdrawalBatch lock(UUID batchId) {
		return batches.findByIdForUpdate(batchId)
			.orElseThrow(() -> new ResourceNotFoundException("Withdrawal batch", batchId));
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

	/** A file kept with a batch: Excel, or CSV for batches from before Excel files. */
	public record PayoutFile(String filename, byte[] content) {

		public boolean excel() {
			return filename.toLowerCase(Locale.ROOT).endsWith(".xlsx");
		}

	}

}
