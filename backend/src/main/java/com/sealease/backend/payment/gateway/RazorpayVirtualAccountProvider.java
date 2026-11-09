package com.sealease.backend.payment.gateway;

import com.sealease.backend.payment.entity.PaymentMethod;
import com.sealease.backend.payment.provider.PaymentProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Razorpay Smart Collect: a virtual bank account opened for one payment. No longer offered, so none is opened; this only
 * closes any opened before, when its order is paid, cancelled or lapses. Money that still arrives in one is recorded
 * from Razorpay's webhook (virtual_account.credited).
 */
@Component
public class RazorpayVirtualAccountProvider implements PaymentProvider {

	private static final Logger log = LoggerFactory.getLogger(RazorpayVirtualAccountProvider.class);

	private final RazorpayClient razorpay;

	public RazorpayVirtualAccountProvider(RazorpayClient razorpay) {
		this.razorpay = razorpay;
	}

	@Override
	public String name() {
		return RazorpayCheckoutProvider.NAME;
	}

	@Override
	public PaymentMethod method() {
		return PaymentMethod.VIRTUAL_ACCOUNT;
	}

	@Override
	public Opened open(OpenRequest request) {
		throw new UnsupportedOperationException("Bank transfer into a virtual account is no longer offered");
	}

	@Override
	public void close(String providerReference) {
		try {
			razorpay.closeVirtualAccount(providerReference);
		}
		catch (RuntimeException e) {
			// It closes on its own at close_by; money that still arrives is recorded for refund.
			log.warn("Could not close virtual account {}: {}", providerReference, e.getMessage());
		}
	}

}
