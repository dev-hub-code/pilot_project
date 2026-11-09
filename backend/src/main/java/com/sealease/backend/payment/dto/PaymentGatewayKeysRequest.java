package com.sealease.backend.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The Razorpay credentials, as an admin sets them (Dashboard → Account &amp; Settings → API keys, and Webhooks).
 *
 * @param keyId         rzp_test_… or rzp_live_…
 * @param keySecret     the key's secret; blank keeps the one saved (only for the same key id)
 * @param webhookSecret the secret set on the Razorpay webhook; blank keeps the one in force
 * @param merchantName  shown at the top of the checkout; blank uses the server's
 */
public record PaymentGatewayKeysRequest(
		@NotBlank @Pattern(regexp = "^\\s*rzp_(test|live)_[A-Za-z0-9]{6,40}\\s*$",
				message = "Enter the key id from Razorpay (rzp_test_… or rzp_live_…)") String keyId,
		@Size(max = 100) @Pattern(regexp = "^\\s*(\\S{8,100})?\\s*$", message = "Enter the key secret as Razorpay shows it")
		String keySecret,
		@Size(max = 200) @Pattern(regexp = "^\\s*(\\S{8,200})?\\s*$",
				message = "The webhook secret is at least 8 characters, without spaces") String webhookSecret,
		@Size(max = 60, message = "Up to 60 characters") String merchantName) {

	@Override
	public String toString() {
		return "PaymentGatewayKeysRequest[keyId=" + keyId + ", merchantName=" + merchantName + "]";
	}

}
