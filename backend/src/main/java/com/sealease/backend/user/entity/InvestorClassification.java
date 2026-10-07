package com.sealease.backend.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/** Append-only record of every change of an investor's classification. */
@Entity
@Immutable
@Table(name = "investor_classifications")
public class InvestorClassification {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.TIME)
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "previous_type", nullable = false, length = 10)
	private InvestorType previousType;

	@Enumerated(EnumType.STRING)
	@Column(name = "new_type", nullable = false, length = 10)
	private InvestorType newType;

	@Column(name = "reason", nullable = false, length = 500)
	private String reason;

	@Column(name = "decided_by", nullable = false)
	private UUID decidedBy;

	@Column(name = "decided_at", nullable = false)
	private Instant decidedAt;

	protected InvestorClassification() {
	}

	public InvestorClassification(UUID userId, InvestorType previousType, InvestorType newType, String reason,
			UUID decidedBy, Instant decidedAt) {
		this.userId = userId;
		this.previousType = previousType;
		this.newType = newType;
		this.reason = reason;
		this.decidedBy = decidedBy;
		this.decidedAt = decidedAt;
	}

	public UUID getId() {
		return id;
	}

	public InvestorType getPreviousType() {
		return previousType;
	}

	public InvestorType getNewType() {
		return newType;
	}

	public String getReason() {
		return reason;
	}

	public UUID getDecidedBy() {
		return decidedBy;
	}

	public Instant getDecidedAt() {
		return decidedAt;
	}

}
