package com.sealease.backend.withdrawal.dto;

import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.ledger.service.EarningsBalance;
import com.sealease.backend.withdrawal.entity.Withdrawal;
import com.sealease.backend.withdrawal.entity.WithdrawalOrigin;
import com.sealease.backend.withdrawal.entity.WithdrawalPayoutMethod;
import com.sealease.backend.withdrawal.entity.WithdrawalStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * A withdrawal. Staff views carry who approved or rejected it; investor views leave those
 * {@code null}.
 *
 * @param origin       INVESTOR when the investor asked for it; SCHEDULED when the monthly run made it
 * @param balance      the earnings balance it draws from: RENT_BANK, RENT_CASH or REFERRAL
 * @param payoutMethod BANK_TRANSFER into the bank account shown, or OFFICE_CASH collected at the office (no bank
 *                     details; {@code payoutReference} is then the cash receipt number)
 * @param createdBy    the finance user who made it at the office (staff views only)
 * @param ticketId     the support ticket opened when its bank payment failed
 */
public record WithdrawalResponse(
		UUID id,
		String reference,
		UUID userId,
		MoneyResponse amount,
		WithdrawalOrigin origin,
		WithdrawalStatus status,
		WithdrawalPayoutMethod payoutMethod,
		EarningsBalance balance,
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
		UUID createdBy,
		UUID ticketId,
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
				w.getOrigin(), w.getStatus(), w.getPayoutMethod(), w.getBalance(), w.getBankHolderName(), w.getBankName(),
				w.isOfficeCash() ? null : "•••• " + w.getBankAccountLast4(),
				w.getRequiredApprovals(), w.approvals(), staff ? w.getFirstApprovedBy() : null, w.getFirstApprovedAt(),
				staff ? w.getSecondApprovedBy() : null, w.getSecondApprovedAt(), staff ? w.getRejectedBy() : null,
				w.getRejectionReason(), staff ? w.getBatchId() : null, w.getPayoutReference(), w.getFailureReason(),
				staff ? w.getCreatedBy() : null, w.getTicketId(), w.getCreatedAt(), w.getClosedAt());
	}

}
