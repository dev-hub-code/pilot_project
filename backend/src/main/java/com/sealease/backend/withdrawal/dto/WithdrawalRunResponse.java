package com.sealease.backend.withdrawal.dto;

import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.withdrawal.entity.WithdrawalKind;
import com.sealease.backend.withdrawal.entity.WithdrawalPayoutMethod;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One withdrawal run: every eligible investor's rent (or referral) balances withdrawn into a bank sheet and a cash
 * sheet.
 *
 * @param kind           RENT or REFERRAL; null for runs made before they were separate (both)
 * @param bankBatchId    the batch of bank transfers; null when nobody is paid by bank
 * @param cashBatchId    the batch paid in cash at the office; null when nobody is paid in cash
 * @param bankInvestors  investors on the bank sheet
 * @param cashInvestors  investors on the cash sheet
 * @param skipped        investors with money to withdraw who were left out, how they would have been paid, and why
 */
public record WithdrawalRunResponse(UUID id, String reference, WithdrawalKind kind, String currency, Instant runAt, UUID triggeredBy,
		UUID bankBatchId, String bankBatchReference, int bankInvestors, MoneyResponse bankTotal,
		UUID cashBatchId, String cashBatchReference, int cashInvestors, MoneyResponse cashTotal, List<Skipped> skipped) {

	/** @param userCode the investor's readable ID ("SL03500"), filled in when the run is read */
	public record Skipped(UUID userId, String userCode, WithdrawalPayoutMethod payoutMethod, String reason) {

		public Skipped(UUID userId, WithdrawalPayoutMethod payoutMethod, String reason) {
			this(userId, null, payoutMethod, reason);
		}

	}

}
