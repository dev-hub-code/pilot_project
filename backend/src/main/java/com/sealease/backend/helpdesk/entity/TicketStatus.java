package com.sealease.backend.helpdesk.entity;

/** OPEN ⇄ WAITING_ON_CUSTOMER → RESOLVED → CLOSED; replying to a RESOLVED ticket reopens it, CLOSED is final. */
public enum TicketStatus {
	OPEN,
	WAITING_ON_CUSTOMER,
	RESOLVED,
	CLOSED
}
