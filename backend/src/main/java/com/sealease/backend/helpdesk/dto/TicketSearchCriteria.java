package com.sealease.backend.helpdesk.dto;

import com.sealease.backend.helpdesk.entity.SupportTeam;
import com.sealease.backend.helpdesk.entity.TicketPriority;
import com.sealease.backend.helpdesk.entity.TicketStatus;

/**
 * @param assignee {@code me}, {@code unassigned}, a user id, or {@code null} for all
 * @param overdue  only tickets past their first-response target without a staff reply
 * @param team     only tickets with this team, or {@code null} for all
 */
public record TicketSearchCriteria(String q, TicketStatus status, TicketPriority priority, String assignee,
		boolean overdue, SupportTeam team) {
}
