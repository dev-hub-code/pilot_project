package com.sealease.backend.container.entity;

import com.sealease.backend.common.persistence.BaseEntity;
import com.sealease.backend.document.entity.DocumentPurpose;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

/** A photo or supporting document attached to a container; investors only see visible ones. */
@Entity
@Table(name = "container_documents")
public class ContainerDocument extends BaseEntity {

	@Column(name = "container_id", nullable = false, updatable = false)
	private UUID containerId;

	@Column(name = "document_id", nullable = false, updatable = false)
	private UUID documentId;

	@Enumerated(EnumType.STRING)
	@Column(name = "purpose", nullable = false, length = 40, updatable = false)
	private DocumentPurpose purpose;

	@Column(name = "title", nullable = false, length = 140)
	private String title;

	@Column(name = "visible_to_investors", nullable = false)
	private boolean visibleToInvestors;

	@Column(name = "uploaded_by", nullable = false, updatable = false)
	private UUID uploadedBy;

	protected ContainerDocument() {
	}

	public ContainerDocument(UUID containerId, UUID documentId, DocumentPurpose purpose, String title,
			boolean visibleToInvestors, UUID uploadedBy) {
		this.containerId = containerId;
		this.documentId = documentId;
		this.purpose = purpose;
		this.title = title;
		this.visibleToInvestors = visibleToInvestors;
		this.uploadedBy = uploadedBy;
	}

	public void setVisibleToInvestors(boolean visible) {
		this.visibleToInvestors = visible;
	}

	public UUID getContainerId() {
		return containerId;
	}

	public UUID getDocumentId() {
		return documentId;
	}

	public DocumentPurpose getPurpose() {
		return purpose;
	}

	public String getTitle() {
		return title;
	}

	public boolean isVisibleToInvestors() {
		return visibleToInvestors;
	}

	public UUID getUploadedBy() {
		return uploadedBy;
	}

}
