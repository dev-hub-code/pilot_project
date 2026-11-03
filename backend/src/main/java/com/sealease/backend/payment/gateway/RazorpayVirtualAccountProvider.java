package com.sealease.backend.payment.gateway;

import com.sealease.backend.payment.entity.PaymentMethod;
import com.sealease.backend.payment.provider.PaymentProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Razorpay Smart Collect: a virtual bank account (and UPI ID) opened for one payment, which the investor pays by NEFT,
 * RTGS, IMPS or UPI from their own bank. It closes when the order is paid, cancelled or lapses. The provider reference
 * is the virtual account id.
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
		RazorpayClient.VirtualAccount account = razorpay.createVirtualAccount(Paise.of(request.amount()),
				"Order " + request.orderNumber(), request.payBy(), Map.of("order", request.orderNumber()));
		return new Opened(account.id(), account.details());
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
