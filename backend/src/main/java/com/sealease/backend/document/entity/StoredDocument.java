package com.sealease.backend.document.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/** An encrypted file. Rows are never updated. */
@Entity
@Immutable
@Table(name = "stored_documents")
public class StoredDocument {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.TIME)
	private UUID id;

	@Column(name = "owner_user_id", nullable = false)
	private UUID ownerUserId;

	@Enumerated(EnumType.STRING)
	@Column(name = "purpose", nullable = false, length = 40)
	private DocumentPurpose purpose;

	@Column(name = "content_type", nullable = false, length = 100)
	private String contentType;

	@Column(name = "size_bytes", nullable = false)
	private int sizeBytes;

	@Column(name = "sha256", nullable = false, length = 64)
	private String sha256;

	/** Encrypted bytes. Documents are only ever loaded individually, never in lists. */
	@Column(name = "content", nullable = false)
	private byte[] content;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected StoredDocument() {
	}

	public StoredDocument(UUID ownerUserId, DocumentPurpose purpose, String contentType, int sizeBytes, String sha256,
			byte[] content, Instant createdAt) {
		this.ownerUserId = ownerUserId;
		this.purpose = purpose;
		this.contentType = contentType;
		this.sizeBytes = sizeBytes;
		this.sha256 = sha256;
		this.content = content;
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public UUID getOwnerUserId() {
		return ownerUserId;
	}

	public DocumentPurpose getPurpose() {
		return purpose;
	}

	public String getContentType() {
		return contentType;
	}

	public int getSizeBytes() {
		return sizeBytes;
	}

	public String getSha256() {
		return sha256;
	}

	public byte[] getContent() {
		return content;
	}

}
