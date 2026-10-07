package com.sealease.backend.common.money;

/**
 * Money on the wire: the amount is a decimal <em>string</em> at the currency's minor-unit scale
 * (e.g. "50000.00"), so JavaScript clients never round-trip money through floating point.
 */
public record MoneyResponse(String amount, String currency) {

	public static MoneyResponse from(Money money) {
		return new MoneyResponse(money.toMinorUnitScale().toPlainString(), money.currency().getCurrencyCode());
	}

}
