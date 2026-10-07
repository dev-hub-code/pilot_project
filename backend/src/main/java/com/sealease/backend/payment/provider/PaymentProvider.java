package com.sealease.backend.payment.provider;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.payment.entity.PaymentMethod;

/**
 * A way to collect money. Implementations are Spring beans; the payment service picks the one for
 * the method the investor chose. A real card gateway (Stripe, Adyen, ...) is added by implementing
 * {@link WebhookPaymentProvider}.
 */
public interface PaymentProvider {

	/** Stored on each payment; also the webhook path segment. Never change it once in use. */
	String name();

	PaymentMethod method();

	/**
	 * Registers the payment with the provider.
	 *
	 * @return the provider's reference for it, unique per provider
	 */
	String open(String orderNumber, Money amount);

}
