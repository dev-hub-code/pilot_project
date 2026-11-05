package com.sealease.backend.payment.gateway;

import com.sealease.backend.common.money.Money;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.time.Duration;

/**
 * Razorpay, through which online investors pay (app.payments.razorpay). Off until the API keys are set: online
 * investors then pay by bank transfer or at the office, as network investors do. The switches, the checkout limit and the
 * payment windows here are defaults: admins change them in Platform config → Payment gateway
 * ({@code PaymentGatewaySettingsService}). The keys and secrets are set only here.
 *
 * @param keyId                 the API key id (rzp_test_… or rzp_live_…); public, the checkout needs it
 * @param keySecret             the API key secret: signs checkout results and authenticates API calls
 * @param webhookSecret         the secret set on the dashboard's webhook; signs every webhook delivery
 * @param merchantName          shown at the top of the checkout
 * @param checkoutWindow        how long an order stays reserved once the investor opens the checkout
 * @param virtualAccountWindow  how long an order stays reserved, and its virtual account open, for a bank transfer
 * @param virtualAccounts       whether investors may pay by bank transfer into a virtual account. Needs Razorpay's Smart
 *                              Collect activated on the account; until it is, set this false and the option is hidden
 * @param maxAmount             the most Razorpay accepts in one checkout payment, in rupees, as set on the account
 *                              (₹5,00,000 unless Razorpay has raised it); 0 for no limit. Larger orders are paid
 *                              another way
 */
@ConfigurationProperties(prefix = "app.payments.razorpay")
public record RazorpayProperties(
		@DefaultValue("") String keyId,
		@DefaultValue("") String keySecret,
		@DefaultValue("") String webhookSecret,
		@DefaultValue("https://api.razorpay.com") URI baseUrl,
		@DefaultValue("SeaLease") String merchantName,
		@DefaultValue("30m") Duration checkoutWindow,
		@DefaultValue("3d") Duration virtualAccountWindow,
		@DefaultValue("15s") Duration timeout,
		@DefaultValue("true") boolean virtualAccounts,
		@DefaultValue("500000") BigDecimal maxAmount) {

	/** Online investors pay through Razorpay only when the API keys are set. */
	public boolean enabled() {
		return !keyId.isBlank() && !keySecret.isBlank();
	}

	/** Whether one checkout payment may be this large. */
	public boolean checkoutAllows(Money amount) {
		return !hasCheckoutLimit() || amount.amount().compareTo(maxAmount) <= 0;
	}

	public boolean hasCheckoutLimit() {
		return maxAmount != null && maxAmount.signum() > 0;
	}

	/** Investors may pay by bank transfer into a virtual account: Razorpay is on, and Smart Collect is too. */
	public boolean virtualAccountsEnabled() {
		return enabled() && virtualAccounts;
	}

}
