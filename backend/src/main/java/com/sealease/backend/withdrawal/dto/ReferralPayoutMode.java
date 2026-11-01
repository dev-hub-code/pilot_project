package com.sealease.backend.withdrawal.dto;

import com.sealease.backend.withdrawal.entity.WithdrawalPayoutMethod;

import java.time.Instant;

/**
 * How an investor's returns and referral commission are paid out.
 *
 * @param mode       BANK_TRANSFER or OFFICE_CASH
 * @param chosen     whether a network investor has chosen it; otherwise it is the default
 * @param chosenAt   when it was chosen; null for the default
 * @param changeable network investors may change it at any time; online investors are always paid by bank
 */
public record ReferralPayoutMode(int year, WithdrawalPayoutMethod mode, boolean chosen, Instant chosenAt,
		boolean changeable) {
}
