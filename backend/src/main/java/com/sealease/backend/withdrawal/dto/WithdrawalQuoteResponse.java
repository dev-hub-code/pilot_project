package com.sealease.backend.withdrawal.dto;

import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.ledger.service.EarningsBalance;
import com.sealease.backend.withdrawal.entity.WithdrawalPayoutMethod;

/**
 * What withdrawing an amount from a wallet would pay, worked out by the backend with the current charges and tax.
 *
 * @param amount    taken from the wallet; {@code fee} and {@code tax} come out of it and {@code net} is paid
 * @param remaining what stays in the wallet
 * @param problem   why it cannot be withdrawn; null when {@code allowed}
 */
public record WithdrawalQuoteResponse(EarningsBalance balance, WithdrawalPayoutMethod payoutMethod,
		MoneyResponse available, MoneyResponse amount, MoneyResponse fee, MoneyResponse tax, MoneyResponse net,
		MoneyResponse remaining, MoneyResponse minimum, boolean allowed, String problem) {
}
