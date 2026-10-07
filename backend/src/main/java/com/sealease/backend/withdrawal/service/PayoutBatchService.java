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
import com.sealease.backend.kafka.KafkaTopics;
import com.sealease.backend.ledger.service.AccountType;
import com.sealease.backend.ledger.service.LedgerService;
import com.sealease.backend.ledger.service.Posting;
import com.sealease.backend.ledger.service.TransactionType;
import com.sealease.backend.outbox.DomainEvent;
import com.sealease.backend.outbox.OutboxPublisher;
import com.sealease.backend.withdrawal.dto.BatchDetail;
import com.sealease.backend.withdrawal.dto.BatchResponse;
import com.sealease.backend.withdrawal.dto.WithdrawalResponse;
import com.sealease.backend.withdrawal.entity.BatchStatus;
import com.sealease.backend.withdrawal.entity.Withdrawal;
import com.sealease.backend.withdrawal.entity.WithdrawalBatch;
import com.sealease.backend.withdrawal.entity.WithdrawalStatus;
import com.sealease.backend.withdrawal.repository.WithdrawalBatchRepository;
import com.sealease.backend.withdrawal.repository.WithdrawalRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Paying approved withdrawals: grouping them into a batch per currency, producing the bank file,
 * and reconciling each item against the bank's outcome.
 *
 * <p>Lock order is <em>batch, then its withdrawals in id order</em>. Paid items move their amount
 * from "withdrawals in transit" to cash; failed items return it to the investor. A batch closes
 * once no item is outstanding.
 */
@Service
public class PayoutBatchService {

	private static final String ENTITY = "WITHDRAWAL_BATCH";

	private final WithdrawalBatchRepository batches;
	private final WithdrawalRepository withdrawals;
	private final WithdrawalService withdrawalService;
	private final BankAccountService bankAccounts;
	private final LedgerService ledger;
	private final OutboxPublisher outbox;
	private final AuditService audit;
	private final WithdrawalProperties properties;
	private final Clock clock;

	public PayoutBatchService(WithdrawalBatchRepository batches, WithdrawalRepository withdrawals,
			WithdrawalService withdrawalService, BankAccountService bankAccounts, LedgerService ledger,
			OutboxPublisher outbox, AuditService audit, WithdrawalProperties properties, Clock clock) {
		this.batches = batches;
		this.withdrawals = withdrawals;
		this.withdrawalService = withdrawalService;
		this.bankAccounts = bankAccounts;
		this.ledger = ledger;
		this.outbox = outbox;
		this.audit = audit;
		this.properties = properties;
		this.clock = clock;
	}

	/** Takes the oldest approved withdrawals in a currency (up to the batch size) into a new batch. */
	@Transactional
	public BatchDetail create(UUID actorId, String currencyCode) {
		Currency currency = Currency.getInstance(currencyCode);
		List<Withdrawal> approved = withdrawals.findForBatching(WithdrawalStatus.APPROVED, currency.getCurrencyCode(),
				PageRequest.of(0, properties.maxBatchSize()));
		if (approved.isEmpty()) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"There are no approved " + currency.getCurrencyCode() + " withdrawals to pay");
		}
		Money total = approved.stream().map(Withdrawal::amount).reduce(Money::plus).orElseThrow();
		WithdrawalBatch batch = batches.saveAndFlush(new WithdrawalBatch("WB-" + String.format("%05d", batches.nextNumber()),
				total, approved.size(), actorId));
		approved.forEach(w -> w.addToBatch(batch.getId()));
		withdrawals.flush();
		audit.record(AuditRecord.of(actorId, AuditAction.WITHDRAWAL_BATCH_CREATED, ENTITY, batch.getId())
			.withNewValue(Map.of("reference", batch.getReference(), "items", approved.size(), "total", total.toString())));
		publishBatch(KafkaTopics.WITHDRAWAL_BATCHED, "WithdrawalBatchCreated", batch);
		return detail(batch.getId());
	}

	/** Puts the withdrawals of a batch that was never sent back in the approved queue. */
	@Transactional
	public BatchDetail cancel(UUID actorId, UUID batchId) {
		WithdrawalBatch batch = lock(batchId);
		requireStatus(batch, BatchStatus.CREATED, "Only batches not yet sent to the bank can be cancelled");
		List<Withdrawal> items = withdrawals.lockBatchItems(batchId);
		items.forEach(Withdrawal::removeFromBatch);
		batch.cancel();
		withdrawals.flush();
		audit.record(AuditRecord.of(actorId, AuditAction.WITHDRAWAL_BATCH_CANCELLED, ENTITY, batchId)
			.withNewValue(Map.of("items", items.size())));
		return detail(batchId);
	}

	/**
	 * The bank payment file (CSV) with full account details. Every download is audited. Refused while
	 * any payee account is no longer verified: cancel the batch and reject those withdrawals first.
	 */
	@Transactional
	public PayoutFile file(UUID actorId, UUID batchId) {
		WithdrawalBatch batch = load(batchId);
		if (batch.getStatus() != BatchStatus.CREATED && batch.getStatus() != BatchStatus.SENT) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "This batch is " + batch.getStatus());
		}
		List<Withdrawal> items = withdrawals.findByBatchIdOrderByCreatedAtAscIdAsc(batchId);
		List<String> unverified = items.stream()
			.filter(w -> !bankAccounts.payoutAccount(w.getBankAccountId()).map(PayoutAccount::isVerified).orElse(false))
			.map(Withdrawal::getReference)
			.toList();
		if (!unverified.isEmpty() && batch.getStatus() == BatchStatus.CREATED) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Bank accounts are no longer verified for "
					+ String.join(", ", unverified) + ": cancel the batch and reject those withdrawals");
		}
		Map<UUID, PayoutInstruction> payees = bankAccounts.payoutInstructions(
				items.stream().map(Withdrawal::getBankAccountId).distinct().toList());
		StringBuilder csv = new StringBuilder(
				"reference,beneficiary_name,account_number,routing_code,bank_name,country,amount,currency,remittance\r\n");
		for (Withdrawal w : items) {
			PayoutInstruction payee = payees.get(w.getBankAccountId());
			csv.append(String.join(",", cell(w.getReference()), cell(payee.holderName()), cell(payee.accountNumber()),
					cell(payee.routingCode()), cell(payee.bankName()), cell(payee.country()),
					w.amount().toMinorUnitScale().toPlainString(), w.getCurrency(),
					cell("SeaLease withdrawal " + w.getReference()))).append("\r\n");
		}
		audit.record(AuditRecord.of(actorId, AuditAction.WITHDRAWAL_BATCH_EXPORTED, ENTITY, batchId)
			.withNewValue(Map.of("reference", batch.getReference(), "items", items.size())));
		return new PayoutFile(batch.getReference() + ".csv", csv.toString());
	}

	/** The file has been submitted to the bank: its items are now in flight. */
	@Transactional
	public BatchDetail markSent(UUID actorId, UUID batchId) {
		WithdrawalBatch batch = lock(batchId);
		requireStatus(batch, BatchStatus.CREATED, "This batch was already sent");
		withdrawals.lockBatchItems(batchId).forEach(Withdrawal::markProcessing);
		batch.markSent(actorId, clock.instant());
		withdrawals.flush();
		audit.record(AuditRecord.of(actorId, AuditAction.WITHDRAWAL_BATCH_SENT, ENTITY, batchId)
			.withNewValue(Map.of("reference", batch.getReference())));
		publishBatch(KafkaTopics.WITHDRAWAL_PROCESSING, "WithdrawalBatchSent", batch);
		return detail(batchId);
	}

	/** Reconciliation: the bank paid this item. */
	@Transactional
	public BatchDetail markPaid(UUID actorId, UUID batchId, UUID withdrawalId, String payoutReference) {
		WithdrawalBatch batch = lockSent(batchId);
		pay(actorId, batch, item(batchId, withdrawalId), blankToNull(payoutReference));
		closeIfDone(batch);
		return detail(batchId);
	}

	/** Reconciliation: the bank returned this item; the money goes back to the investor's balance. */
	@Transactional
	public BatchDetail markFailed(UUID actorId, UUID batchId, UUID withdrawalId, String reason) {
		WithdrawalBatch batch = lockSent(batchId);
		Withdrawal withdrawal = item(batchId, withdrawalId);
		withdrawal.markFailed(reason.strip(), clock.instant());
		withdrawals.flush();
		withdrawalService.release(withdrawal, actorId, "failed");
		audit.record(AuditRecord.of(actorId, AuditAction.WITHDRAWAL_FAILED, WithdrawalService.ENTITY, withdrawalId)
			.withNewValue(Map.of("batch", batch.getReference(), "reason", reason.strip())));
		withdrawalService.publish(KafkaTopics.WITHDRAWAL_FAILED, "WithdrawalFailed", withdrawal,
				Map.of("reason", reason.strip()));
		closeIfDone(batch);
		return detail(batchId);
	}

	/** Reconciliation: everything still outstanding in the batch was paid (the usual case). */
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

	private void closeIfDone(WithdrawalBatch batch) {
		boolean outstanding = withdrawals.findByBatchIdOrderByCreatedAtAscIdAsc(batch.getId()).stream()
			.anyMatch(w -> w.getStatus() == WithdrawalStatus.PROCESSING);
		if (!outstanding) {
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
				? "Mark the batch as sent to the bank first" : "This batch is " + batch.getStatus());
		return batch;
	}

	private static void requireStatus(WithdrawalBatch batch, BatchStatus expected, String message) {
		if (batch.getStatus() != expected) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, message);
		}
	}

	private BatchResponse summary(WithdrawalBatch batch, List<Withdrawal> items) {
		long paid = items.stream().filter(w -> w.getStatus() == WithdrawalStatus.PAID).count();
		long failed = items.stream().filter(w -> w.getStatus() == WithdrawalStatus.FAILED).count();
		long outstanding = items.stream().filter(w -> w.getStatus() == WithdrawalStatus.PROCESSING).count();
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

	/**
	 * A CSV field, quoted, with a leading formula character neutralised so the file is safe to open
	 * in a spreadsheet (CSV injection).
	 */
	static String cell(String value) {
		String v = value == null ? "" : value;
		if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
			v = "'" + v;
		}
		return "\"" + v.replace("\"", "\"\"") + "\"";
	}

	/** A generated bank file. */
	public record PayoutFile(String filename, String content) {
	}

}
