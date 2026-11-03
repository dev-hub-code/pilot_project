package com.sealease.backend.payment.gateway;

import com.sealease.backend.payment.provider.PaymentProvider.VirtualAccountDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Razorpay's REST API (https://razorpay.com/docs/api/), authenticated with the API key. */
@Component
public class HttpRazorpayClient implements RazorpayClient {

	private static final Logger log = LoggerFactory.getLogger(HttpRazorpayClient.class);

	private final RestClient client;

	public HttpRazorpayClient(RazorpayProperties properties) {
		JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(properties.timeout()).build());
		requests.setReadTimeout(properties.timeout());
		String credentials = Base64.getEncoder().encodeToString(
				(properties.keyId() + ":" + properties.keySecret()).getBytes(StandardCharsets.UTF_8));
		this.client = RestClient.builder()
			.baseUrl(properties.baseUrl().toString())
			.requestFactory(requests)
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
			.build();
	}

	@Override
	public String createOrder(long amountPaise, String currency, String receipt, Map<String, String> notes) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("amount", amountPaise);
		body.put("currency", currency);
		body.put("receipt", receipt);
		body.put("notes", notes);
		return call("create an order", () -> post("/v1/orders", body)).path("id").asString();
	}

	@Override
	public VirtualAccount createVirtualAccount(long amountExpectedPaise, String description, Instant closeBy,
			Map<String, String> notes) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("receivers", Map.of("types", List.of("bank_account", "vpa")));
		body.put("description", description);
		body.put("amount_expected", amountExpectedPaise);
		body.put("close_by", closeBy.getEpochSecond());
		body.put("notes", notes);
		JsonNode account = call("open a virtual account", () -> post("/v1/virtual_accounts", body));
		String number = null, ifsc = null, name = null, bank = null, upi = null;
		for (JsonNode receiver : account.path("receivers")) {
			switch (receiver.path("entity").asString()) {
				case "bank_account" -> {
					number = receiver.path("account_number").asString();
					ifsc = receiver.path("ifsc").asString();
					name = text(receiver, "name");
					bank = text(receiver, "bank_name");
				}
				case "vpa" -> upi = text(receiver, "address");
				default -> {
				}
			}
		}
		if (number == null || number.isBlank()) {
			throw new RazorpayException("Bank transfer is unavailable right now", null);
		}
		return new VirtualAccount(account.path("id").asString(), new VirtualAccountDetails(number, ifsc, name, bank, upi));
	}

	@Override
	public void closeVirtualAccount(String virtualAccountId) {
		call("close a virtual account", () -> post("/v1/virtual_accounts/" + virtualAccountId + "/close", Map.of()));
	}

	@Override
	public GatewayPayment fetchPayment(String paymentId) {
		return payment(call("look up a payment", () -> client.get().uri("/v1/payments/{id}", paymentId)
			.retrieve().body(JsonNode.class)));
	}

	@Override
	public GatewayPayment capture(String paymentId, long amountPaise, String currency) {
		return payment(call("capture a payment", () -> post("/v1/payments/" + paymentId + "/capture",
				Map.of("amount", amountPaise, "currency", currency))));
	}

	private JsonNode post(String path, Object body) {
		return client.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
	}

	private static GatewayPayment payment(JsonNode p) {
		return new GatewayPayment(p.path("id").asString(), text(p, "order_id"), p.path("amount").asLong(),
				p.path("currency").asString(), p.path("status").asString());
	}

	private static String text(JsonNode node, String field) {
		JsonNode value = node.path(field);
		return value.isNull() || value.isMissingNode() ? null : value.asString();
	}

	private static JsonNode call(String what, Supplier<JsonNode> request) {
		try {
			JsonNode response = request.get();
			if (response == null) {
				throw new RazorpayException("Online payment is unavailable right now; please try again shortly", null);
			}
			return response;
		}
		catch (RestClientResponseException e) {
			// Razorpay explains a refusal in error.description (e.g. the amount exceeds the account's limit).
			log.warn("Razorpay refused to {}: {} {}", what, e.getStatusCode(), e.getResponseBodyAsString());
			String reason = e.getStatusCode().is4xxClientError() ? describe(e) : null;
			throw new RazorpayException(reason != null ? "The payment gateway refused: " + reason
					: "Online payment is unavailable right now; please try again shortly", e);
		}
		catch (RestClientException e) {
			log.warn("Could not reach Razorpay to {}: {}", what, e.getMessage());
			throw new RazorpayException("Online payment is unavailable right now; please try again shortly", e);
		}
	}

	private static String describe(RestClientResponseException e) {
		try {
			JsonNode error = e.getResponseBodyAs(JsonNode.class);
			return error == null ? null : text(error.path("error"), "description");
		}
		catch (RuntimeException ignored) {
			return null;
		}
	}

}
