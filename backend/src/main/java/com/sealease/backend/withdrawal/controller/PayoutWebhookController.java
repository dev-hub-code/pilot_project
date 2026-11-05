package com.sealease.backend.withdrawal.controller;

import com.sealease.backend.withdrawal.service.WithdrawalPaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * A payout provider's status updates (see {@link WithdrawalPaymentService#onWebhook} for the format). Open to the
 * internet: each delivery is checked against the webhook secret; with no secret configured the endpoint does not exist
 * (404). A failure answers 5xx so the provider delivers it again; a redelivery is recognised and changes nothing.
 */
@RestController
public class PayoutWebhookController {

	public static final String PATH = "/api/v1/webhooks/payouts";

	private final WithdrawalPaymentService payments;

	public PayoutWebhookController(WithdrawalPaymentService payments) {
		this.payments = payments;
	}

	@PostMapping(PATH)
	public ResponseEntity<Map<String, String>> receive(@RequestBody String body,
			@RequestHeader(name = "X-Payout-Signature", required = false) String signature) {
		if (!payments.webhookEnabled()) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(Map.of("outcome", payments.onWebhook(body, signature).name()));
	}

}
