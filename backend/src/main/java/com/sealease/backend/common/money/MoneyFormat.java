package com.sealease.backend.common.money;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Locale;

/**
 * Money as people read it: currency symbol, grouped digits, minor-unit scale, e.g. "₹1,23,456.50"
 * or "-₹500.00". Rupees use Indian grouping (lakh, crore); other currencies group in thousands.
 */
public final class MoneyFormat {

	private static final Locale INDIA = Locale.of("en", "IN");

	private MoneyFormat() {
	}

	public static String format(Money money) {
		BigDecimal value = money.toMinorUnitScale();
		String plain = value.abs().toPlainString();
		int dot = plain.indexOf('.');
		String whole = dot < 0 ? plain : plain.substring(0, dot);
		String fraction = dot < 0 ? "" : plain.substring(dot);
		String grouped = "INR".equals(money.currency().getCurrencyCode()) ? indian(whole) : thousands(whole);
		return (value.signum() < 0 ? "-" : "") + symbol(money.currency()) + grouped + fraction;
	}

	public static String symbol(Currency currency) {
		return currency.getSymbol(INDIA);
	}

	/** 12345678 → "1,23,45,678": the last three digits, then groups of two. */
	private static String indian(String digits) {
		if (digits.length() <= 3) {
			return digits;
		}
		String last = digits.substring(digits.length() - 3);
		String rest = digits.substring(0, digits.length() - 3);
		StringBuilder out = new StringBuilder();
		int head = rest.length() % 2;
		if (head > 0) {
			out.append(rest, 0, head);
		}
		for (int i = head; i < rest.length(); i += 2) {
			if (!out.isEmpty()) {
				out.append(',');
			}
			out.append(rest, i, i + 2);
		}
		return out.append(',').append(last).toString();
	}

	private static String thousands(String digits) {
		StringBuilder out = new StringBuilder();
		int head = digits.length() % 3;
		if (head > 0) {
			out.append(digits, 0, head);
		}
		for (int i = head; i < digits.length(); i += 3) {
			if (!out.isEmpty()) {
				out.append(',');
			}
			out.append(digits, i, i + 3);
		}
		return out.toString();
	}

}
