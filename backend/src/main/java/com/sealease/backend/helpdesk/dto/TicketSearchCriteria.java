package com.sealease.backend.helpdesk.dto;

import com.sealease.backend.helpdesk.entity.TicketPriority;
import com.sealease.backend.helpdesk.entity.TicketStatus;

/**
 * @param assignee {@code me}, {@code unassigned}, a user id, or {@code null} for all
 * @param overdue  only tickets past their first-response target without a staff reply
 */
public record TicketSearchCriteria(String q, TicketStatus status, TicketPriority priority, String assignee,
		boolean overdue) {
}
