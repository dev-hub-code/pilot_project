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
import com.sealease.backend.withdrawal.dto.BatchDetail;
import com.sealease.backend.withdrawal.dto.BatchFileResponse;
import com.sealease.backend.withdrawal.dto.BatchResponse;
import com.sealease.backend.withdrawal.dto.ReconciliationResult;
import com.sealease.backend.withdrawal.dto.WithdrawalResponse;
import com.sealease.backend.withdrawal.entity.BatchStatus;
import com.sealease.backend.withdrawal.entity.Withdrawal;
import com.sealease.backend.withdrawal.entity.WithdrawalBatch;
import com.sealease.backend.withdrawal.entity.WithdrawalKind;
import com.sealease.backend.withdrawal.entity.WithdrawalStatus;
import com.sealease.backend.withdrawal.repository.WithdrawalBatchRepository;
import com.sealease.backend.withdrawal.repository.WithdrawalRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Paying the monthly bank withdrawals: each run's withdrawals form a batch whose Excel file (serial number, user id,
 * name, account number, IFSC code, amount) is made and kept (encrypted) with it. Finance pays the file by hand and
 * uploads it back with a Status per row, which completes or fails each withdrawal; items can also be settled one by
 * one.
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
	private static final int MAX_REPORTED_ERRORS = 10;
	private static final String DEFAULT_FAILURE = "The bank did not pay it";

	private final WithdrawalBatchRepository batches;
	private final WithdrawalRepository withdrawals;
	private final WithdrawalService withdrawalService;
	private final BankAccountService bankAccounts;
	private final LedgerService ledger;
	private final SupportService support;
	private final OutboxPublisher outbox;
	private final AuditService audit;
	private final BatchFileStore files;
	private final Clock clock;

	public PayoutBatchService(WithdrawalBatchRepository batches, WithdrawalRepository withdrawals,
			WithdrawalService withdrawalService, BankAccountService bankAccounts, LedgerService ledger,
			SupportService support, OutboxPublisher outbox, AuditService audit, BatchFileStore files, Clock clock) {
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

	/** The batch of one month's rent or referral withdrawals, with its Excel file. Caller's transaction. */
	WithdrawalBatch createScheduled(WithdrawalKind kind, YearMonth period, List<Withdrawal> scheduled) {
		Money total = scheduled.stream().map(Withdrawal::amount).reduce(Money::plus).orElseThrow();
		WithdrawalBatch batch = batches.saveAndFlush(WithdrawalBatch.scheduled(reference(), total, scheduled.size(), kind, period));
		scheduled.forEach(w -> w.addToBatch(batch.getId()));
		withdrawals.flush();
		files.save(batch.getId(), BatchFileStore.PAYMENT, filename(kind, period), paymentFile(scheduled), null, null, null);
		audit.record(AuditRecord.of(null, AuditAction.WITHDRAWAL_BATCH_CREATED, ENTITY, batch.getId())
			.withNewValue(new LinkedHashMap<>(Map.of("reference", batch.getReference(), "kind", kind, "period", period.toString(),
					"items", scheduled.size(), "total", batch.total().toString()))));
		publishBatch(KafkaTopics.WITHDRAWAL_BATCHED, "WithdrawalBatchCreated", batch);
		return batch;
	}

	/** "Rent-October-2026.xlsx": the kind of withdrawal, the month's name and the year. */
	static String filename(WithdrawalKind kind, YearMonth period) {
		return kind.label() + "-" + period.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + "-"
				+ period.getYear() + ".xlsx";
	}

	private String reference() {
		return "WB-" + String.format("%05d", batches.nextNumber());
	}

	/**
	 * The batch's Excel file with full account details, as kept when the batch was made. Every download is audited.
	 * Refused before payment while any payee account is no longer verified.
	 */
	@Transactional
	public PayoutFile file(UUID actorId, UUID batchId) {
		WithdrawalBatch batch = load(batchId);
		List<Withdrawal> items = withdrawals.findByBatchIdOrderByCreatedAtAscIdAsc(batchId);
		if (batch.getStatus() == BatchStatus.CREATED) {
			List<String> unverified = items.stream()
				.filter(w -> !bankAccounts.payoutAccount(w.getBankAccountId()).map(PayoutAccount::isVerified).orElse(false))
				.map(Withdrawal::getReference)
				.toList();
			if (!unverified.isEmpty()) {
				throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Bank accounts are no longer verified for "
						+ String.join(", ", unverified) + ": pay the others, and mark these failed when uploading the file");
			}
		}
		BatchFileStore.StoredFile file = files.payment(batchId)
			.orElseThrow(() -> new ResourceNotFoundException("Batch file", batchId));
		audit.record(AuditRecord.of(actorId, AuditAction.WITHDRAWAL_BATCH_EXPORTED, ENTITY, batchId)
			.withNewValue(Map.of("reference", batch.getReference(), "items", items.size(), "file", file.filename())));
		return new PayoutFile(file.filename(), file.content());
	}

	/** One of the files kept with a batch (the payment file or an uploaded one). Audited. */
	@Transactional
	public PayoutFile storedFile(UUID actorId, UUID batchId, UUID fileId) {
		WithdrawalBatch batch = load(batchId);
		BatchFileStore.StoredFile file = files.content(batchId, fileId);
		audit.record(AuditRecord.of(actorId, AuditAction.WITHDRAWAL_BATCH_EXPORTED, ENTITY, batchId)
			.withNewValue(Map.of("reference", batch.getReference(), "file", file.filename())));
		return new PayoutFile(file.filename(), file.content());
	}

	@Transactional(readOnly = true)
	public List<BatchFileResponse> files(UUID batchId) {
		load(batchId);
		return files.list(batchId);
	}

	/**
	 * Reconciles a batch from its Excel file uploaded back after paying it by hand: each row names a {@code User ID}
	 * and its {@code Status}: PAID (the withdrawal completes; {@code Remarks} may hold the bank reference), FAILED
	 * (held for finance, with a ticket; {@code Remarks} says why) or blank (still outstanding). Every row is checked
	 * before anything is applied: one bad row refuses the whole file. Uploading the same file again changes nothing.
	 */
	@Transactional
	public ReconciliationResult reconcile(UUID actorId, UUID batchId, String filename, byte[] content) {
		WithdrawalBatch batch = lock(batchId);
		// A closed batch takes the same file again (nothing changes); a row that would change it is refused below.
		if (batch.getStatus() == BatchStatus.CANCELLED) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "This batch is " + batch.getStatus());
		}
		List<WithdrawalSheet.Entry> entries = WithdrawalSheet.read(content);
		if (entries.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "The file has no withdrawals in it");
		}
		for (String required : List.of("userid", "status")) {
			if (!WithdrawalSheet.hasColumn(entries, required)) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED, "The file has no \"" + (required.equals("userid")
						? "User ID" : "Status") + "\" column: upload the batch's file with the Status column filled in");
			}
		}

		Map<String, Withdrawal> items = new HashMap<>();
		withdrawals.lockBatchItems(batchId).forEach(w -> items.put(w.getUserId().toString(), w));
		List<String> errors = new ArrayList<>();
		List<RowOutcome> outcomes = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		int unchanged = 0;
		for (WithdrawalSheet.Entry entry : entries) {
			String where = "Row " + entry.row() + ": ";
			String userId = entry.get("userid").toLowerCase(Locale.ROOT);
			Withdrawal w = items.get(userId);
			if (w == null) {
				errors.add(where + "user \"" + entry.get("userid") + "\" is not in " + batch.getReference());
				continue;
			}
			if (!seen.add(userId)) {
				errors.add(where + "user " + userId + " appears more than once");
				continue;
			}
			String status = entry.get("status").toUpperCase(Locale.ROOT);
			String remarks = blankToNull(entry.get("remarks"));
			switch (status) {
				case "", "PENDING", "PROCESSING" -> unchanged++;
				case "PAID", "COMPLETED", "SUCCESS", "SUCCESSFUL" -> {
					if (w.getStatus() == WithdrawalStatus.PAID) {
						unchanged++;
					}
					else if (w.getStatus() == WithdrawalStatus.BATCHED || w.getStatus() == WithdrawalStatus.PROCESSING) {
						outcomes.add(new RowOutcome(w, true, remarks));
					}
					else {
						errors.add(where + w.getReference() + " is already " + w.getStatus()
								+ (w.getStatus() == WithdrawalStatus.FAILED ? ": resolve it from the withdrawal" : ""));
					}
				}
				case "FAILED", "FAIL", "RETURNED", "REJECTED" -> {
					if (w.getStatus() == WithdrawalStatus.FAILED || w.getStatus() == WithdrawalStatus.RETURNED) {
						unchanged++;
					}
					else if (w.getStatus() == WithdrawalStatus.BATCHED || w.getStatus() == WithdrawalStatus.PROCESSING) {
						outcomes.add(new RowOutcome(w, false, remarks == null ? DEFAULT_FAILURE : remarks));
					}
					else {
						errors.add(where + w.getReference() + " is already " + w.getStatus());
					}
				}
				default -> errors.add(where + "status \"" + entry.get("status") + "\" is not PAID, FAILED or blank");
			}
		}
		if (!errors.isEmpty()) {
			String shown = String.join("; ", errors.subList(0, Math.min(errors.size(), MAX_REPORTED_ERRORS)));
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Nothing was changed. " + shown
					+ (errors.size() > MAX_REPORTED_ERRORS ? " (and " + (errors.size() - MAX_REPORTED_ERRORS) + " more)" : ""));
		}

		if (batch.getStatus() == BatchStatus.CREATED && !outcomes.isEmpty()) {
			send(actorId, batch);
		}
		int paid = 0;
		int failed = 0;
		for (RowOutcome outcome : outcomes) {
			if (outcome.paid()) {
				pay(actorId, batch, outcome.withdrawal(), outcome.remarks());
				paid++;
			}
			else {
				fail(actorId, batch, outcome.withdrawal(), outcome.remarks());
				failed++;
			}
		}
		if (batch.getStatus() == BatchStatus.SENT) {
			closeIfDone(batch);
		}
		String name = filename == null || filename.isBlank() ? batch.getReference() + "-status.xlsx" : filename.strip();
		files.save(batchId, BatchFileStore.RECONCILIATION, name.length() > 200 ? name.substring(0, 200) : name, content,
				actorId, paid, failed);
		audit.record(AuditRecord.of(actorId, AuditAction.WITHDRAWAL_BATCH_RECONCILED, ENTITY, batchId)
			.withNewValue(Map.of("reference", batch.getReference(), "file", name, "paid", paid, "failed", failed,
					"unchanged", unchanged)));
		return new ReconciliationResult(paid, failed, unchanged, detail(batchId));
	}

	private record RowOutcome(Withdrawal withdrawal, boolean paid, String remarks) {
	}

	/** The Excel file: one row per withdrawal, with empty Status and Remarks columns for finance to fill in. */
	private byte[] paymentFile(List<Withdrawal> items) {
		Map<UUID, PayoutInstruction> payees = bankAccounts.payoutInstructions(
				items.stream().map(Withdrawal::getBankAccountId).distinct().toList());
		List<WithdrawalSheet.Line> lines = items.stream().sorted(Comparator.comparing(Withdrawal::getReference))
			.map(w -> {
				PayoutInstruction payee = payees.get(w.getBankAccountId());
				return new WithdrawalSheet.Line(w.getUserId().toString(), payee.holderName(), payee.accountNumber(),
						payee.routingCode(), w.amount().toMinorUnitScale());
			})
			.toList();
		return WithdrawalSheet.write(lines);
	}

	/** Finance is paying the file: its items are now in flight. */
	@Transactional
	public BatchDetail markSent(UUID actorId, UUID batchId) {
		WithdrawalBatch batch = lock(batchId);
		requireStatus(batch, BatchStatus.CREATED, "This batch was already sent");
		send(actorId, batch);
		return detail(batchId);
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

	/** Everything still in flight in the batch was paid (the usual case). */
	@Transactional
	public BatchDetail markRemainingPaid(UUID actorId, UUID batchId, String payoutReference) {
		WithdrawalBatch batch = lockSent(batchId);
		for (Withdrawal w : withdrawals.lockBatchItems(batchId)) {
			if (w.getStatus() == WithdrawalStatus.PROCESSING) {
				pay(actorId, batch, w, blankToNull(payoutReference));
			}
		}
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
		return new BatchDetail(summary(batch, items), items.stream().map(WithdrawalResponse::forStaff).toList());
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
				"Your withdrawal " + withdrawal.getReference() + " of " + withdrawal.amount().display() + " to "
						+ withdrawal.getBankName() + " •••• " + withdrawal.getBankAccountLast4()
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
