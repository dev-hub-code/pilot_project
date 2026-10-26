package com.sealease.backend.agreement.entity;

import com.sealease.backend.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Reviewing one lease agreement: opened when the agreement is issued, assigned by a reviewer to themselves, and
 * marked done by them.
 */
@Entity
@Table(name = "agreement_review_tasks")
public class AgreementReviewTask extends BaseEntity {

	public enum Status {
		OPEN, DONE
	}

	@Column(name = "agreement_id", nullable = false, updatable = false)
	private UUID agreementId;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private Status status;

	@Column(name = "assigned_to")
	private UUID assignedTo;

	@Column(name = "assigned_at")
	private Instant assignedAt;

	@Column(name = "completed_by")
	private UUID completedBy;

	@Column(name = "completed_at")
	private Instant completedAt;

	protected AgreementReviewTask() {
	}

	public AgreementReviewTask(UUID agreementId) {
		this.agreementId = agreementId;
		this.status = Status.OPEN;
	}

	public void assignTo(UUID reviewerId, Instant now) {
		this.assignedTo = reviewerId;
		this.assignedAt = now;
	}

	public void unassign() {
		this.assignedTo = null;
		this.assignedAt = null;
	}

	public void complete(UUID reviewerId, Instant now) {
		this.status = Status.DONE;
		this.completedBy = reviewerId;
		this.completedAt = now;
	}

	public boolean isAssignedTo(UUID reviewerId) {
		return reviewerId.equals(assignedTo);
	}

	public UUID getAgreementId() {
		return agreementId;
	}

	public Status getStatus() {
		return status;
	}

	public UUID getAssignedTo() {
		return assignedTo;
	}

	public Instant getAssignedAt() {
		return assignedAt;
	}

	public UUID getCompletedBy() {
		return completedBy;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

}
