package com.sealease.backend.withdrawal.service;

import com.sealease.backend.common.money.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

/**
 * What a withdrawal of an amount pays out: the charges (a flat amount plus a percentage) and the tax withheld (a
 * percentage) come out of the amount withdrawn, and the rest is paid. Every figure is rounded half-up to the currency's
 * minor unit (the paisa), and nothing is accepted from the caller but the amount: the investor's screen shows the same
 * quote the backend makes when the request is submitted.
 */
public final class WithdrawalCalculator {

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	private WithdrawalCalculator() {
	}

	/**
	 * @param feeFlat    fixed charge per withdrawal
	 * @param feePercent charge as a percentage of the amount
	 * @param taxPercent tax withheld as a percentage of the amount
	 */
	public record Charges(BigDecimal feeFlat, BigDecimal feePercent, BigDecimal taxPercent) {

		public static final Charges NONE = new Charges(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

	}

	/**
	 * @param available what the wallet holds (whole paisa)
	 * @param amount    what is taken from the wallet
	 * @param remaining what stays in the wallet afterwards
	 * @param problem   why it cannot be withdrawn; null when it can
	 */
	public record Quote(Money available, Money amount, Money fee, Money tax, Money net, Money remaining, Money minimum,
			String problem) {

		public boolean allowed() {
			return problem == null;
		}

	}

	/** Quotes withdrawing {@code amount} from a wallet holding {@code available}. */
	public static Quote quote(Money available, BigDecimal amount, Money minimum, Charges charges) {
		Currency currency = available.currency();
		int digits = Math.max(currency.getDefaultFractionDigits(), 0);
		Money zero = Money.zero(currency);
		if (amount == null || amount.signum() <= 0) {
			return new Quote(available, zero, zero, zero, zero, available, minimum, "Enter an amount to withdraw");
		}
		if (amount.stripTrailingZeros().scale() > digits) {
			return new Quote(available, zero, zero, zero, zero, available, minimum,
					"Amounts are in whole " + (digits == 2 ? "paise" : "minor units"));
		}
		Money gross = Money.of(amount.setScale(digits, RoundingMode.UNNECESSARY), currency);
		Money fee = Money.of(charges.feeFlat().add(percent(gross.amount(), charges.feePercent()))
			.setScale(digits, RoundingMode.HALF_UP), currency);
		Money tax = Money.of(percent(gross.amount(), charges.taxPercent()).setScale(digits, RoundingMode.HALF_UP), currency);
		Money net = gross.minus(fee).minus(tax);
		Money remaining = available.minus(gross);
		String problem = null;
		if (gross.isLessThan(minimum)) {
			problem = "The minimum withdrawal is " + minimum.display();
		}
		else if (available.isLessThan(gross)) {
			problem = "This wallet has " + available.display() + " available";
		}
		else if (!net.isPositive()) {
			problem = "Charges and tax would leave nothing to pay";
		}
		return new Quote(available, gross, fee, tax, net, remaining.isNegative() ? zero : remaining, minimum, problem);
	}

	private static BigDecimal percent(BigDecimal amount, BigDecimal percent) {
		return percent.signum() == 0 ? BigDecimal.ZERO : amount.multiply(percent).divide(HUNDRED, 10, RoundingMode.HALF_UP);
	}

}
