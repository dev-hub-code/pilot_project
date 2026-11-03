package com.sealease.backend.payment.gateway;

import com.sealease.backend.common.money.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

/** Razorpay counts rupees in paise. */
public final class Paise {

	public static final Currency INR = Currency.getInstance("INR");

	private Paise() {
	}

	/** An amount in whole paise; anything finer (or another currency) is refused rather than rounded. */
	public static long of(Money money) {
		if (!INR.equals(money.currency())) {
			throw new IllegalArgumentException("Razorpay takes rupees, not " + money.currency());
		}
		return money.amount().movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
	}

	public static Money toMoney(long paise, String currency) {
		return Money.of(BigDecimal.valueOf(paise).movePointLeft(2), Currency.getInstance(currency));
	}

}
