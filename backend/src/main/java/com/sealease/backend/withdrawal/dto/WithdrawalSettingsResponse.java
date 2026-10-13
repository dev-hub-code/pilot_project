package com.sealease.backend.withdrawal.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * The days of the month withdrawals are made. Each bank file is made the day before its withdrawal day, for finance
 * to pay on the day; cash is collected at the office from the withdrawal day.
 *
 * @param nextRentFileOn     when the next rent file will be made (this month's if not made yet)
 * @param nextReferralFileOn likewise for the referral file
 */
public record WithdrawalSettingsResponse(int rentWithdrawalDay, int referralWithdrawalDay, LocalDate nextRentFileOn,
		LocalDate nextReferralFileOn, Instant updatedAt) {
}
