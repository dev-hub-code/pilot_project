package com.sealease.backend.withdrawal.dto;

import com.sealease.backend.withdrawal.entity.WithdrawalPayoutMethod;

import java.time.Instant;

/**
 * How an investor's referral commission is withdrawn this year.
 *
 * @param mode      BANK_TRANSFER (in the referral file on the referral withdrawal day) or OFFICE_CASH (collected at the
 *                  office from that day)
 * @param chosen    whether the investor chose it this year; otherwise it is the default and they may still choose
 * @param chosenAt  when it was chosen; null for the default
 */
public record ReferralPayoutMode(int year, WithdrawalPayoutMethod mode, boolean chosen, Instant chosenAt) {
}
