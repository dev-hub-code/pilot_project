package com.sealease.backend.referral.entity;

import com.sealease.backend.common.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

/** One upline's commission on the rent part of one monthly payout to a referred investor. Append-only. */
@Entity
@Immutable
@Table(name = "referral_earnings")
public class ReferralEarning {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.TIME)
	private UUID id;

	@Column(name = "source_installment_id", nullable = false)
	private UUID sourceInstallmentId;

	@Column(name = "source_user_id", nullable = false)
	private UUID sourceUserId;

	@Column(name = "beneficiary_user_id", nullable = false)
	private UUID beneficiaryUserId;

	@Column(name = "product_id", nullable = false)
	private UUID productId;

	@Column(name = "installment_number", nullable = false)
	private int installmentNumber;

	@Column(name = "level", nullable = false)
	private short level;

	@Column(name = "base_amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal baseAmount;

	@Column(name = "rate_percent", nullable = false, precision = 5, scale = 3)
	private BigDecimal ratePercent;

	@Column(name = "amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal amount;

	@Column(name = "currency", nullable = false, length = 3)
	private String currency;

	@Column(name = "rate_version_id", nullable = false)
	private UUID rateVersionId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected ReferralEarning() {
	}

	public ReferralEarning(UUID sourceInstallmentId, UUID sourceUserId, UUID beneficiaryUserId, UUID productId,
			int installmentNumber, int level, Money base, BigDecimal ratePercent, Money amount, UUID rateVersionId,
			Instant createdAt) {
		this.sourceInstallmentId = sourceInstallmentId;
		this.sourceUserId = sourceUserId;
		this.beneficiaryUserId = beneficiaryUserId;
		this.productId = productId;
		this.installmentNumber = installmentNumber;
		this.level = (short) level;
		this.baseAmount = base.amount();
		this.ratePercent = ratePercent;
		this.amount = amount.amount();
		this.currency = amount.currency().getCurrencyCode();
		this.rateVersionId = rateVersionId;
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public UUID getSourceInstallmentId() {
		return sourceInstallmentId;
	}

	public UUID getSourceUserId() {
		return sourceUserId;
	}

	public UUID getBeneficiaryUserId() {
		return beneficiaryUserId;
	}

	public UUID getProductId() {
		return productId;
	}

	public int getInstallmentNumber() {
		return installmentNumber;
	}

	public int getLevel() {
		return level;
	}

	public BigDecimal getRatePercent() {
		return ratePercent;
	}

	public Money base() {
		return Money.of(baseAmount, Currency.getInstance(currency));
	}

	public Money amount() {
		return Money.of(amount, Currency.getInstance(currency));
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
