package com.sealease.backend.investment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/** A photo shown for a plan in the marketplace. The image itself is in the document store. */
@Entity
@Table(name = "plan_photos")
public class PlanPhoto {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.TIME)
	private UUID id;

	@Column(name = "product_id", nullable = false, updatable = false)
	private UUID productId;

	@Column(name = "document_id", nullable = false, updatable = false)
	private UUID documentId;

	@Column(name = "uploaded_by", nullable = false, updatable = false)
	private UUID uploadedBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected PlanPhoto() {
	}

	public PlanPhoto(UUID productId, UUID documentId, UUID uploadedBy, Instant createdAt) {
		this.productId = productId;
		this.documentId = documentId;
		this.uploadedBy = uploadedBy;
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public UUID getProductId() {
		return productId;
	}

	public UUID getDocumentId() {
		return documentId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
