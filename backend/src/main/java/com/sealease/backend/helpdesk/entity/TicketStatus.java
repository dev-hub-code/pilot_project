package com.sealease.backend.helpdesk.entity;

/**
 * OPEN ⇄ WAITING_ON_CUSTOMER → RESOLVED → CLOSED. Replying to a RESOLVED ticket reopens it; the customer can also
 * reopen a RESOLVED or CLOSED ticket, with a reason, within the reopen window.
 */
public enum TicketStatus {
	OPEN,
	WAITING_ON_CUSTOMER,
	RESOLVED,
	CLOSED
}
