package com.sealease.backend.approval.entity;

import com.sealease.backend.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A sensitive operation waiting for someone other than its maker. */
@Entity
@Table(name = "approval_requests")
public class ApprovalRequest extends BaseEntity {

	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false, length = 40, updatable = false)
	private ApprovalType type;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private ApprovalStatus status = ApprovalStatus.PENDING;

	@Column(name = "summary", nullable = false, updatable = false)
	private String summary;

	@Column(name = "reason", nullable = false, length = 500, updatable = false)
	private String reason;

	@Column(name = "payload", nullable = false, updatable = false, columnDefinition = "text")
	private String payload;

	@Column(name = "created_by", nullable = false, updatable = false)
	private UUID createdBy;

	@Column(name = "assigned_to", updatable = false)
	private UUID assignedTo;

	@Column(name = "decided_by")
	private UUID decidedBy;

	@Column(name = "decided_at")
	private Instant decidedAt;

	@Column(name = "decision_reason", length = 500)
	private String decisionReason;

	@Column(name = "transaction_reference", length = 120)
	private String transactionReference;

	protected ApprovalRequest() {
	}

	public ApprovalRequest(ApprovalType type, String summary, String reason, String payload, UUID createdBy, UUID assignedTo) {
		this.type = type;
		this.summary = summary;
		this.reason = reason;
		this.payload = payload;
		this.createdBy = createdBy;
		this.assignedTo = assignedTo;
	}

	public void approve(UUID checker, Instant at, String reference) {
		this.status = ApprovalStatus.APPROVED;
		this.decidedBy = checker;
		this.decidedAt = at;
		this.transactionReference = reference;
	}

	public void reject(UUID checker, Instant at, String why) {
		this.status = ApprovalStatus.REJECTED;
		this.decidedBy = checker;
		this.decidedAt = at;
		this.decisionReason = why;
	}

	public ApprovalType getType() {
		return type;
	}

	public ApprovalStatus getStatus() {
		return status;
	}

	public String getSummary() {
		return summary;
	}

	public String getReason() {
		return reason;
	}

	public String getPayload() {
		return payload;
	}

	public UUID getCreatedBy() {
		return createdBy;
	}

	public UUID getAssignedTo() {
		return assignedTo;
	}

	public UUID getDecidedBy() {
		return decidedBy;
	}

	public Instant getDecidedAt() {
		return decidedAt;
	}

	public String getDecisionReason() {
		return decisionReason;
	}

	public String getTransactionReference() {
		return transactionReference;
	}

}
