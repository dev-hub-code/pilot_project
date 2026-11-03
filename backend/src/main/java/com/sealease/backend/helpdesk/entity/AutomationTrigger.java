package com.sealease.backend.helpdesk.entity;

/** When an automation rule is considered. */
public enum AutomationTrigger {
	TICKET_CREATED,
	/** Still unassigned {@code minutes} after creation (checked by the SLA monitor). */
	UNASSIGNED_FOR,
	/** {@code percent} of an SLA target used (checked by the SLA monitor). */
	SLA_PERCENT,
	SLA_BREACHED,
	CUSTOMER_REPLIED_RESOLVED,
	TICKET_RESOLVED,
	TICKET_CLOSED,
	TICKET_REOPENED
}
