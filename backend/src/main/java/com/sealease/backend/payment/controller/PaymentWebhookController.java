package com.sealease.backend.payment.controller;

import com.sealease.backend.payment.service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Payment gateway callbacks. Public (gateways cannot sign in) but every delivery must carry a
 * valid signature over the exact raw body, which is why the body is taken as bytes.
 */
@RestController
public class PaymentWebhookController {

	public static final String PATH = "/api/v1/payments/webhooks/{provider}";

	private final PaymentService payments;

	public PaymentWebhookController(PaymentService payments) {
		this.payments = payments;
	}

	@PostMapping(PATH)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void receive(@PathVariable String provider, @RequestBody byte[] body,
			@RequestHeader(value = "X-Signature", required = false) String signature) {
		payments.receiveWebhook(provider, body, signature);
	}

}
