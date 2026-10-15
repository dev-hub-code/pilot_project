package com.sealease.backend.withdrawal.dto;

import com.sealease.backend.common.api.Assignee;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.withdrawal.entity.WithdrawalKind;
import com.sealease.backend.withdrawal.entity.WithdrawalPayoutMethod;
import com.sealease.backend.withdrawal.entity.WithdrawalStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * One finance task: a withdrawal made by a withdrawal run, to pay into the investor's bank account or hand over in
 * cash at the office, then mark paid (collected) or failed.
 *
 * @param accountNumber in full while the bank task is open (to pay it), masked once it is done; null for cash
 * @param phone         cash tasks only: to call the investor in to collect
 * @param status        PROCESSING while open; then PAID, FAILED (held, with a ticket) or RETURNED (back in the wallet)
 * @param payoutReference the bank reference or cash receipt it was marked paid with
 * @param assignee        the finance member who took the task; only they may mark it
 */
public record FinanceTaskResponse(UUID id, String reference, WithdrawalKind kind, WithdrawalPayoutMethod payoutMethod,
		UUID batchId, String batchReference, UUID userId, String userCode, String name, String phone, String bankName,
		String accountHolder, String accountNumber, String ifsc, MoneyResponse amount, WithdrawalStatus status,
		String payoutReference, String failureReason, UUID ticketId, Instant createdAt, Instant closedAt,
		Assignee assignee) {
}
