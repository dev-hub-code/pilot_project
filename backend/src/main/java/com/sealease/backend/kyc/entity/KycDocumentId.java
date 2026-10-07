package com.sealease.backend.kyc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
public record KycDocumentId(
		@Column(name = "submission_id", nullable = false) UUID submissionId,
		@Column(name = "document_id", nullable = false) UUID documentId) implements Serializable {
}
