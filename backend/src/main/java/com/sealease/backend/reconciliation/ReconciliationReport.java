package com.sealease.backend.reconciliation;

import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.ledger.dto.TrialBalanceLine;
import com.sealease.backend.payment.dto.UnmatchedReceiptResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What needs finance's attention: money whose outcome is unclear or overdue, and checks that the ledger agrees with the
 * payments and withdrawals it records. {@code issues} counts them all; zero means everything reconciles.
 */
public record ReconciliationReport(Instant generatedAt, int issues, List<PayoutEvent> payoutEvents,
		List<StuckWithdrawal> stuckWithdrawals, List<PendingRefund> pendingRefunds,
		List<UnmatchedReceiptResponse> unmatchedReceipts, List<InTransit> inTransit, List<TrialBalanceLine> trialBalance,
		List<UnpostedDeposit> unpostedDeposits) {

	/** A payout-provider event that could not be applied automatically (recent ones). */
	public record PayoutEvent(UUID id, String eventId, String eventType, String outcome, String providerReference,
			String withdrawalReference, String withdrawalStatus, Instant receivedAt) {
	}

	/** Handed to the bank or provider and still not paid or failed after the expected time. */
	public record StuckWithdrawal(UUID id, String reference, String status, MoneyResponse amount, Instant since) {
	}

	/** Money received that must go back to the investor, not refunded after the expected time. */
	public record PendingRefund(UUID paymentId, UUID orderId, String method, MoneyResponse amount, Instant since) {
	}

	/** The ledger's withdrawals in transit against the open withdrawals that reserved money. */
	public record InTransit(String currency, MoneyResponse ledger, MoneyResponse openWithdrawals, boolean matches) {
	}

	/** A payment settled (or refunded) since deposits are posted to the ledger, without its posting. */
	public record UnpostedDeposit(UUID paymentId, String status, MoneyResponse amount, Instant settledAt, String missing) {
	}

}
