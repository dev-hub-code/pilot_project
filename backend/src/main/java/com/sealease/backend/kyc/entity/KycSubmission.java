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
 * One identity-verification attempt: the Aadhaar and PAN, which may be uploaded at different times. Until both are
 * in it is a DRAFT; then it is PENDING review. A rejected submission is kept; the user starts a new one, so the full
 * verification history stays reconstructable.
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
	@Column(name = "document_type", length = 20, updatable = false)
	private IdentityDocumentType documentType;

	@Column(name = "document_number_encrypted", updatable = false, columnDefinition = "TEXT")
	private String documentNumberEncrypted;

	@Column(name = "document_number_last4", length = 4, updatable = false)
	private String documentNumberLast4;

	@Column(name = "document_issuing_country", length = 2, updatable = false)
	private String documentIssuingCountry;

	@Column(name = "document_expiry_date", updatable = false)
	private LocalDate documentExpiryDate;

	@Column(name = "aadhaar_number_encrypted", columnDefinition = "TEXT")
	private String aadhaarNumberEncrypted;

	@Column(name = "aadhaar_number_last4", length = 4)
	private String aadhaarNumberLast4;

	@Column(name = "pan_number_encrypted", columnDefinition = "TEXT")
	private String panNumberEncrypted;

	@Column(name = "pan_number_last4", length = 4)
	private String panNumberLast4;

	/** Keyed fingerprints of the numbers, for finding them on other accounts without decrypting. */
	@Column(name = "aadhaar_fingerprint", length = 64)
	private String aadhaarFingerprint;

	@Column(name = "pan_fingerprint", length = 64)
	private String panFingerprint;

	@Column(name = "submitted_at", nullable = false)
	private Instant submittedAt;

	@Column(name = "reviewed_by")
	private UUID reviewedBy;

	@Column(name = "reviewed_at")
	private Instant reviewedAt;

	@Column(name = "rejection_reason", length = 500)
	private String rejectionReason;

	/** The reviewer the review is assigned to; only they may decide it. Kept after the decision. */
	@Column(name = "assigned_to")
	private UUID assignedTo;

	@Column(name = "assigned_at")
	private Instant assignedAt;

	protected KycSubmission() {
	}

	/** A draft, started by the first document uploaded; the name, date of birth and nationality are from the profile. */
	public static KycSubmission draft(UUID userId, String legalFirstName, String legalLastName, LocalDate dateOfBirth,
			String nationality, Instant now) {
		KycSubmission s = new KycSubmission();
		s.userId = userId;
		s.status = KycSubmissionStatus.DRAFT;
		s.legalFirstName = legalFirstName;
		s.legalLastName = legalLastName;
		s.dateOfBirth = dateOfBirth;
		s.nationality = nationality;
		s.submittedAt = now;
		return s;
	}

	/** Adds (or, in a draft, replaces) the Aadhaar number: encrypted, its last 4 digits, and its fingerprint. */
	public void setAadhaar(String encrypted, String last4, String fingerprint) {
		requireDraft();
		this.aadhaarNumberEncrypted = encrypted;
		this.aadhaarNumberLast4 = last4;
		this.aadhaarFingerprint = fingerprint;
	}

	/** Adds (or, in a draft, replaces) the PAN. */
	public void setPan(String encrypted, String last4, String fingerprint) {
		requireDraft();
		this.panNumberEncrypted = encrypted;
		this.panNumberLast4 = last4;
		this.panFingerprint = fingerprint;
	}

	public boolean hasAadhaar() {
		return aadhaarNumberEncrypted != null;
	}

	public boolean hasPan() {
		return panNumberEncrypted != null;
	}

	/** Both documents are in: the submission goes for review, submitted now. */
	public void submitForReview(Instant now) {
		requireDraft();
		if (!hasAadhaar() || !hasPan()) {
			throw new IllegalStateException("Both Aadhaar and PAN are needed for review");
		}
		this.status = KycSubmissionStatus.PENDING;
		this.submittedAt = now;
	}

	private void requireDraft() {
		if (status != KycSubmissionStatus.DRAFT) {
			throw new IllegalStateException("Submission " + getId() + " is " + status);
		}
	}

	/** For submissions made before fingerprints were kept. */
	public void fingerprint(String aadhaarFingerprint, String panFingerprint) {
		this.aadhaarFingerprint = aadhaarFingerprint;
		this.panFingerprint = panFingerprint;
	}

	public String getDocumentNumberEncrypted() {
		return documentNumberEncrypted;
	}

	public String getAadhaarNumberEncrypted() {
		return aadhaarNumberEncrypted;
	}

	public String getPanNumberEncrypted() {
		return panNumberEncrypted;
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

	/** Assigns the review to a reviewer, who alone may now decide it. */
	public void assignTo(UUID reviewer, Instant now) {
		this.assignedTo = reviewer;
		this.assignedAt = now;
	}

	/** Gives the review back to the queue, for another reviewer to take. */
	public void unassign() {
		this.assignedTo = null;
		this.assignedAt = null;
	}

	public boolean isAssignedTo(UUID reviewer) {
		return reviewer.equals(assignedTo);
	}

	public UUID getAssignedTo() {
		return assignedTo;
	}

	public Instant getAssignedAt() {
		return assignedAt;
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

	public String getAadhaarNumberLast4() {
		return aadhaarNumberLast4;
	}

	public String getPanNumberLast4() {
		return panNumberLast4;
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
