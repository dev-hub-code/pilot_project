package com.sealease.backend.helpdesk.dto;

import com.sealease.backend.helpdesk.entity.RelatedType;
import com.sealease.backend.helpdesk.entity.SupportTicket;
import com.sealease.backend.helpdesk.entity.TicketCategory;
import com.sealease.backend.helpdesk.entity.TicketPriority;
import com.sealease.backend.helpdesk.entity.TicketStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * A ticket. Staff views name the requester and assignee; investor views leave those {@code null}.
 *
 * @param overdue no staff reply yet and the first-response target has passed
 */
public record TicketResponse(
		UUID id,
		String reference,
		String subject,
		TicketCategory category,
		RelatedType relatedType,
		UUID relatedId,
		String relatedLabel,
		TicketStatus status,
		TicketPriority priority,
		UUID requesterId,
		String requesterName,
		UUID assigneeId,
		String assigneeName,
		Instant firstResponseDueAt,
		Instant firstRespondedAt,
		boolean overdue,
		Instant lastMessageAt,
		Instant createdAt,
		Instant resolvedAt,
		Instant closedAt) {

	public static TicketResponse forStaff(SupportTicket t, String requesterName, String assigneeName, Instant now) {
		return of(t, requesterName, assigneeName, now, true);
	}

	public static TicketResponse forCustomer(SupportTicket t, Instant now) {
		return of(t, null, null, now, false);
	}

	private static TicketResponse of(SupportTicket t, String requesterName, String assigneeName, Instant now, boolean staff) {
		return new TicketResponse(t.getId(), t.getReference(), t.getSubject(), t.getCategory(), t.getRelatedType(),
				t.getRelatedId(), t.getRelatedLabel(), t.getStatus(), t.getPriority(), staff ? t.getUserId() : null,
				requesterName, staff ? t.getAssigneeId() : null, assigneeName, staff ? t.getFirstResponseDueAt() : null,
				t.getFirstRespondedAt(), staff && t.isOverdueAt(now), t.getLastMessageAt(), t.getCreatedAt(),
				t.getResolvedAt(), t.getClosedAt());
	}

}
