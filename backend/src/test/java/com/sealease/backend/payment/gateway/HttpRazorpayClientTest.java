package com.sealease.backend.payment.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** How Razorpay's refusals reach investors: a virtual account that cannot be opened is explained plainly. */
class HttpRazorpayClientTest {

	private HttpServer server;

	@AfterEach
	void stop() {
		if (server != null) {
			server.stop(0);
		}
	}

	@Test
	void smartCollectNotActivatedIsExplainedPlainly() throws IOException {
		HttpRazorpayClient client = clientAnswering(400, """
				{"error":{"code":"BAD_REQUEST_ERROR","description":"The requested URL was not found on the server.",
				"source":"internal","step":"NA","reason":"NA","metadata":{}}}""");

		assertThatThrownBy(() -> client.createVirtualAccount(5_000_000, "Order", Instant.now().plusSeconds(3600), Map.of()))
			.isInstanceOf(RazorpayClient.RazorpayException.class)
			.hasMessage("Bank transfer isn't available right now; please pay online");
	}

	@Test
	void otherVirtualAccountFailuresGetTheSameMessage() throws IOException {
		HttpRazorpayClient client = clientAnswering(500, "{}");

		assertThatThrownBy(() -> client.createVirtualAccount(5_000_000, "Order", Instant.now().plusSeconds(3600), Map.of()))
			.hasMessage("Bank transfer isn't available right now; please pay online");
	}

	@Test
	void checkoutRefusalsStillCarryRazorpaysReason() throws IOException {
		HttpRazorpayClient client = clientAnswering(400, """
				{"error":{"code":"BAD_REQUEST_ERROR","description":"Amount exceeds maximum amount allowed."}}""");

		assertThatThrownBy(() -> client.createOrder(5_000_000, "INR", "receipt", Map.of()))
			.hasMessage("The payment gateway refused: Amount exceeds maximum amount allowed.");
	}

	private HttpRazorpayClient clientAnswering(int status, String body) throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(status, bytes.length);
			exchange.getResponseBody().write(bytes);
			exchange.close();
		});
		server.start();
		URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
		RazorpayProperties properties = new RazorpayProperties("rzp_test_key", "secret", "", base, "SeaLease",
				Duration.ofMinutes(30), Duration.ofDays(3), Duration.ofSeconds(5), true);
		assertThat(properties.virtualAccountsEnabled()).isTrue();
		return new HttpRazorpayClient(properties);
	}

}
