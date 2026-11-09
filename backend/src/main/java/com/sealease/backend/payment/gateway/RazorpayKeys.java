package com.sealease.backend.payment.gateway;

/**
 * The Razorpay credentials in force: an admin's (Platform config → Payment gateway) over the server's configuration.
 * Never logged or returned whole.
 *
 * @param keyId            the API key id (rzp_test_… or rzp_live_…); blank when not set
 * @param keySecret        the API key secret; blank when not set
 * @param webhookSecret    the webhook secret; blank when not set
 * @param merchantName     shown at the top of the checkout
 * @param keysFromAdmin    the key id and secret were set by an admin, not the server
 * @param webhookFromAdmin the webhook secret was set by an admin, not the server
 */
public record RazorpayKeys(String keyId, String keySecret, String webhookSecret, String merchantName,
		boolean keysFromAdmin, boolean webhookFromAdmin) {

	/** Online investors pay through Razorpay only when the API keys are set. */
	public boolean enabled() {
		return !keyId.isBlank() && !keySecret.isBlank();
	}

	public boolean webhookEnabled() {
		return !webhookSecret.isBlank();
	}

	/** "live" or "test" from the key id's prefix; null without keys. */
	public String mode() {
		return !enabled() ? null : keyId.startsWith("rzp_live_") ? "live" : "test";
	}

	@Override
	public String toString() {
		return "RazorpayKeys[keyId=" + keyId + ", mode=" + mode() + ", fromAdmin=" + keysFromAdmin + "]";
	}

}
