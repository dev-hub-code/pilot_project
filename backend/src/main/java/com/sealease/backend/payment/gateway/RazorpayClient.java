package com.sealease.backend.payment.gateway;

import com.sealease.backend.payment.provider.PaymentProvider.VirtualAccountDetails;

import java.time.Instant;
import java.util.Map;

/** The parts of Razorpay's API the platform uses. Amounts are in paise. */
public interface RazorpayClient {

	/** Creates an order for the checkout; returns its id (order_…). */
	String createOrder(long amountPaise, String currency, String receipt, Map<String, String> notes);

	/** Opens a virtual account (bank account and UPI ID) that closes at {@code closeBy}. */
	VirtualAccount createVirtualAccount(long amountExpectedPaise, String description, Instant closeBy,
			Map<String, String> notes);

	/** Stops a virtual account taking money. */
	void closeVirtualAccount(String virtualAccountId);

	GatewayPayment fetchPayment(String paymentId);

	/** Captures an authorized payment, so the money is taken. */
	GatewayPayment capture(String paymentId, long amountPaise, String currency);

	record VirtualAccount(String id, VirtualAccountDetails details) {
	}

	/**
	 * A payment at Razorpay.
	 *
	 * @param orderId the checkout order it pays, or null (e.g. a transfer into a virtual account)
	 * @param status  created, authorized, captured, refunded or failed
	 */
	record GatewayPayment(String id, String orderId, long amountPaise, String currency, String status) {

		public boolean captured() {
			return "captured".equals(status);
		}

		public boolean authorized() {
			return "authorized".equals(status);
		}

	}

	/** Razorpay refused or could not be reached. The message is safe to show. */
	class RazorpayException extends RuntimeException {

		public RazorpayException(String message, Throwable cause) {
			super(message, cause);
		}

	}

}
