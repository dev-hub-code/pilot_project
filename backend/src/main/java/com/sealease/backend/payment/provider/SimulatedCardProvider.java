package com.sealease.backend.payment.provider;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.payment.entity.PaymentMethod;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Behaves like a card gateway: payments get a gateway reference and their outcome arrives as an
 * HMAC-signed webhook ({@code X-Signature: sha256=<hex>}), exactly the path a real gateway takes.
 * {@link #signedEvent} lets the investor portal trigger an outcome during development.
 */
@Component
@ConditionalOnProperty(prefix = "app.payments.simulator", name = "enabled", havingValue = "true")
public class SimulatedCardProvider implements WebhookPaymentProvider {

	public static final String NAME = "SIMULATOR";
	private static final String SIGNATURE_PREFIX = "sha256=";

	private final JsonMapper jsonMapper;
	private final byte[] secret;

	public SimulatedCardProvider(JsonMapper jsonMapper, SimulatorProperties properties) {
		if (properties.webhookSecret() == null || properties.webhookSecret().length() < 32) {
			throw new IllegalStateException("app.payments.simulator.webhook-secret must be at least 32 characters");
		}
		this.jsonMapper = jsonMapper;
		this.secret = properties.webhookSecret().getBytes(StandardCharsets.UTF_8);
	}

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public PaymentMethod method() {
		return PaymentMethod.CARD;
	}

	@Override
	public String open(String orderNumber, Money amount) {
		return "sim_" + UUID.randomUUID().toString().replace("-", "");
	}

	@Override
	public ProviderEvent verify(byte[] body, String signature) {
		if (signature == null || !signature.startsWith(SIGNATURE_PREFIX)) {
			throw new InvalidWebhookException("Missing webhook signature");
		}
		byte[] expected = hmac(body);
		byte[] given;
		try {
			given = HexFormat.of().parseHex(signature.substring(SIGNATURE_PREFIX.length()));
		}
		catch (IllegalArgumentException ex) {
			throw new InvalidWebhookException("Malformed webhook signature");
		}
		if (!MessageDigest.isEqual(expected, given)) {
			throw new InvalidWebhookException("Webhook signature does not match");
		}
		try {
			JsonNode json = jsonMapper.readTree(body);
			ProviderEvent.Outcome outcome = switch (json.path("type").asString()) {
				case "payment.succeeded" -> ProviderEvent.Outcome.SUCCEEDED;
				case "payment.failed" -> ProviderEvent.Outcome.FAILED;
				default -> throw new InvalidWebhookException("Unknown event type");
			};
			return new ProviderEvent(required(json, "id"), outcome, required(json, "paymentReference"),
					new BigDecimal(required(json, "amount")), required(json, "currency"),
					json.path("failureReason").isMissingNode() ? null : json.path("failureReason").asString(),
					jsonMapper.convertValue(json, Map.class));
		}
		catch (JacksonException | NumberFormatException ex) {
			throw new InvalidWebhookException("Malformed webhook body");
		}
	}

	/** A webhook delivery as the gateway would send it: the body and its signature header. */
	public SignedEvent signedEvent(String paymentReference, Money amount, ProviderEvent.Outcome outcome) {
		Map<String, Object> event = new LinkedHashMap<>();
		event.put("id", "evt_" + UUID.randomUUID().toString().replace("-", ""));
		event.put("type", outcome == ProviderEvent.Outcome.SUCCEEDED ? "payment.succeeded" : "payment.failed");
		event.put("paymentReference", paymentReference);
		event.put("amount", amount.amount().toPlainString());
		event.put("currency", amount.currency().getCurrencyCode());
		if (outcome == ProviderEvent.Outcome.FAILED) {
			event.put("failureReason", "Card declined (simulated)");
		}
		byte[] body = jsonMapper.writeValueAsBytes(event);
		return new SignedEvent(body, SIGNATURE_PREFIX + HexFormat.of().formatHex(hmac(body)));
	}

	public record SignedEvent(byte[] body, String signature) {
	}

	private byte[] hmac(byte[] body) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret, "HmacSHA256"));
			return mac.doFinal(body);
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("HmacSHA256 unavailable", ex);
		}
	}

	private static String required(JsonNode json, String field) {
		JsonNode node = json.path(field);
		if (node.isMissingNode() || node.isNull() || node.asString().isBlank()) {
			throw new InvalidWebhookException("Webhook field missing: " + field);
		}
		return node.asString();
	}

}
