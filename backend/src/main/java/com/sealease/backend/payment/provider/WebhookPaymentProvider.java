package com.sealease.backend.payment.provider;

/** A provider that reports payment outcomes asynchronously, by signed webhook. */
public interface WebhookPaymentProvider extends PaymentProvider {

	/**
	 * Authenticates and parses a webhook delivery.
	 *
	 * @throws InvalidWebhookException when the signature does not verify or the body is malformed
	 */
	ProviderEvent verify(byte[] body, String signature);

}
