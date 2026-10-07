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

	@Column(name = "created_by", nullable = false, updatable = false)
	private UUID createdBy;

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
