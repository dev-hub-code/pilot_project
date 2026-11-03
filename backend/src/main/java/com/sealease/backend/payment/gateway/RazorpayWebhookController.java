package com.sealease.backend.payment.gateway;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Razorpay's webhook (set on the dashboard: Settings → Webhooks, events payment.authorized, payment.captured and
 * virtual_account.credited). Open to the internet: each delivery is checked against the webhook secret. A failure
 * answers 5xx, so Razorpay delivers it again.
 */
@RestController
public class RazorpayWebhookController {

	public static final String PATH = "/api/v1/webhooks/razorpay";

	private final GatewayPayments gateway;
	private final RazorpayProperties properties;

	public RazorpayWebhookController(GatewayPayments gateway, RazorpayProperties properties) {
		this.gateway = gateway;
		this.properties = properties;
	}

	@PostMapping(PATH)
	public ResponseEntity<Void> receive(@RequestBody String body,
			@RequestHeader(name = "X-Razorpay-Signature", required = false) String signature) {
		if (properties.webhookSecret().isBlank()) {
			return ResponseEntity.notFound().build();
		}
		gateway.onWebhook(body, signature);
		return ResponseEntity.ok().build();
	}

}
