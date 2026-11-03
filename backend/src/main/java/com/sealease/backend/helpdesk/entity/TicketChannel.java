package com.sealease.backend.helpdesk.entity;

/** Where a ticket came from: the investor portal, staff on a customer's behalf, an application workflow, or email. */
public enum TicketChannel {
	PORTAL,
	STAFF,
	SYSTEM,
	EMAIL
}
