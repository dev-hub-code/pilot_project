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

/**
 * One monthly payout of one holding: the month's rent plus the month's return of capital. Amounts
 * are fixed when the holding is created; paying it only records when and in which ledger
 * transaction the money was credited.
 */
@Entity
@Table(name = "payout_installments")
public class PayoutInstallment extends BaseEntity {

	@Column(name = "holding_id", nullable = false, updatable = false)
	private UUID holdingId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "product_id", nullable = false, updatable = false)
	private UUID productId;

	@Column(name = "installment_number", nullable = false, updatable = false)
	private int installmentNumber;

	@Column(name = "due_on", nullable = false, updatable = false)
	private LocalDate dueOn;

	@Column(name = "rent_amount", nullable = false, updatable = false, precision = 19, scale = 4)
	private BigDecimal rentAmount;

	@Column(name = "capital_amount", nullable = false, updatable = false, precision = 19, scale = 4)
	private BigDecimal capitalAmount;

	@Column(name = "currency", nullable = false, updatable = false, length = 3)
	private String currency;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private PayoutStatus status;

	@Column(name = "paid_at")
	private Instant paidAt;

	@Column(name = "ledger_transaction_id")
	private UUID ledgerTransactionId;

	protected PayoutInstallment() {
	}

	public PayoutInstallment(UUID holdingId, UUID userId, UUID productId, int installmentNumber, LocalDate dueOn,
			Money rent, Money capital) {
		this.holdingId = holdingId;
		this.userId = userId;
		this.productId = productId;
		this.installmentNumber = installmentNumber;
		this.dueOn = dueOn;
		this.rentAmount = rent.amount();
		this.capitalAmount = capital.amount();
		this.currency = rent.currency().getCurrencyCode();
		this.status = PayoutStatus.SCHEDULED;
	}

	public void markPaid(UUID ledgerTransactionId, Instant now) {
		if (status != PayoutStatus.SCHEDULED) {
			throw new IllegalStateException("Payout " + getId() + " is already " + status);
		}
		this.status = PayoutStatus.PAID;
		this.ledgerTransactionId = ledgerTransactionId;
		this.paidAt = now;
	}

	public boolean isDueOn(LocalDate day) {
		return !dueOn.isAfter(day);
	}

	public Money rent() {
		return Money.of(rentAmount, Currency.getInstance(currency));
	}

	public Money capital() {
		return Money.of(capitalAmount, Currency.getInstance(currency));
	}

	public Money total() {
		return rent().plus(capital());
	}

	public UUID getHoldingId() {
		return holdingId;
	}

	public UUID getUserId() {
		return userId;
	}

	public UUID getProductId() {
		return productId;
	}

	public int getInstallmentNumber() {
		return installmentNumber;
	}

	public LocalDate getDueOn() {
		return dueOn;
	}

	public PayoutStatus getStatus() {
		return status;
	}

	public Instant getPaidAt() {
		return paidAt;
	}

	public UUID getLedgerTransactionId() {
		return ledgerTransactionId;
	}

}
