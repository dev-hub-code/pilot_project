package com.sealease.backend.withdrawal.service;

import com.sealease.backend.withdrawal.entity.Withdrawal;
import com.sealease.backend.withdrawal.entity.WithdrawalBatch;
import com.sealease.backend.withdrawal.entity.WithdrawalKind;
import com.sealease.backend.withdrawal.entity.WithdrawalPayoutMethod;

import java.util.List;
import java.util.UUID;

/**
 * Pays approved withdrawals: a bank sheet finance pays from ({@link BankSheetPayoutProvider}), or, later, a payout
 * API. A provider only starts payment; it never marks a withdrawal paid. Paid (or failed) comes from evidence: the
 * reconciled sheet, finance's entry with the bank's reference, or a verified webhook
 * ({@link WithdrawalPaymentService#onWebhook}).
 *
 * <p>Implementations must be idempotent per withdrawal: each is passed once, under its lock, while APPROVED, and the
 * batch it lands in is recorded on it.
 */
public interface PayoutProvider {

	/** Stored on each withdrawal it pays, e.g. "BANK_SHEET". */
	String name();

	/** Hands the withdrawals (one kind, one payout method) over for payment; returns the batch they are paid in. */
	WithdrawalBatch initiate(WithdrawalKind kind, WithdrawalPayoutMethod method, List<Withdrawal> items, UUID actorId);

}
