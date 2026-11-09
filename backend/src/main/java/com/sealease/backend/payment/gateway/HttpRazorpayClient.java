package com.sealease.backend.payment.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Razorpay's REST API (https://razorpay.com/docs/api/), authenticated with the API key. */
@Component
public class HttpRazorpayClient implements RazorpayClient {

	private static final Logger log = LoggerFactory.getLogger(HttpRazorpayClient.class);


	private final RestClient client;

	/** With the credentials in force on each call, so keys an admin changes apply at once. */
	@Autowired
	public HttpRazorpayClient(RazorpayProperties properties, RazorpayCredentials credentials) {
		this(properties, credentials::current);
	}

	/** With the server's keys only. */
	public HttpRazorpayClient(RazorpayProperties properties) {
		this(properties, () -> new RazorpayKeys(properties.keyId(), properties.keySecret(), properties.webhookSecret(),
				properties.merchantName(), false, false));
	}

	private HttpRazorpayClient(RazorpayProperties properties, Supplier<RazorpayKeys> keys) {
		JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(properties.timeout()).build());
		requests.setReadTimeout(properties.timeout());
		this.client = RestClient.builder()
			.baseUrl(properties.baseUrl().toString())
			.requestFactory(requests)
			.requestInterceptor((request, body, execution) -> {
				RazorpayKeys k = keys.get();
				String basic = Base64.getEncoder().encodeToString(
						(k.keyId() + ":" + k.keySecret()).getBytes(StandardCharsets.UTF_8));
				request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Basic " + basic);
				return execution.execute(request, body);
			})
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
