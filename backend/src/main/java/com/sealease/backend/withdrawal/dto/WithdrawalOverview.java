package com.sealease.backend.withdrawal.dto;

import com.sealease.backend.bankaccount.entity.BankAccountStatus;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.investment.entity.HoldingStatus;
import com.sealease.backend.ledger.service.EarningsBalance;
import com.sealease.backend.withdrawal.entity.WithdrawalPayoutMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Everything the investor's Withdrawals page shows, worked out by the backend, in the platform's currency.
 *
 * @param totalInvestment     paid for, over every investment
 * @param totalEarned         returns credited so far: rent, capital paid back and referral commission
 * @param available           in the wallets now, ready to withdraw
 * @param locked              principal not (yet) withdrawable: the investments' amounts less capital paid back
 * @param pending             withdrawn from the wallets and not yet paid out
 * @param withdrawn           paid out over all time
 * @param activePlans         investments whose term is running
 * @param canRequest          whether this investor requests their own withdrawals (online investors); network
 *                            investors are paid by withdrawal runs
 * @param payoutMethod        how their withdrawals are paid
 * @param processingDays      working days a withdrawal usually takes once approved; null when not set
 */
public record WithdrawalOverview(
		String currency,
		MoneyResponse totalInvestment,
		MoneyResponse totalEarned,
		MoneyResponse available,
		MoneyResponse locked,
		MoneyResponse pending,
		MoneyResponse withdrawn,
		int activePlans,
		boolean canRequest,
		WithdrawalPayoutMethod payoutMethod,
		MoneyResponse minimum,
		BigDecimal feeFlat,
		BigDecimal feePercent,
		BigDecimal taxPercent,
		Integer processingDays,
		List<Wallet> wallets,
		List<Investment> investments,
		List<BankAccount> bankAccounts) {

	/**
	 * One wallet earnings are credited to and withdrawn from.
	 *
	 * @param openWithdrawalId the withdrawal in progress from it; only one at a time
	 */
	public record Wallet(EarningsBalance balance, MoneyResponse available, UUID openWithdrawalId,
			String openWithdrawalReference) {
	}

	/**
	 * One investment: what is locked and why, and what it has earned (credited to the wallets, where it can be
	 * withdrawn).
	 *
	 * @param lockedPrincipal principal not withdrawable now
	 * @param lockReason      why
	 * @param earned          rent and capital credited from it so far
	 * @param nextUnlock      when the next part of the principal comes back, for plans that pay it back; else null
	 */
	public record Investment(UUID holdingId, String productCode, String productTitle, HoldingStatus status,
			MoneyResponse invested, MoneyResponse earned, MoneyResponse lockedPrincipal, String lockReason,
			LocalDate leaseStartsOn, LocalDate leaseEndsOn, LocalDate nextUnlock) {
	}

	/** A bank account a withdrawal can be paid into: verified, number masked. */
	public record BankAccount(UUID id, String holderName, String bankName, String accountMasked, boolean primary,
			BankAccountStatus status) {
	}

}
