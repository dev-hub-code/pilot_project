package com.sealease.backend.helpdesk.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A ticket with its conversation, oldest first. Investors never receive internal notes. */
public record TicketDetail(TicketResponse ticket, List<Message> messages) {

	/** @param authorName for investors, staff appear by first name only */
	public record Message(UUID id, boolean fromStaff, boolean internal, String authorName, String body,
			Instant createdAt, List<Attachment> attachments) {
	}

	public record Attachment(UUID id, String filename, String contentType, long sizeBytes) {
	}

}
