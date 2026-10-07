package com.sealease.backend.helpdesk.entity;

import com.sealease.backend.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** An investor's support request and its handling. */
@Entity
@Table(name = "support_tickets")
public class SupportTicket extends BaseEntity {

	@Column(name = "reference", nullable = false, updatable = false, length = 20)
	private String reference;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "subject", nullable = false, updatable = false, length = 200)
	private String subject;

	@Enumerated(EnumType.STRING)
	@Column(name = "category", nullable = false, updatable = false, length = 20)
	private TicketCategory category;

	@Enumerated(EnumType.STRING)
	@Column(name = "related_type", updatable = false, length = 20)
	private RelatedType relatedType;

	@Column(name = "related_id", updatable = false)
	private UUID relatedId;

	@Column(name = "related_label", updatable = false, length = 60)
	private String relatedLabel;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 25)
	private TicketStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "priority", nullable = false, length = 10)
	private TicketPriority priority;

	@Column(name = "assignee_id")
	private UUID assigneeId;

	@Column(name = "first_response_due_at", nullable = false)
	private Instant firstResponseDueAt;

	@Column(name = "first_responded_at")
	private Instant firstRespondedAt;

	@Column(name = "last_message_at", nullable = false)
	private Instant lastMessageAt;

	@Column(name = "resolved_at")
	private Instant resolvedAt;

	@Column(name = "closed_at")
	private Instant closedAt;

	protected SupportTicket() {
	}

	public SupportTicket(String reference, UUID userId, String subject, TicketCategory category, RelatedType relatedType,
			UUID relatedId, String relatedLabel, TicketPriority priority, Instant firstResponseDueAt, Instant now) {
		this.reference = reference;
		this.userId = userId;
		this.subject = subject;
		this.category = category;
		this.relatedType = relatedType;
		this.relatedId = relatedId;
		this.relatedLabel = relatedLabel;
		this.priority = priority;
		this.firstResponseDueAt = firstResponseDueAt;
		this.lastMessageAt = now;
		this.status = TicketStatus.OPEN;
	}

	/** A public staff reply: the customer's turn. */
	public void staffReplied(Instant now) {
		if (firstRespondedAt == null) {
			firstRespondedAt = now;
		}
		lastMessageAt = now;
		status = TicketStatus.WAITING_ON_CUSTOMER;
	}

	/** The customer wrote: back in the queue, reopening a resolved ticket. */
	public void customerReplied(Instant now) {
		lastMessageAt = now;
		status = TicketStatus.OPEN;
		resolvedAt = null;
	}

	public void resolve(Instant now) {
		status = TicketStatus.RESOLVED;
		resolvedAt = now;
	}

	public void close(Instant now) {
		status = TicketStatus.CLOSED;
		closedAt = now;
	}

	public void reprioritise(TicketPriority priority, Instant firstResponseDueAt) {
		this.priority = priority;
		this.firstResponseDueAt = firstResponseDueAt;
	}

	public void assignTo(UUID assigneeId) {
		this.assigneeId = assigneeId;
	}

	public boolean isOverdueAt(Instant now) {
		return firstRespondedAt == null && status != TicketStatus.CLOSED && now.isAfter(firstResponseDueAt);
	}

	public String getReference() {
		return reference;
	}

	public UUID getUserId() {
		return userId;
	}

	public String getSubject() {
		return subject;
	}

	public TicketCategory getCategory() {
		return category;
	}

	public RelatedType getRelatedType() {
		return relatedType;
	}

	public UUID getRelatedId() {
		return relatedId;
	}

	public String getRelatedLabel() {
		return relatedLabel;
	}

	public TicketStatus getStatus() {
		return status;
	}

	public TicketPriority getPriority() {
		return priority;
	}

	public UUID getAssigneeId() {
		return assigneeId;
	}

	public Instant getFirstResponseDueAt() {
		return firstResponseDueAt;
	}

	public Instant getFirstRespondedAt() {
		return firstRespondedAt;
	}

	public Instant getLastMessageAt() {
		return lastMessageAt;
	}

	public Instant getResolvedAt() {
		return resolvedAt;
	}

	public Instant getClosedAt() {
		return closedAt;
	}

}
