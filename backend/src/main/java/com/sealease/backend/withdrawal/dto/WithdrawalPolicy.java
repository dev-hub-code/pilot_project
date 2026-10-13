package com.sealease.backend.withdrawal.dto;

import java.math.BigDecimal;

/**
 * When and how investors are paid, shown on their Withdrawals page.
 *
 * @param minimumAmount         a monthly bank withdrawal is made only for a balance of at least this
 * @param rentWithdrawalDay     rent is paid into bank accounts, or collected in cash at the office, from this day
 * @param referralWithdrawalDay likewise for referral earnings
 * @param officeAddress         where cash is collected; {@code officeHours} when
 */
public record WithdrawalPolicy(BigDecimal minimumAmount, int rentWithdrawalDay, int referralWithdrawalDay,
		String officeAddress, String officeHours) {
}
