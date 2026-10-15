package com.sealease.backend.helpdesk.entity;

/** What happened to a ticket. Customers see all but priority changes, unassignment and hand-overs between teams. */
public enum TicketEventType {
	OPENED,
	STATUS_CHANGED,
	/** A resolved or closed ticket was opened again by its customer. */
	REOPENED,
	ASSIGNED,
	UNASSIGNED,
	PRIORITY_CHANGED,
	/** Handed from support to finance, or back. */
	TEAM_CHANGED;

	public boolean visibleToCustomer() {
		return this != PRIORITY_CHANGED && this != UNASSIGNED && this != TEAM_CHANGED;
	}
}
