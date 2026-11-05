package com.sealease.backend.payment.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.time.Duration;

/**
 * Razorpay, through which online investors pay (app.payments.razorpay). Off until the API keys are set: online
 * investors then pay by bank transfer or at the office, as network investors do.
 *
 * @param keyId                 the API key id (rzp_test_… or rzp_live_…); public, the checkout needs it
 * @param keySecret             the API key secret: signs checkout results and authenticates API calls
 * @param webhookSecret         the secret set on the dashboard's webhook; signs every webhook delivery
 * @param merchantName          shown at the top of the checkout
 * @param checkoutWindow        how long an order stays reserved once the investor opens the checkout
 * @param virtualAccountWindow  how long an order stays reserved, and its virtual account open, for a bank transfer
 * @param virtualAccounts       whether investors may pay by bank transfer into a virtual account. Needs Razorpay's Smart
 *                              Collect activated on the account; until it is, set this false and the option is hidden
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
		@DefaultValue("true") boolean virtualAccounts) {

	/** Online investors pay through Razorpay only when the API keys are set. */
	public boolean enabled() {
		return !keyId.isBlank() && !keySecret.isBlank();
	}

	/** Investors may pay by bank transfer into a virtual account: Razorpay is on, and Smart Collect is too. */
	public boolean virtualAccountsEnabled() {
		return enabled() && virtualAccounts;
	}

}
