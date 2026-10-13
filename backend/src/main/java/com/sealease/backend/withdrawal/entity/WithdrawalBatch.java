package com.sealease.backend.withdrawal.entity;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Currency;
import java.util.UUID;

/** Approved withdrawals in one currency, paid together by one bank file. */
@Entity
@Table(name = "withdrawal_batches")
public class WithdrawalBatch extends BaseEntity {

	@Column(name = "reference", nullable = false, updatable = false, length = 20)
	private String reference;

	@Column(name = "currency", nullable = false, updatable = false, length = 3)
	private String currency;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private BatchStatus status;

	@Column(name = "item_count", nullable = false, updatable = false)
	private int itemCount;

	@Column(name = "total_amount", nullable = false, updatable = false, precision = 19, scale = 4)
	private BigDecimal totalAmount;

	/** Null for a batch made by the monthly withdrawal run. */
	@Column(name = "created_by", updatable = false)
	private UUID createdBy;

	/** The month ("yyyy-MM") of a batch made by the monthly withdrawal run; null for batches made by hand. */
	@Column(name = "period", updatable = false, length = 7)
	private String period;

	/** Which monthly run made it: rent or referral; null for batches made by hand before the runs. */
	@Enumerated(EnumType.STRING)
	@Column(name = "kind", updatable = false, length = 20)
	private WithdrawalKind kind;

	@Column(name = "sent_by")
	private UUID sentBy;

	@Column(name = "sent_at")
	private Instant sentAt;

	@Column(name = "closed_at")
	private Instant closedAt;

	protected WithdrawalBatch() {
	}

	public WithdrawalBatch(String reference, Money total, int itemCount, UUID createdBy) {
		this.reference = reference;
		this.currency = total.currency().getCurrencyCode();
		this.totalAmount = total.amount();
		this.itemCount = itemCount;
		this.createdBy = createdBy;
		this.status = BatchStatus.CREATED;
	}

	/** The batch of one month's scheduled withdrawals. */
	public static WithdrawalBatch scheduled(String reference, Money total, int itemCount, WithdrawalKind kind,
			YearMonth period) {
		WithdrawalBatch batch = new WithdrawalBatch(reference, total, itemCount, null);
		batch.kind = kind;
		batch.period = period.toString();
		return batch;
	}

	public void markSent(UUID actorId, Instant now) {
		status = BatchStatus.SENT;
		sentBy = actorId;
		sentAt = now;
	}

	public void close(Instant now) {
		status = BatchStatus.CLOSED;
		closedAt = now;
	}

	public void cancel() {
		status = BatchStatus.CANCELLED;
	}

	public Money total() {
		return Money.of(totalAmount, Currency.getInstance(currency));
	}

	public String getReference() {
		return reference;
	}

	public String getCurrency() {
		return currency;
	}

	public BatchStatus getStatus() {
		return status;
	}

	public int getItemCount() {
		return itemCount;
	}

	public UUID getCreatedBy() {
		return createdBy;
	}

	public WithdrawalKind getKind() {
		return kind;
	}

	public String getPeriod() {
		return period;
	}

	public UUID getSentBy() {
		return sentBy;
	}

	public Instant getSentAt() {
		return sentAt;
	}

	public Instant getClosedAt() {
		return closedAt;
	}

}
