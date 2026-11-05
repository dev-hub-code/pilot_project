package com.sealease.backend.payment.dto;

import com.sealease.backend.common.money.MoneyResponse;

import java.time.Instant;

/**
 * How online payments run now, and the server's defaults they replace. The API keys are never shown: only whether they
 * are set, and whether they are test or live keys.
 *
 * @param keysConfigured        the Razorpay API keys are set on the server (without them, nothing here takes effect)
 * @param mode                  "test" or "live", from the key id; null without keys
 * @param webhookConfigured     the webhook secret is set (needed for payments confirmed by webhook)
 * @param maxAmount             the most one checkout payment can be; zero for no limit
 * @param defaults              the server's configuration, which applies until an admin saves
 * @param updatedAt             when an admin last saved; null while the defaults apply
 */
public record PaymentGatewaySettingsResponse(boolean keysConfigured, String mode, boolean webhookConfigured,
		boolean onlinePayments, boolean virtualAccounts, MoneyResponse maxAmount, int checkoutWindowMinutes,
		int virtualAccountWindowHours, Defaults defaults, Instant updatedAt, String updatedByName) {

	public record Defaults(boolean onlinePayments, boolean virtualAccounts, MoneyResponse maxAmount,
			int checkoutWindowMinutes, int virtualAccountWindowHours) {
	}

}
