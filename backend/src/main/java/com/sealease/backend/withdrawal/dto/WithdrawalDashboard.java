package com.sealease.backend.withdrawal.dto;

import com.sealease.backend.common.money.MoneyResponse;

/**
 * The finance team's withdrawal queue at a glance, in the platform's currency. Each figure is a count with its total
 * amount (what was withdrawn from wallets, before charges).
 *
 * @param avgHoursToPay average hours from request to paid over the last 30 days; null with none paid
 */
public record WithdrawalDashboard(Figure submitted, Figure underReview, Figure onHold, Figure pendingSecondApproval,
		Figure approved, Figure inPayment, Figure failed, Figure paidToday, Figure paidThisMonth,
		Figure rejectedThisMonth, Double avgHoursToPay) {

	public record Figure(long count, MoneyResponse amount) {
	}

}
