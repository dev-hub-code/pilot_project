package com.sealease.backend.payment.dto;

import com.sealease.backend.common.money.MoneyResponse;

import java.time.Instant;

/**
 * How online payments run now, and the server's defaults they replace. The secrets are never shown: only their last four
 * characters, and whether the keys are test or live keys.
 *
 * @param keysConfigured        the Razorpay API keys are set on the server (without them, nothing here takes effect)
 * @param mode                  "test" or "live", from the key id; null without keys
 * @param webhookConfigured     the webhook secret is set (needed for payments confirmed by webhook)
 * @param maxAmount             the most one checkout payment can be; zero for no limit
 * @param defaults              the server's configuration, which applies until an admin saves
 * @param updatedAt             when an admin last saved; null while the defaults apply
 * @param keys                  the Razorpay credentials in force, without the secrets
 */
public record PaymentGatewaySettingsResponse(boolean keysConfigured, String mode, boolean webhookConfigured,
		boolean onlinePayments, MoneyResponse maxAmount, int checkoutWindowMinutes, Defaults defaults, Instant updatedAt,
		String updatedByName, Keys keys) {

	/**
	 * @param keyId              the API key id (public: the checkout needs it); null when not set
	 * @param keySecretHint      the key secret's last four characters; null when not set
	 * @param webhookSecretHint  the webhook secret's last four characters; null when not set
	 * @param source             ADMIN (saved here) or SERVER (environment); null when not set
	 * @param webhookSource      ADMIN or SERVER; null when not set
	 */
	public record Keys(String keyId, String keySecretHint, String webhookSecretHint, String merchantName, String source,
			String webhookSource, Instant updatedAt, String updatedByName) {
	}

	public record Defaults(boolean onlinePayments, MoneyResponse maxAmount, int checkoutWindowMinutes) {
	}

}
