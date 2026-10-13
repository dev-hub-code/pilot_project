package com.sealease.backend.helpdesk.dto;

import com.sealease.backend.helpdesk.entity.TicketEventType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A ticket with its conversation and history, oldest first. Investors never receive internal notes, priority
 * changes or unassignments.
 */
public record TicketDetail(TicketResponse ticket, List<Message> messages, List<Event> events) {

	/** @param authorName for investors, staff appear by first name only */
	public record Message(UUID id, boolean fromStaff, boolean internal, String authorName, String body,
			Instant createdAt, List<Attachment> attachments) {
	}

	/**
	 * @param fromValue  previous status or priority, when the event changed one
	 * @param toValue    new status or priority
	 * @param actorName  who did it ("You", or staff by first name for investors); null for the system
	 * @param agentName  for assignments, the agent now handling the ticket
	 */
	public record Event(UUID id, TicketEventType type, String fromValue, String toValue, boolean byStaff,
			String actorName, String agentName, Instant createdAt) {
	}

	public record Attachment(UUID id, String filename, String contentType, long sizeBytes) {
	}

}
