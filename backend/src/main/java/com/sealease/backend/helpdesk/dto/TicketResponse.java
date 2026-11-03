package com.sealease.backend.helpdesk.dto;

import com.sealease.backend.helpdesk.entity.RelatedType;
import com.sealease.backend.helpdesk.entity.SupportTeam;
import com.sealease.backend.helpdesk.entity.SupportTicket;
import com.sealease.backend.helpdesk.entity.TicketCategory;
import com.sealease.backend.helpdesk.entity.TicketChannel;
import com.sealease.backend.helpdesk.entity.TicketPriority;
import com.sealease.backend.helpdesk.entity.TicketStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * A ticket in a list or at the top of its page. Customers never receive staff-only fields (assignee, team, SLA
 * targets, escalation, channel): those are null for them.
 *
 * @param slaState for staff: the ticket's worst SLA state (BREACHED, AT_RISK, PAUSED, ON_TRACK, MET, NONE)
 */
public record TicketResponse(
		UUID id,
		String reference,
		String subject,
		TicketCategory category,
		UUID subcategoryId,
		String subcategoryName,
		RelatedType relatedType,
		UUID relatedId,
		String relatedLabel,
		TicketStatus status,
		TicketPriority priority,
		UUID requesterId,
		String requesterName,
		UUID assigneeId,
		String assigneeName,
		SupportTeam team,
		TicketChannel channel,
		Instant firstResponseDueAt,
		Instant firstRespondedAt,
		Instant nextResponseDueAt,
		Instant resolutionDueAt,
		String slaState,
		Integer escalationLevel,
		Integer reopenCount,
		boolean overdue,
		Instant lastMessageAt,
		Instant createdAt,
		Instant resolvedAt,
		Instant closedAt,
		Instant reopenableUntil,
		boolean feedbackRequested) {

	public static TicketResponse forStaff(SupportTicket t, String requesterName, String assigneeName, String subcategoryName,
			String slaState, Instant now, Instant reopenableUntil) {
		return new TicketResponse(t.getId(), t.getReference(), t.getSubject(), t.getCategory(), t.getSubcategoryId(),
				subcategoryName, t.getRelatedType(), t.getRelatedId(), t.getRelatedLabel(), t.getStatus(), t.getPriority(),
				t.getUserId(), requesterName, t.getAssigneeId(), assigneeName, t.getTeam(), t.getChannel(),
				t.getFirstResponseDueAt(), t.getFirstRespondedAt(), t.getNextResponseDueAt(), t.getResolutionDueAt(), slaState,
				t.getEscalationLevel(), t.getReopenCount(), t.isOverdueAt(now), t.getLastMessageAt(), t.getCreatedAt(),
				t.getResolvedAt(), t.getClosedAt(), reopenableUntil, t.getFeedbackRequestedAt() != null);
	}

	public static TicketResponse forCustomer(SupportTicket t, String subcategoryName, Instant reopenableUntil) {
		return new TicketResponse(t.getId(), t.getReference(), t.getSubject(), t.getCategory(), t.getSubcategoryId(),
				subcategoryName, t.getRelatedType(), t.getRelatedId(), t.getRelatedLabel(), t.getStatus(), t.getPriority(),
				null, null, null, null, null, null, null, t.getFirstRespondedAt(), null, null, null, null, null, false,
				t.getLastMessageAt(), t.getCreatedAt(), t.getResolvedAt(), t.getClosedAt(), reopenableUntil,
				t.getFeedbackRequestedAt() != null);
	}

}
