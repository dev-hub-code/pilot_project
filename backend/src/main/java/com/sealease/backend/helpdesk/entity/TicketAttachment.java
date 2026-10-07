package com.sealease.backend.helpdesk.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/** A file on a ticket message; the content lives (encrypted) in the document store. Append-only. */
@Entity
@Immutable
@Table(name = "ticket_attachments")
public class TicketAttachment {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.TIME)
	private UUID id;

	@Column(name = "ticket_id", nullable = false)
	private UUID ticketId;

	@Column(name = "message_id", nullable = false)
	private UUID messageId;

	@Column(name = "document_id", nullable = false)
	private UUID documentId;

	@Column(name = "filename", nullable = false, length = 200)
	private String filename;

	@Column(name = "content_type", nullable = false, length = 100)
	private String contentType;

	@Column(name = "size_bytes", nullable = false)
	private long sizeBytes;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected TicketAttachment() {
	}

	public TicketAttachment(UUID ticketId, UUID messageId, UUID documentId, String filename, String contentType,
			long sizeBytes, Instant createdAt) {
		this.ticketId = ticketId;
		this.messageId = messageId;
		this.documentId = documentId;
		this.filename = filename;
		this.contentType = contentType;
		this.sizeBytes = sizeBytes;
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public UUID getTicketId() {
		return ticketId;
	}

	public UUID getMessageId() {
		return messageId;
	}

	public UUID getDocumentId() {
		return documentId;
	}

	public String getFilename() {
		return filename;
	}

	public String getContentType() {
		return contentType;
	}

	public long getSizeBytes() {
		return sizeBytes;
	}

}
