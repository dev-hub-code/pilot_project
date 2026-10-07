package com.sealease.backend.investment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Append-only record of every capacity change, with the counters after the change. */
@Entity
@Immutable
@Table(name = "capacity_movements")
public class CapacityMovement {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.TIME)
	private UUID id;

	@Column(name = "product_id", nullable = false)
	private UUID productId;

	@Enumerated(EnumType.STRING)
	@Column(name = "movement_type", nullable = false, length = 10)
	private CapacityMovementType movementType;

	@Column(name = "amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal amount;

	@Column(name = "reference", nullable = false, length = 100)
	private String reference;

	@Column(name = "investor_user_id", nullable = false)
	private UUID investorUserId;

	@Column(name = "reserved_after", nullable = false, precision = 19, scale = 4)
	private BigDecimal reservedAfter;

	@Column(name = "committed_after", nullable = false, precision = 19, scale = 4)
	private BigDecimal committedAfter;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected CapacityMovement() {
	}

	public CapacityMovement(UUID productId, CapacityMovementType movementType, BigDecimal amount, String reference,
			UUID investorUserId, BigDecimal reservedAfter, BigDecimal committedAfter, Instant createdAt) {
		this.productId = productId;
		this.movementType = movementType;
		this.amount = amount;
		this.reference = reference;
		this.investorUserId = investorUserId;
		this.reservedAfter = reservedAfter;
		this.committedAfter = committedAfter;
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public UUID getProductId() {
		return productId;
	}

	public CapacityMovementType getMovementType() {
		return movementType;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public String getReference() {
		return reference;
	}

	public UUID getInvestorUserId() {
		return investorUserId;
	}

	public BigDecimal getReservedAfter() {
		return reservedAfter;
	}

	public BigDecimal getCommittedAfter() {
		return committedAfter;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
