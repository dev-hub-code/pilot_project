package com.sealease.backend.kyc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/** The support ticket opened for an investor who had not verified their identity a day after registering. */
@Entity
@Immutable
@Table(name = "kyc_follow_ups")
public class KycFollowUp {

	@Id
	@Column(name = "user_id")
	private UUID userId;

	@Column(name = "ticket_id", nullable = false)
	private UUID ticketId;

	@Column(name = "opened_at", nullable = false)
	private Instant openedAt;

	protected KycFollowUp() {
	}

	public KycFollowUp(UUID userId, UUID ticketId, Instant openedAt) {
		this.userId = userId;
		this.ticketId = ticketId;
		this.openedAt = openedAt;
	}

	public UUID getUserId() {
		return userId;
	}

	public UUID getTicketId() {
		return ticketId;
	}

	public Instant getOpenedAt() {
		return openedAt;
	}

}
