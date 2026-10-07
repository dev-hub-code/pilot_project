package com.sealease.backend.helpdesk.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/** One message on a ticket. Internal notes are written by staff for staff. Append-only. */
@Entity
@Immutable
@Table(name = "ticket_messages")
public class TicketMessage {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.TIME)
	private UUID id;

	@Column(name = "ticket_id", nullable = false)
	private UUID ticketId;

	@Column(name = "author_id", nullable = false)
	private UUID authorId;

	@Column(name = "from_staff", nullable = false)
	private boolean fromStaff;

	@Column(name = "internal", nullable = false)
	private boolean internal;

	@Column(name = "body", nullable = false, length = 8000)
	private String body;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected TicketMessage() {
	}

	public TicketMessage(UUID ticketId, UUID authorId, boolean fromStaff, boolean internal, String body, Instant createdAt) {
		this.ticketId = ticketId;
		this.authorId = authorId;
		this.fromStaff = fromStaff;
		this.internal = internal;
		this.body = body;
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public UUID getTicketId() {
		return ticketId;
	}

	public UUID getAuthorId() {
		return authorId;
	}

	public boolean isFromStaff() {
		return fromStaff;
	}

	public boolean isInternal() {
		return internal;
	}

	public String getBody() {
		return body;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
