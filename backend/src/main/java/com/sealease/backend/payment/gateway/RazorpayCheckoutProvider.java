package com.sealease.backend.payment.gateway;

import com.sealease.backend.payment.entity.PaymentMethod;
import com.sealease.backend.payment.provider.PaymentProvider;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Razorpay's checkout: a Razorpay order is created for the payment, and the investor pays it by UPI or net banking in
 * the checkout window. The provider reference is the Razorpay order id.
 */
@Component
public class RazorpayCheckoutProvider implements PaymentProvider {

	public static final String NAME = "RAZORPAY";

	private final RazorpayClient razorpay;

	public RazorpayCheckoutProvider(RazorpayClient razorpay) {
		this.razorpay = razorpay;
	}

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public PaymentMethod method() {
		return PaymentMethod.GATEWAY;
	}

	@Override
	public Opened open(OpenRequest request) {
		return Opened.of(razorpay.createOrder(Paise.of(request.amount()), request.amount().currency().getCurrencyCode(),
				request.orderNumber(), Map.of("order", request.orderNumber())));
	}

}
