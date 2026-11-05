package com.sealease.backend.payment.service;

import com.sealease.backend.common.money.Money;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * How online payments run now: the admin's settings over the server's defaults.
 *
 * @param keysConfigured  the Razorpay API keys are set (nothing is offered through Razorpay without them)
 * @param onlinePayments  the admin's switch for paying through Razorpay
 * @param virtualAccounts the admin's switch for bank transfer into a virtual account
 * @param maxAmount       the most one checkout payment can be, in rupees; zero for no limit
 */
public record GatewaySettings(boolean keysConfigured, boolean onlinePayments, boolean virtualAccounts,
		BigDecimal maxAmount, Duration checkoutWindow, Duration virtualAccountWindow) {

	/** Online investors are offered Razorpay's checkout. */
	public boolean checkoutOffered() {
		return keysConfigured && onlinePayments;
	}

	/** Online investors are offered bank transfer into a virtual account. */
	public boolean virtualAccountsOffered() {
		return checkoutOffered() && virtualAccounts;
	}

	public boolean hasCheckoutLimit() {
		return maxAmount != null && maxAmount.signum() > 0;
	}

	/** Whether one checkout payment may be this large. */
	public boolean checkoutAllows(Money amount) {
		return !hasCheckoutLimit() || amount.amount().compareTo(maxAmount) <= 0;
	}

}
