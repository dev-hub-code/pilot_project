package com.sealease.backend.payment.gateway;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Razorpay signs what it sends with HMAC-SHA256, hex encoded: a checkout result over "order_id|payment_id" with the API
 * key secret, and a webhook over its raw body with the webhook secret. Compared in constant time.
 */
public final class RazorpaySignatures {

	private RazorpaySignatures() {
	}

	public static boolean checkoutValid(String secret, String orderId, String paymentId, String signature) {
		return matches(secret, orderId + "|" + paymentId, signature);
	}

	public static boolean webhookValid(String secret, String body, String signature) {
		return matches(secret, body, signature);
	}

	/** The signature of {@code payload}; tests use it to sign what they send. */
	public static String sign(String secret, String payload) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("HmacSHA256 unavailable", e);
		}
	}

	private static boolean matches(String secret, String payload, String signature) {
		if (secret == null || secret.isBlank() || signature == null || signature.isBlank()) {
			return false;
		}
		return MessageDigest.isEqual(sign(secret, payload).getBytes(StandardCharsets.US_ASCII),
				signature.strip().toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
	}

}
