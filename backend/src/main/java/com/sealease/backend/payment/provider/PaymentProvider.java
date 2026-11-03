package com.sealease.backend.payment.provider;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.payment.entity.PaymentMethod;

import java.time.Instant;

/**
 * A way to collect money. Implementations are Spring beans; the payment service picks the one for
 * the method the investor chose.
 */
public interface PaymentProvider {

	/** Stored on each payment. Never change it once in use. */
	String name();

	PaymentMethod method();

	/**
	 * Registers the payment with the provider.
	 *
	 * @return the provider's reference for it (unique per provider) and, for a virtual account, where to pay
	 */
	Opened open(OpenRequest request);

	/** Stops the provider taking more money for this payment (a virtual account is closed). Best effort. */
	default void close(String providerReference) {
	}

	/**
	 * @param payBy until when the payment can be made (a virtual account closes then)
	 */
	record OpenRequest(String orderNumber, Money amount, Instant payBy) {
	}

	/** @param virtualAccount where to pay, for a virtual account; otherwise null */
	record Opened(String reference, VirtualAccountDetails virtualAccount) {

		public static Opened of(String reference) {
			return new Opened(reference, null);
		}

	}

	/** A bank account (and UPI ID) that takes money for one payment. */
	record VirtualAccountDetails(String accountNumber, String ifsc, String accountName, String bankName, String upiId) {
	}

}
