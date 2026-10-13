package com.sealease.backend.helpdesk.entity;

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

/**
 * One entry in a ticket's history. {@code fromValue}/{@code toValue} hold statuses or priorities;
 * {@code subjectUserId} is the agent assigned. Append-only.
 */
@Entity
@Immutable
@Table(name = "ticket_events")
public class TicketEvent {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.TIME)
	private UUID id;

	@Column(name = "ticket_id", nullable = false)
	private UUID ticketId;

	@Column(name = "actor_id")
	private UUID actorId;

	@Column(name = "actor_is_staff", nullable = false)
	private boolean actorIsStaff;

	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false, length = 20)
	private TicketEventType type;

	@Column(name = "from_value", length = 25)
	private String fromValue;

	@Column(name = "to_value", length = 25)
	private String toValue;

	@Column(name = "subject_user_id")
	private UUID subjectUserId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected TicketEvent() {
	}

	public TicketEvent(UUID ticketId, UUID actorId, boolean actorIsStaff, TicketEventType type, String fromValue,
			String toValue, UUID subjectUserId, Instant createdAt) {
		this.ticketId = ticketId;
		this.actorId = actorId;
		this.actorIsStaff = actorIsStaff;
		this.type = type;
		this.fromValue = fromValue;
		this.toValue = toValue;
		this.subjectUserId = subjectUserId;
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public UUID getTicketId() {
		return ticketId;
	}

	public UUID getActorId() {
		return actorId;
	}

	public boolean isActorIsStaff() {
		return actorIsStaff;
	}

	public TicketEventType getType() {
		return type;
	}

	public String getFromValue() {
		return fromValue;
	}

	public String getToValue() {
		return toValue;
	}

	public UUID getSubjectUserId() {
		return subjectUserId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
