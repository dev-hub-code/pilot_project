package com.sealease.backend.payment.dto;

/**
 * What the web app needs to open the gateway's checkout for a pending gateway payment.
 *
 * @param keyId       the gateway's public key id
 * @param orderId     the gateway's order for this payment
 * @param amountPaise the amount in the currency's smallest unit (paise)
 */
public record CheckoutInstructions(String keyId, String orderId, long amountPaise, String currency, String merchantName,
		String description, String prefillName, String prefillEmail) {
}
