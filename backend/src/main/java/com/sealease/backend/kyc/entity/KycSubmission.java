package com.sealease.backend.kyc.entity;

import com.sealease.backend.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One identity-verification attempt. A rejected submission is kept; the user submits a new one,
 * so the full verification history stays reconstructable.
 */
@Entity
@Table(name = "kyc_submissions")
public class KycSubmission extends BaseEntity {

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private KycSubmissionStatus status;

	@Column(name = "legal_first_name", nullable = false, length = 100, updatable = false)
	private String legalFirstName;

	@Column(name = "legal_last_name", nullable = false, length = 100, updatable = false)
	private String legalLastName;

	@Column(name = "date_of_birth", nullable = false, updatable = false)
	private LocalDate dateOfBirth;

	@Column(name = "nationality", nullable = false, length = 2, updatable = false)
	private String nationality;

	@Enumerated(EnumType.STRING)
	@Column(name = "document_type", nullable = false, length = 20, updatable = false)
	private IdentityDocumentType documentType;

	@Column(name = "document_number_encrypted", nullable = false, updatable = false)
	private String documentNumberEncrypted;

	@Column(name = "document_number_last4", nullable = false, length = 4, updatable = false)
	private String documentNumberLast4;

	@Column(name = "document_issuing_country", nullable = false, length = 2, updatable = false)
	private String documentIssuingCountry;

	@Column(name = "document_expiry_date", nullable = false, updatable = false)
	private LocalDate documentExpiryDate;

	@Column(name = "submitted_at", nullable = false, updatable = false)
	private Instant submittedAt;

	@Column(name = "reviewed_by")
	private UUID reviewedBy;

	@Column(name = "reviewed_at")
	private Instant reviewedAt;

	@Column(name = "rejection_reason", length = 500)
	private String rejectionReason;

	protected KycSubmission() {
	}

	public KycSubmission(UUID userId, String legalFirstName, String legalLastName, LocalDate dateOfBirth,
			String nationality, IdentityDocumentType documentType, String documentNumberEncrypted,
			String documentNumberLast4, String documentIssuingCountry, LocalDate documentExpiryDate,
			Instant submittedAt) {
		this.userId = userId;
		this.status = KycSubmissionStatus.PENDING;
		this.legalFirstName = legalFirstName;
		this.legalLastName = legalLastName;
		this.dateOfBirth = dateOfBirth;
		this.nationality = nationality;
		this.documentType = documentType;
		this.documentNumberEncrypted = documentNumberEncrypted;
		this.documentNumberLast4 = documentNumberLast4;
		this.documentIssuingCountry = documentIssuingCountry;
		this.documentExpiryDate = documentExpiryDate;
		this.submittedAt = submittedAt;
	}

	public void approve(UUID reviewer, Instant now) {
		this.status = KycSubmissionStatus.APPROVED;
		this.reviewedBy = reviewer;
		this.reviewedAt = now;
	}

	public void reject(UUID reviewer, String reason, Instant now) {
		this.status = KycSubmissionStatus.REJECTED;
		this.reviewedBy = reviewer;
		this.reviewedAt = now;
		this.rejectionReason = reason;
	}

	public UUID getUserId() {
		return userId;
	}

	public KycSubmissionStatus getStatus() {
		return status;
	}

	public String getLegalFirstName() {
		return legalFirstName;
	}

	public String getLegalLastName() {
		return legalLastName;
	}

	public LocalDate getDateOfBirth() {
		return dateOfBirth;
	}

	public String getNationality() {
		return nationality;
	}

	public IdentityDocumentType getDocumentType() {
		return documentType;
	}

	public String getDocumentNumberLast4() {
		return documentNumberLast4;
	}

	public String getDocumentIssuingCountry() {
		return documentIssuingCountry;
	}

	public LocalDate getDocumentExpiryDate() {
		return documentExpiryDate;
	}

	public Instant getSubmittedAt() {
		return submittedAt;
	}

	public UUID getReviewedBy() {
		return reviewedBy;
	}

	public Instant getReviewedAt() {
		return reviewedAt;
	}

	public String getRejectionReason() {
		return rejectionReason;
	}

}
