package com.sealease.backend.helpdesk.entity;

/** What happened to a ticket. Customers see what concerns them; internal handling stays with staff. */
public enum TicketEventType {
	OPENED,
	STATUS_CHANGED,
	/** A resolved or closed ticket was opened again by its customer. */
	REOPENED,
	ASSIGNED,
	UNASSIGNED,
	PRIORITY_CHANGED,
	/** Handed from support to finance, or back. */
	TEAM_CHANGED,
	CATEGORY_CHANGED,
	/** Raised to a higher escalation level (to_value: the level). */
	ESCALATED,
	/** An SLA target is close (from_value: the metric, to_value: the percentage used). */
	SLA_WARNING,
	/** An SLA target was missed (from_value: the metric). */
	SLA_BREACHED,
	SLA_PAUSED,
	SLA_RESUMED,
	/** The customer rated the ticket (to_value: the rating). */
	FEEDBACK;

	public boolean visibleToCustomer() {
		return switch (this) {
			case OPENED, STATUS_CHANGED, REOPENED, ASSIGNED, FEEDBACK -> true;
			default -> false;
		};
	}
}
