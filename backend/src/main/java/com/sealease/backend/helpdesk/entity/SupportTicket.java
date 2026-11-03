package com.sealease.backend.helpdesk.entity;

import com.sealease.backend.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A customer's support request and its handling: lifecycle, assignment, SLA targets (first response, next response,
 * resolution) and escalation. Messages, events and attachments live in their own immutable tables.
 */
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
	@Column(name = "category", nullable = false, length = 20)
	private TicketCategory category;

	@Column(name = "subcategory_id")
	private UUID subcategoryId;

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

	@Enumerated(EnumType.STRING)
	@Column(name = "team", nullable = false, length = 20)
	private SupportTeam team = SupportTeam.SUPPORT;

	@Enumerated(EnumType.STRING)
	@Column(name = "channel", nullable = false, updatable = false, length = 20)
	private TicketChannel channel = TicketChannel.PORTAL;

	/** The staff member who opened it on the customer's behalf, if any. */
	@Column(name = "created_by", updatable = false)
	private UUID createdBy;

	@Column(name = "sla_policy_id")
	private UUID slaPolicyId;

	@Column(name = "first_response_due_at", nullable = false)
	private Instant firstResponseDueAt;

	@Column(name = "first_responded_at")
	private Instant firstRespondedAt;

	/** When the customer wrote again after a staff reply: staff owe a next response by then. */
	@Column(name = "next_response_due_at")
	private Instant nextResponseDueAt;

	@Column(name = "resolution_due_at")
	private Instant resolutionDueAt;

	/** The resolution clock stopped here while waiting on the customer. */
	@Column(name = "sla_paused_at")
	private Instant slaPausedAt;

	/** Business minutes the resolution clock has been stopped in total. */
	@Column(name = "sla_paused_minutes", nullable = false)
	private int slaPausedMinutes;

	@Column(name = "first_response_breached_at")
	private Instant firstResponseBreachedAt;

	@Column(name = "next_response_breached_at")
	private Instant nextResponseBreachedAt;

	@Column(name = "resolution_breached_at")
	private Instant resolutionBreachedAt;

	/** First staff action (assignment, reply or status change). */
	@Column(name = "acknowledged_at")
	private Instant acknowledgedAt;

	@Column(name = "escalation_level", nullable = false)
	private short escalationLevel;

	@Column(name = "reopen_count", nullable = false)
	private int reopenCount;

	/** Customer messages while waiting for staff (chasing). */
	@Column(name = "customer_follow_ups", nullable = false)
	private int customerFollowUps;

	@Column(name = "first_response_business_minutes")
	private Integer firstResponseBusinessMinutes;

	@Column(name = "resolution_business_minutes")
	private Integer resolutionBusinessMinutes;

	@Column(name = "feedback_requested_at")
	private Instant feedbackRequestedAt;

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
		this.status = TicketStatus.NEW;
	}

	/**
	 * Where it came from; set once, when the ticket is created. Customers' tickets start NEW (untriaged); tickets staff
	 * or the system open already carry a staff message, so they start OPEN.
	 */
	public void origin(TicketChannel channel, UUID createdBy, UUID subcategoryId, SupportTeam team) {
		this.channel = channel;
		this.createdBy = createdBy;
		if (channel == TicketChannel.STAFF || channel == TicketChannel.SYSTEM) {
			this.status = TicketStatus.OPEN;
		}
		this.subcategoryId = subcategoryId;
		if (team != null) {
			this.team = team;
		}
	}

	/** The SLA targets for this ticket (set on creation and when the priority or category changes). */
	public void applySla(UUID policyId, Instant firstResponseDueAt, Instant resolutionDueAt) {
		this.slaPolicyId = policyId;
		this.firstResponseDueAt = firstResponseDueAt;
		this.resolutionDueAt = resolutionDueAt;
	}

	/** A public staff reply: the customer's turn. */
	public void staffReplied(Instant now) {
		if (firstRespondedAt == null) {
			firstRespondedAt = now;
		}
		acknowledge(now);
		nextResponseDueAt = null;
		customerFollowUps = 0;
		lastMessageAt = now;
		status = TicketStatus.WAITING_ON_CUSTOMER;
	}

	/**
	 * The customer wrote. A resolved ticket reopens (back to OPEN, counted as a reopen); a ticket waiting on them goes
	 * back in the queue; otherwise the status stays and the message counts as a follow-up.
	 *
	 * @param nextResponseDueAt when staff owe the next reply (null: no next-response target)
	 */
	public void customerReplied(Instant now, Instant nextResponseDueAt) {
		lastMessageAt = now;
		if (status == TicketStatus.RESOLVED) {
			status = TicketStatus.OPEN;
			resolvedAt = null;
			reopenCount++;
		}
		else if (status == TicketStatus.WAITING_ON_CUSTOMER) {
			status = TicketStatus.OPEN;
		}
		else {
			customerFollowUps++;
		}
		if (firstRespondedAt != null && this.nextResponseDueAt == null) {
			this.nextResponseDueAt = nextResponseDueAt;
		}
	}

	/** The customer reopened a resolved ticket: back in the queue (OPEN; the reopen is counted and kept as an event). */
	public void reopen(Instant now, Instant nextResponseDueAt) {
		lastMessageAt = now;
		status = TicketStatus.OPEN;
		resolvedAt = null;
		closedAt = null;
		reopenCount++;
		if (firstRespondedAt != null) {
			this.nextResponseDueAt = nextResponseDueAt;
		}
	}

	/** Moves to a status staff chose (already checked against the allowed transitions). */
	public void moveTo(TicketStatus target, Instant now) {
		acknowledge(now);
		switch (target) {
			case RESOLVED -> {
				resolvedAt = now;
				nextResponseDueAt = null;
			}
			case CLOSED -> {
				closedAt = now;
				nextResponseDueAt = null;
				if (resolvedAt == null) {
					resolvedAt = now;
				}
			}
			default -> {
			}
		}
		status = target;
	}

	public void resolve(Instant now) {
		moveTo(TicketStatus.RESOLVED, now);
	}

	public void close(Instant now) {
		closedAt = now;
		nextResponseDueAt = null;
		status = TicketStatus.CLOSED;
	}

	public void reprioritise(TicketPriority priority, Instant firstResponseDueAt) {
		this.priority = priority;
		this.firstResponseDueAt = firstResponseDueAt;
	}

	public void recategorise(TicketCategory category, UUID subcategoryId) {
		this.category = category;
		this.subcategoryId = subcategoryId;
	}

	public void assignTo(UUID assigneeId) {
		this.assigneeId = assigneeId;
	}

	/** Hands the ticket to another team, unassigned, for one of its members to take. */
	public void handTo(SupportTeam team) {
		this.team = team;
		this.assigneeId = null;
	}

	public void acknowledge(Instant now) {
		if (acknowledgedAt == null) {
			acknowledgedAt = now;
		}
	}

	public void escalateTo(int level) {
		this.escalationLevel = (short) level;
		this.status = TicketStatus.ESCALATED;
	}

	public void pauseSla(Instant now) {
		if (slaPausedAt == null) {
			slaPausedAt = now;
		}
	}

	/** Restarts the resolution clock: the paused minutes are added up and the target moves accordingly. */
	public void resumeSla(int pausedMinutes, Instant newResolutionDueAt) {
		slaPausedAt = null;
		slaPausedMinutes += pausedMinutes;
		resolutionDueAt = newResolutionDueAt;
	}

	public void firstResponseBreached(Instant at) {
		firstResponseBreachedAt = at;
	}

	public void nextResponseBreached(Instant at) {
		nextResponseBreachedAt = at;
	}

	public void resolutionBreached(Instant at) {
		resolutionBreachedAt = at;
	}

	public void recordMetrics(Integer firstResponseBusinessMinutes, Integer resolutionBusinessMinutes) {
		this.firstResponseBusinessMinutes = firstResponseBusinessMinutes;
		this.resolutionBusinessMinutes = resolutionBusinessMinutes;
	}

	public void feedbackRequested(Instant at) {
		feedbackRequestedAt = at;
	}

	public boolean isOverdueAt(Instant now) {
		return firstRespondedAt == null && status.isOpen() && now.isAfter(firstResponseDueAt);
	}

	public SupportTeam getTeam() {
		return team;
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

	public UUID getSubcategoryId() {
		return subcategoryId;
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

	public TicketChannel getChannel() {
		return channel;
	}

	public UUID getCreatedBy() {
		return createdBy;
	}

	public UUID getSlaPolicyId() {
		return slaPolicyId;
	}

	public Instant getFirstResponseDueAt() {
		return firstResponseDueAt;
	}

	public Instant getFirstRespondedAt() {
		return firstRespondedAt;
	}

	public Instant getNextResponseDueAt() {
		return nextResponseDueAt;
	}

	public Instant getResolutionDueAt() {
		return resolutionDueAt;
	}

	public Instant getSlaPausedAt() {
		return slaPausedAt;
	}

	public int getSlaPausedMinutes() {
		return slaPausedMinutes;
	}

	public Instant getFirstResponseBreachedAt() {
		return firstResponseBreachedAt;
	}

	public Instant getNextResponseBreachedAt() {
		return nextResponseBreachedAt;
	}

	public Instant getResolutionBreachedAt() {
		return resolutionBreachedAt;
	}

	public Instant getAcknowledgedAt() {
		return acknowledgedAt;
	}

	public int getEscalationLevel() {
		return escalationLevel;
	}

	public int getReopenCount() {
		return reopenCount;
	}

	public int getCustomerFollowUps() {
		return customerFollowUps;
	}

	public Integer getFirstResponseBusinessMinutes() {
		return firstResponseBusinessMinutes;
	}

	public Integer getResolutionBusinessMinutes() {
		return resolutionBusinessMinutes;
	}

	public Instant getFeedbackRequestedAt() {
		return feedbackRequestedAt;
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
