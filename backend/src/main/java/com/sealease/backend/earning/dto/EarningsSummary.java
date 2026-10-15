package com.sealease.backend.earning.dto;

import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.ledger.service.EarningsBalance;

import java.time.Instant;
import java.util.List;

/**
 * @param balances             what the platform owes the investor now (their wallet), per currency: all balances together
 * @param wallet               the same, per earnings balance (each withdrawn its own way) and currency
 * @param rentPaid             rent credited over all time, per currency
 * @param capitalReturned      capital credited back over all time, per currency
 * @param referralEarned       commission on referrals' rent credited over all time, per currency
 * @param leases               the rent of each container's lease, oldest lease first
 * @param withdrawalInProgress withdrawn from the wallet and not yet paid out, per currency
 * @param totalWithdrawn       paid out to the investor over all time, per currency
 * @param nextCreditAt         when the next hour of rent ends and is credited (a minute or so after); {@code null} with
 *                             no lease running or starting
 */
public record EarningsSummary(List<MoneyResponse> balances, List<WalletBalance> wallet, List<MoneyResponse> rentPaid,
		List<MoneyResponse> capitalReturned, List<MoneyResponse> referralEarned, List<LeaseRentResponse> leases,
		List<MoneyResponse> withdrawalInProgress, List<MoneyResponse> totalWithdrawn, Instant nextCreditAt) {

	/**
	 * One earnings balance: RENT_BANK is paid into a bank account, RENT_CASH collected in cash at the office, and
	 * REFERRAL either way, as the investor chooses.
	 */
	public record WalletBalance(EarningsBalance balance, MoneyResponse amount) {
	}

}
