package com.sealease.backend.approval.dto;

import com.sealease.backend.approval.entity.ApprovalRequest;
import com.sealease.backend.approval.entity.ApprovalStatus;
import com.sealease.backend.approval.entity.ApprovalType;
import com.sealease.backend.ledger.dto.AdjustmentRequest;
import com.sealease.backend.payment.dto.RefundRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/** What the approval endpoints send and receive. */
public final class ApprovalDtos {

	private ApprovalDtos() {
	}

	/** A request: {@code adjustment} for a ledger adjustment, {@code paymentId} and {@code refund} for a refund. */
	public record CreateApprovalRequest(
			@NotNull ApprovalType type,
			@NotBlank @Size(max = 500) String reason,
			UUID assignedTo,
			@Valid AdjustmentRequest adjustment,
			UUID paymentId,
			@Valid RefundRequest refund) {
	}

	public record RejectApprovalRequest(@NotBlank @Size(max = 500) String reason) {
	}

	public record ApprovalResponse(UUID id, ApprovalType type, ApprovalStatus status, String summary, String reason,
			UUID createdBy, UUID assignedTo, UUID decidedBy, Instant decidedAt, String decisionReason,
			String transactionReference, Instant createdAt) {

		public static ApprovalResponse from(ApprovalRequest a) {
			return new ApprovalResponse(a.getId(), a.getType(), a.getStatus(), a.getSummary(), a.getReason(),
					a.getCreatedBy(), a.getAssignedTo(), a.getDecidedBy(), a.getDecidedAt(), a.getDecisionReason(),
					a.getTransactionReference(), a.getCreatedAt());
		}

	}

}
