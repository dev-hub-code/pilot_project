package com.sealease.backend.helpdesk.dto;

import com.sealease.backend.helpdesk.entity.SupportTeam;
import com.sealease.backend.helpdesk.entity.TicketCategory;
import com.sealease.backend.helpdesk.entity.TicketChannel;
import com.sealease.backend.helpdesk.entity.TicketPriority;
import com.sealease.backend.helpdesk.entity.TicketStatus;

import java.time.LocalDate;
import java.util.UUID;

/**
 * @param q          reference, subject, related record, or the customer's name, email or code
 * @param status     a status, or {@code null}; see {@code view} for groups
 * @param assignee   {@code me}, {@code unassigned}, a user id, or {@code null} for all
 * @param overdue    only tickets past their first-response target without a staff reply
 * @param team       only tickets with this team, or {@code null} for all
 * @param view       a queue: {@code open} (not resolved or closed), {@code breached} (open, past an SLA target),
 *                   {@code at-risk} (open, due within the at-risk window), {@code escalated}, {@code waiting}, or
 *                   {@code null}
 * @param customerId only this customer's tickets
 * @param from       created on or after (display time zone)
 * @param to         created on or before (display time zone)
 */
public record TicketSearchCriteria(String q, TicketStatus status, TicketPriority priority, String assignee,
		boolean overdue, SupportTeam team, TicketCategory category, UUID subcategoryId, TicketChannel channel, String view,
		UUID customerId, LocalDate from, LocalDate to) {

	public TicketSearchCriteria(String q, TicketStatus status, TicketPriority priority, String assignee, boolean overdue,
			SupportTeam team) {
		this(q, status, priority, assignee, overdue, team, null, null, null, null, null, null, null);
	}

	public TicketSearchCriteria withTeam(SupportTeam team) {
		return new TicketSearchCriteria(q, status, priority, assignee, overdue, team, category, subcategoryId, channel, view,
				customerId, from, to);
	}

}
