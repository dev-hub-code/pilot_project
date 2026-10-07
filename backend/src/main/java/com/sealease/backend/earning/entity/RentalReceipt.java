package com.sealease.backend.earning.entity;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;

/** Rent the lessee actually paid for one period of an offering's lease. */
@Entity
@Table(name = "rental_receipts")
public class RentalReceipt extends BaseEntity {

	@Column(name = "product_id", nullable = false, updatable = false)
	private UUID productId;

	@Column(name = "period_number", nullable = false, updatable = false)
	private int periodNumber;

	@Column(name = "period_starts_on", nullable = false, updatable = false)
	private LocalDate periodStartsOn;

	@Column(name = "period_ends_on", nullable = false, updatable = false)
	private LocalDate periodEndsOn;

	@Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 4)
	private BigDecimal amount;

	@Column(name = "currency", nullable = false, updatable = false, length = 3)
	private String currency;

	@Column(name = "expected_amount", nullable = false, updatable = false, precision = 19, scale = 4)
	private BigDecimal expectedAmount;

	@Column(name = "received_on", nullable = false, updatable = false)
	private LocalDate receivedOn;

	@Column(name = "external_reference", nullable = false, updatable = false, length = 100)
	private String externalReference;

	@Column(name = "note", updatable = false, length = 500)
	private String note;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private ReceiptStatus status;

	@Column(name = "recorded_by", nullable = false, updatable = false)
	private UUID recordedBy;

	@Column(name = "decided_by")
	private UUID decidedBy;

	@Column(name = "decided_at")
	private Instant decidedAt;

	@Column(name = "rejection_reason", length = 500)
	private String rejectionReason;

	@Column(name = "management_fee_percent", precision = 5, scale = 2)
	private BigDecimal managementFeePercent;

	@Column(name = "ledger_transaction_id")
	private UUID ledgerTransactionId;

	protected RentalReceipt() {
	}

	public RentalReceipt(UUID productId, int periodNumber, LocalDate periodStartsOn, LocalDate periodEndsOn,
			Money amount, Money expectedAmount, LocalDate receivedOn, String externalReference, String note,
			UUID recordedBy) {
		this.productId = productId;
		this.periodNumber = periodNumber;
		this.periodStartsOn = periodStartsOn;
		this.periodEndsOn = periodEndsOn;
		this.amount = amount.amount();
		this.currency = amount.currency().getCurrencyCode();
		this.expectedAmount = expectedAmount.amount();
		this.receivedOn = receivedOn;
		this.externalReference = externalReference;
		this.note = note;
		this.recordedBy = recordedBy;
		this.status = ReceiptStatus.RECORDED;
	}

	public void distribute(UUID approverId, BigDecimal feePercent, UUID ledgerTransactionId, Instant now) {
		this.status = ReceiptStatus.DISTRIBUTED;
		this.decidedBy = approverId;
		this.decidedAt = now;
		this.managementFeePercent = feePercent;
		this.ledgerTransactionId = ledgerTransactionId;
	}

	public void reject(UUID actorId, String reason, Instant now) {
		this.status = ReceiptStatus.REJECTED;
		this.decidedBy = actorId;
		this.decidedAt = now;
		this.rejectionReason = reason;
	}

	public Money amount() {
		return Money.of(amount, Currency.getInstance(currency));
	}

	public Money expectedAmount() {
		return Money.of(expectedAmount, Currency.getInstance(currency));
	}

	public UUID getProductId() {
		return productId;
	}

	public int getPeriodNumber() {
		return periodNumber;
	}

	public LocalDate getPeriodStartsOn() {
		return periodStartsOn;
	}

	public LocalDate getPeriodEndsOn() {
		return periodEndsOn;
	}

	public LocalDate getReceivedOn() {
		return receivedOn;
	}

	public String getExternalReference() {
		return externalReference;
	}

	public String getNote() {
		return note;
	}

	public ReceiptStatus getStatus() {
		return status;
	}

	public UUID getRecordedBy() {
		return recordedBy;
	}

	public UUID getDecidedBy() {
		return decidedBy;
	}

	public Instant getDecidedAt() {
		return decidedAt;
	}

	public String getRejectionReason() {
		return rejectionReason;
	}

	public BigDecimal getManagementFeePercent() {
		return managementFeePercent;
	}

	public UUID getLedgerTransactionId() {
		return ledgerTransactionId;
	}

}
