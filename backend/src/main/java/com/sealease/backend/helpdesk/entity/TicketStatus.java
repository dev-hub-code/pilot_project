package com.sealease.backend.helpdesk.entity;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * A ticket's lifecycle. NEW until staff first act on it; OPEN when it waits in the queue again (e.g. after the customer
 * writes); IN_PROGRESS, WAITING_ON_CUSTOMER and WAITING_ON_INTERNAL while it is being handled; ESCALATED once raised;
 * RESOLVED, then CLOSED (by staff, the customer, or by itself after the reopen window). REOPENED: a resolved ticket
 * the customer came back to. Values are stored by name (WAITING_ON_CUSTOMER predates the others).
 */
public enum TicketStatus {
	NEW,
	OPEN,
	IN_PROGRESS,
	WAITING_ON_CUSTOMER,
	WAITING_ON_INTERNAL,
	ESCALATED,
	RESOLVED,
	CLOSED,
	REOPENED;

	/** Statuses staff may set directly; others follow from replies, escalation and reopening. */
	public static final Set<TicketStatus> STAFF_SETTABLE = EnumSet.of(IN_PROGRESS, WAITING_ON_CUSTOMER, WAITING_ON_INTERNAL,
			RESOLVED, CLOSED);

	private static final Set<TicketStatus> ACTIVE = EnumSet.of(NEW, OPEN, IN_PROGRESS, WAITING_ON_INTERNAL, ESCALATED, REOPENED);

	private static final Map<TicketStatus, Set<TicketStatus>> STAFF_TRANSITIONS = Map.of(
			NEW, EnumSet.of(IN_PROGRESS, WAITING_ON_CUSTOMER, WAITING_ON_INTERNAL, RESOLVED, CLOSED),
			OPEN, EnumSet.of(IN_PROGRESS, WAITING_ON_CUSTOMER, WAITING_ON_INTERNAL, RESOLVED, CLOSED),
			IN_PROGRESS, EnumSet.of(WAITING_ON_CUSTOMER, WAITING_ON_INTERNAL, RESOLVED, CLOSED),
			WAITING_ON_CUSTOMER, EnumSet.of(IN_PROGRESS, WAITING_ON_INTERNAL, RESOLVED, CLOSED),
			WAITING_ON_INTERNAL, EnumSet.of(IN_PROGRESS, WAITING_ON_CUSTOMER, RESOLVED, CLOSED),
			ESCALATED, EnumSet.of(IN_PROGRESS, WAITING_ON_CUSTOMER, WAITING_ON_INTERNAL, RESOLVED, CLOSED),
			REOPENED, EnumSet.of(IN_PROGRESS, WAITING_ON_CUSTOMER, WAITING_ON_INTERNAL, RESOLVED, CLOSED),
			RESOLVED, EnumSet.of(CLOSED),
			CLOSED, EnumSet.noneOf(TicketStatus.class));

	/** Whether staff may move a ticket from this status to {@code target}. */
	public boolean staffMayMoveTo(TicketStatus target) {
		return STAFF_TRANSITIONS.get(this).contains(target);
	}

	/** Waiting for, or being worked on by, the support team: counts towards the backlog and SLA. */
	public boolean isActive() {
		return ACTIVE.contains(this);
	}

	/** Not resolved or closed. */
	public boolean isOpen() {
		return this != RESOLVED && this != CLOSED;
	}

	public static Set<TicketStatus> openStatuses() {
		return EnumSet.complementOf(EnumSet.of(RESOLVED, CLOSED));
	}
}
