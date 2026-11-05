package com.sealease.backend.withdrawal.dto;

import com.sealease.backend.common.money.MoneyResponse;

import java.util.List;

/**
 * Everything staff see on one withdrawal: the withdrawal, who asked for it, its timeline (every step, with who made it)
 * and internal notes.
 *
 * @param walletNow       what is in the investor's wallet the withdrawal draws from, now
 * @param batchReference  the payout batch it is paid in, once payment is initiated
 */
public record WithdrawalAdminDetail(WithdrawalResponse withdrawal, Investor investor, MoneyResponse walletNow,
		String batchReference, List<WithdrawalEventResponse> timeline, List<WithdrawalNoteResponse> notes) {

	public record Investor(String userCode, String name, String email, String kycStatus) {
	}

}
