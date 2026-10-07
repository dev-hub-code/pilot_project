package com.sealease.backend.earning.entity;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.earning.service.RentalSplit;
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

/** One investor's share of one distributed rental receipt. Append-only. */
@Entity
@Immutable
@Table(name = "earnings")
public class Earning {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.TIME)
	private UUID id;

	@Column(name = "receipt_id", nullable = false)
	private UUID receiptId;

	@Column(name = "holding_id", nullable = false)
	private UUID holdingId;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "product_id", nullable = false)
	private UUID productId;

	@Column(name = "period_number", nullable = false)
	private int periodNumber;

	@Column(name = "ownership_percent", nullable = false, precision = 9, scale = 4)
	private BigDecimal ownershipPercent;

	@Column(name = "gross_amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal grossAmount;

	@Column(name = "fee_amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal feeAmount;

	@Column(name = "net_amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal netAmount;

	@Column(name = "currency", nullable = false, length = 3)
	private String currency;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected Earning() {
	}

	public Earning(UUID receiptId, UUID productId, int periodNumber, RentalSplit.Share share, Instant createdAt) {
		this.receiptId = receiptId;
		this.holdingId = share.holdingId();
		this.userId = share.userId();
		this.productId = productId;
		this.periodNumber = periodNumber;
		this.ownershipPercent = share.ownershipPercent();
		this.grossAmount = share.gross().amount();
		this.feeAmount = share.fee().amount();
		this.netAmount = share.net().amount();
		this.currency = share.net().currency().getCurrencyCode();
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public UUID getReceiptId() {
		return receiptId;
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

	public int getPeriodNumber() {
		return periodNumber;
	}

	public BigDecimal getOwnershipPercent() {
		return ownershipPercent;
	}

	public Money gross() {
		return Money.of(grossAmount, Currency.getInstance(currency));
	}

	public Money fee() {
		return Money.of(feeAmount, Currency.getInstance(currency));
	}

	public Money net() {
		return Money.of(netAmount, Currency.getInstance(currency));
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
