package com.sealease.backend.withdrawal.dto;

import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.withdrawal.entity.Withdrawal;
import com.sealease.backend.withdrawal.entity.WithdrawalStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * A withdrawal. Staff views carry who approved or rejected it; investor views leave those
 * {@code null}.
 */
public record WithdrawalResponse(
		UUID id,
		String reference,
		UUID userId,
		MoneyResponse amount,
		WithdrawalStatus status,
		String bankHolderName,
		String bankName,
		String bankAccountMasked,
		int requiredApprovals,
		int approvals,
		UUID firstApprovedBy,
		Instant firstApprovedAt,
		UUID secondApprovedBy,
		Instant secondApprovedAt,
		UUID rejectedBy,
		String rejectionReason,
		UUID batchId,
		String payoutReference,
		String failureReason,
		Instant createdAt,
		Instant closedAt) {

	public static WithdrawalResponse forStaff(Withdrawal w) {
		return of(w, true);
	}

	public static WithdrawalResponse forInvestor(Withdrawal w) {
		return of(w, false);
	}

	private static WithdrawalResponse of(Withdrawal w, boolean staff) {
		return new WithdrawalResponse(w.getId(), w.getReference(), w.getUserId(), MoneyResponse.from(w.amount()),
				w.getStatus(), w.getBankHolderName(), w.getBankName(), "•••• " + w.getBankAccountLast4(),
				w.getRequiredApprovals(), w.approvals(), staff ? w.getFirstApprovedBy() : null, w.getFirstApprovedAt(),
				staff ? w.getSecondApprovedBy() : null, w.getSecondApprovedAt(), staff ? w.getRejectedBy() : null,
				w.getRejectionReason(), staff ? w.getBatchId() : null, w.getPayoutReference(), w.getFailureReason(),
				w.getCreatedAt(), w.getClosedAt());
	}

}
