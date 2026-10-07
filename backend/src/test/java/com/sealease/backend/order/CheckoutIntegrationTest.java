package com.sealease.backend.order;

import com.jayway.jsonpath.JsonPath;
import com.sealease.backend.common.money.Money;
import com.sealease.backend.investment.service.CapacityService;
import com.sealease.backend.order.service.OrderService;
import com.sealease.backend.outbox.OutboxRelay;
import com.sealease.backend.outbox.ProcessedEvents;
import com.sealease.backend.payment.provider.ProviderEvent;
import com.sealease.backend.payment.provider.SimulatedCardProvider;
import com.sealease.backend.support.IntegrationTest;
import com.sealease.backend.support.OfferingFixtures;
import com.sealease.backend.support.TestApi;
import com.sealease.backend.support.TestApi.Account;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase 5: cart, checkout, payments, confirmation, invoices and the outbox. */
@IntegrationTest
class CheckoutIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private CapacityService capacity;

	@Autowired
	private OrderService orderService;

	@Autowired
	private OutboxRelay relay;

	@Autowired
	private ProcessedEvents processedEvents;

	@Autowired
	private TransactionTemplate transactions;

	@Autowired
	private SimulatedCardProvider gateway;

	@Autowired
	private ConsumerFactory<String, String> consumers;

	private TestApi api;
	private OfferingFixtures offerings;

	@BeforeEach
	void setUp() throws Exception {
		api = new TestApi(mvc, jdbc);
		offerings = new OfferingFixtures(mvc, api.admin());
	}

	// --------------------------------------------------------------------------------- cart

	@Test
	void cartValidatesEveryLineAndShowsTheInvestorsShare() throws Exception {
		UUID product = offerings.retail("50000", "1000", "500");
		Account investor = approvedInvestor();

		setCartItem(investor, product, "1250").andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.message").value(containsString("multiples of 500.00 USD")));
		setCartItem(investor, product, "10000").andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].ownershipPercent").value(20.0))
			.andExpect(jsonPath("$.items[0].rentalPerPayment.amount").value("300.00"))
			.andExpect(jsonPath("$.total.amount").value("10000.00"))
			.andExpect(jsonPath("$.checkoutReady").value(true));
		// Setting the same offering again changes the line instead of adding one.
		setCartItem(investor, product, "5000").andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.total.amount").value("5000.00"));

		Account unverified = api.register();
		setCartItem(unverified, product, "1000").andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.message").value(containsString("Verify your identity")));

		mvc.perform(delete("/api/v1/cart/items/{id}", product).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.items.length()").value(0))
			.andExpect(jsonPath("$.checkoutReady").value(false));
		assertThat(capacity.snapshot(product).reserved().amount()).isEqualTo("0.00");
	}

	// ----------------------------------------------------------------------------- checkout

	@Test
	void checkoutReservesCapacityAndIsIdempotent() throws Exception {
		UUID product = offerings.retail("50000", "1000", "500");
		Account investor = approvedInvestor();
		setCartItem(investor, product, "10000").andExpect(status().isOk());

		mvc.perform(post("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(acceptance(product)))
			.andExpect(status().isBadRequest());

		String key = UUID.randomUUID().toString();
		String orderId = JsonPath.read(checkout(investor, key, acceptance(product))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
			.andExpect(jsonPath("$.orderNumber").value(matchesPattern("ORD-\\d{6,}")))
			.andExpect(jsonPath("$.items[0].ownershipPercent").value(20.0))
			.andExpect(jsonPath("$.items[0].termsVersion").value(OfferingFixtures.TERMS_VERSION))
			.andReturn().getResponse().getContentAsString(), "$.id");

		assertThat(capacity.snapshot(product).reserved().amount()).isEqualTo("10000.00");
		mvc.perform(get("/api/v1/cart").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.items.length()").value(0));

		// A retry (lost response) returns the same order; nothing is reserved twice.
		checkout(investor, key, acceptance(product)).andExpect(jsonPath("$.id").value(orderId));
		assertThat(capacity.snapshot(product).reserved().amount()).isEqualTo("10000.00");
		checkout(investor, key, acceptance(UUID.randomUUID())).andExpect(status().isConflict());

		assertThat(api.auditCount("ORDER_PLACED", orderId)).isEqualTo(1);
		assertThat(outboxCount("investment.created", orderId)).isEqualTo(1);
	}

	@Test
	void checkoutRequiresTheCurrentTermsOfEveryOffering() throws Exception {
		UUID product = offerings.retail("50000", "1000", "500");
		Account investor = approvedInvestor();
		setCartItem(investor, product, "1000").andExpect(status().isOk());

		checkout(investor, UUID.randomUUID().toString(), """
				{"acceptedTerms":[{"productId":"%s","termsVersion":"2025.9"}]}""".formatted(product))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.message").value(containsString("now version 2026.1")));
		checkout(investor, UUID.randomUUID().toString(), acceptance(UUID.randomUUID()))
			.andExpect(status().isUnprocessableContent());
		assertThat(capacity.snapshot(product).reserved().amount()).isEqualTo("0.00");
	}

	@Test
	void concurrentCheckoutsOfOneCartPlaceOneOrder() throws Exception {
		UUID product = offerings.retail("50000", "1000", "500");
		Account investor = approvedInvestor();
		setCartItem(investor, product, "2000").andExpect(status().isOk());

		List<Callable<Integer>> attempts = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			attempts.add(() -> checkout(investor, UUID.randomUUID().toString(), acceptance(product))
				.andReturn().getResponse().getStatus());
		}
		List<Integer> statuses = new ArrayList<>();
		try (ExecutorService pool = Executors.newFixedThreadPool(5)) {
			for (Future<Integer> result : pool.invokeAll(attempts)) {
				statuses.add(result.get());
			}
		}
		assertThat(statuses).containsOnlyOnce(201);
		assertThat(capacity.snapshot(product).reserved().amount()).isEqualTo("2000.00");
	}

	@Test
	void cancellingAnOrderReleasesItsCapacity() throws Exception {
		UUID product = offerings.retail("50000", "1000", "500");
		Account investor = approvedInvestor();
		String orderId = placeOrder(investor, product, "3000");

		Account other = approvedInvestor();
		mvc.perform(post("/api/v1/orders/{id}/cancel", orderId).header(HttpHeaders.AUTHORIZATION, other.bearer()))
			.andExpect(status().isNotFound());
		mvc.perform(post("/api/v1/orders/{id}/cancel", orderId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CANCELLED"));
		assertThat(capacity.snapshot(product).reserved().amount()).isEqualTo("0.00");
		startPayment(investor, orderId, "CARD").andExpect(status().isUnprocessableContent());
	}

	// ---------------------------------------------------------------------- card payments

	@Test
	void cardPaymentConfirmsTheOrderCreatesHoldingsAndIssuesTheInvoice() throws Exception {
		UUID product = offerings.retail("50000", "1000", "500");
		Account investor = approvedInvestor();
		String orderId = placeOrder(investor, product, "10000");

		String paymentId = JsonPath.read(startPayment(investor, orderId, "CARD")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("PENDING"))
			.andExpect(jsonPath("$.simulated").value(true))
			.andReturn().getResponse().getContentAsString(), "$.id");
		simulate(investor, paymentId, "SUCCEEDED").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SUCCEEDED"));

		mvc.perform(get("/api/v1/orders/{id}", orderId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.status").value("CONFIRMED"));
		assertThat(capacity.snapshot(product).committed().amount()).isEqualTo("10000.00");
		assertThat(capacity.snapshot(product).reserved().amount()).isEqualTo("0.00");

		mvc.perform(get("/api/v1/portfolio").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.activeHoldings").value(1))
			.andExpect(jsonPath("$.totalsByCurrency[0].amount").value("10000.00"))
			.andExpect(jsonPath("$.holdings[0].ownershipPercent").value(20.0))
			.andExpect(jsonPath("$.holdings[0].expectedRentalPerPayment.amount").value("300.00"));

		String invoice = mvc.perform(get("/api/v1/orders/{id}/invoice", orderId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.invoiceNumber").value(matchesPattern("INV-\\d{4}-\\d{6}")))
			.andExpect(jsonPath("$.total.amount").value("10000.00"))
			.andExpect(jsonPath("$.issuer.name").value("SeaLease Test Ltd"))
			.andExpect(jsonPath("$.buyer.email").value(investor.email()))
			.andExpect(jsonPath("$.lines[0].description").value(containsString("20% ownership")))
			.andReturn().getResponse().getContentAsString();
		Account stranger = approvedInvestor();
		mvc.perform(get("/api/v1/orders/{id}/invoice", orderId).header(HttpHeaders.AUTHORIZATION, stranger.bearer()))
			.andExpect(status().isNotFound());
		String invoiceId = JsonPath.read(invoice, "$.id");
		assertThatThrownBy(() -> jdbc.update("UPDATE invoices SET total_amount = 1 WHERE id = ?::uuid", invoiceId))
			.hasMessageContaining("append-only");

		assertThat(outboxCount("payment.success", paymentId)).isEqualTo(1);
		assertThat(outboxCount("investment.confirmed", orderId)).isEqualTo(1);
		assertThat(outboxCount("invoice.generated", invoiceId)).isEqualTo(1);
		assertThat(api.auditCount("ORDER_CONFIRMED", orderId)).isEqualTo(1);
	}

	@Test
	void webhooksMustBeSignedAndRedeliveryChangesNothing() throws Exception {
		UUID product = offerings.retail("50000", "1000", "500");
		Account investor = approvedInvestor();
		String orderId = placeOrder(investor, product, "2000");
		String paymentId = JsonPath.read(startPayment(investor, orderId, "CARD")
			.andReturn().getResponse().getContentAsString(), "$.id");
		String reference = jdbc.queryForObject("SELECT provider_reference FROM payments WHERE id = ?::uuid", String.class, paymentId);

		SimulatedCardProvider.SignedEvent delivery = gateway.signedEvent(reference, Money.of("2000", "USD"),
				ProviderEvent.Outcome.SUCCEEDED);
		byte[] tampered = new String(delivery.body(), StandardCharsets.UTF_8).replace("2000", "1").getBytes(StandardCharsets.UTF_8);
		webhook(tampered, delivery.signature()).andExpect(status().isUnauthorized());
		webhook(delivery.body(), null).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/v1/payments/webhooks/unknown").content(delivery.body()))
			.andExpect(status().isNotFound());

		webhook(delivery.body(), delivery.signature()).andExpect(status().isNoContent());
		webhook(delivery.body(), delivery.signature()).andExpect(status().isNoContent());

		Integer holdings = jdbc.queryForObject("SELECT count(*) FROM holdings WHERE order_id = ?::uuid", Integer.class, orderId);
		assertThat(holdings).isEqualTo(1);
		assertThat(capacity.snapshot(product).committed().amount()).isEqualTo("2000.00");
		Integer events = jdbc.queryForObject("SELECT count(*) FROM payment_events WHERE payment_id = ?::uuid", Integer.class, paymentId);
		assertThat(events).isEqualTo(1);
	}

	@Test
	void aDeclinedCardLeavesTheOrderPayable() throws Exception {
		UUID product = offerings.retail("50000", "1000", "500");
		Account investor = approvedInvestor();
		String orderId = placeOrder(investor, product, "2000");
		String first = JsonPath.read(startPayment(investor, orderId, "CARD").andReturn().getResponse().getContentAsString(), "$.id");

		simulate(investor, first, "FAILED").andExpect(jsonPath("$.status").value("FAILED"))
			.andExpect(jsonPath("$.failureReason").value(containsString("declined")));
		mvc.perform(get("/api/v1/orders/{id}", orderId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));

		String second = JsonPath.read(startPayment(investor, orderId, "CARD")
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
		assertThat(second).isNotEqualTo(first);
		simulate(investor, second, "SUCCEEDED").andExpect(jsonPath("$.status").value("SUCCEEDED"));
		assertThat(outboxCount("payment.failed", first)).isEqualTo(1);
	}

	// ---------------------------------------------------------------------- bank transfers

	@Test
	void financeConfirmsBankTransfersAndLateCardMoneyIsFlaggedForRefund() throws Exception {
		UUID product = offerings.retail("50000", "1000", "500");
		Account investor = approvedInvestor();
		Account finance = api.staff("FINANCE");
		String orderId = placeOrder(investor, product, "5000");

		String card = JsonPath.read(startPayment(investor, orderId, "CARD").andReturn().getResponse().getContentAsString(), "$.id");
		String bank = JsonPath.read(startPayment(investor, orderId, "BANK_TRANSFER")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.bankTransfer.reference").value(matchesPattern("SL-[A-Z2-9]{10}")))
			.andExpect(jsonPath("$.bankTransfer.amount.amount").value("5000.00"))
			.andReturn().getResponse().getContentAsString(), "$.id");
		assertThat(paymentStatus(card)).isEqualTo("CANCELLED");

		confirmTransfer(investor, bank, "5000").andExpect(status().isForbidden());
		confirmTransfer(finance, bank, "4999.99").andExpect(status().isUnprocessableContent());
		confirmTransfer(finance, bank, "5000").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SUCCEEDED"))
			.andExpect(jsonPath("$.externalReference").value("BANK-REF-1"));
		confirmTransfer(finance, bank, "5000").andExpect(status().isConflict());
		mvc.perform(get("/api/v1/orders/{id}", orderId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.status").value("CONFIRMED"));

		// The abandoned card payment completes after all: the money is flagged, never kept silently.
		String reference = jdbc.queryForObject("SELECT provider_reference FROM payments WHERE id = ?::uuid", String.class, card);
		SimulatedCardProvider.SignedEvent late = gateway.signedEvent(reference, Money.of("5000", "USD"),
				ProviderEvent.Outcome.SUCCEEDED);
		webhook(late.body(), late.signature()).andExpect(status().isNoContent());
		assertThat(paymentStatus(card)).isEqualTo("REFUND_REQUIRED");
		assertThat(capacity.snapshot(product).committed().amount()).isEqualTo("5000.00");

		refund(investor, card).andExpect(status().isForbidden());
		refund(finance, card).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REFUNDED"));
	}

	@Test
	void staffCannotConfirmPaymentsForTheirOwnOrders() throws Exception {
		UUID product = offerings.retail("50000", "1000", "500");
		Account financeInvestor = api.staff("FINANCE", "INVESTOR");
		jdbc.update("UPDATE user_profiles SET kyc_status = 'APPROVED' WHERE user_id = ?", financeInvestor.id());
		String orderId = placeOrder(financeInvestor, product, "1000");
		String bank = JsonPath.read(startPayment(financeInvestor, orderId, "BANK_TRANSFER")
			.andReturn().getResponse().getContentAsString(), "$.id");

		confirmTransfer(financeInvestor, bank, "1000").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value(containsString("own order")));
	}

	@Test
	void unpaidOrdersExpireAndReleaseCapacity() throws Exception {
		UUID product = offerings.retail("50000", "1000", "500");
		Account investor = approvedInvestor();
		Account finance = api.staff("FINANCE");
		String orderId = placeOrder(investor, product, "4000");
		String bank = JsonPath.read(startPayment(investor, orderId, "BANK_TRANSFER")
			.andReturn().getResponse().getContentAsString(), "$.id");

		jdbc.update("UPDATE orders SET expires_at = now() - interval '1 minute' WHERE id = ?::uuid", orderId);
		startPayment(investor, orderId, "CARD").andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.message").value(containsString("payment window")));
		assertThat(orderService.expireDue()).isGreaterThanOrEqualTo(1);

		mvc.perform(get("/api/v1/orders/{id}", orderId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.status").value("EXPIRED"));
		assertThat(capacity.snapshot(product).reserved().amount()).isEqualTo("0.00");
		assertThat(paymentStatus(bank)).isEqualTo("CANCELLED");

		// The transfer arrives anyway: recorded, but the money must go back.
		confirmTransfer(finance, bank, "4000").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("REFUND_REQUIRED"));
		assertThat(capacity.snapshot(product).committed().amount()).isEqualTo("0.00");
	}

	// ------------------------------------------------------------------------------- outbox

	@Test
	void outboxRelayPublishesCommittedEventsToKafka() throws Exception {
		UUID product = offerings.retail("50000", "1000", "500");
		Account investor = approvedInvestor();
		String orderId = placeOrder(investor, product, "1000");

		while (relay.publishPending() > 0) {
			// drain everything earlier tests left behind as well
		}
		Integer unpublished = jdbc.queryForObject(
				"SELECT count(*) FROM outbox_events WHERE aggregate_id = ? AND published_at IS NULL", Integer.class, orderId);
		assertThat(unpublished).isZero();

		try (Consumer<String, String> consumer = consumers.createConsumer("outbox-test-" + UUID.randomUUID(), null)) {
			TopicPartition partition = new TopicPartition("investment.created", 0);
			consumer.assign(List.of(partition));
			consumer.seekToBeginning(List.of(partition));
			ConsumerRecord<String, String> found = null;
			long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
			while (found == null && System.nanoTime() < deadline) {
				for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
					if (orderId.equals(record.key())) {
						found = record;
					}
				}
			}
			assertThat(found).as("investment.created record for the order").isNotNull();
			assertThat(found.headers().lastHeader("eventId")).isNotNull();
			assertThat((String) JsonPath.read(found.value(), "$.eventType")).isEqualTo("InvestmentOrderPlaced");
			assertThat((String) JsonPath.read(found.value(), "$.data.orderId")).isEqualTo(orderId);
		}
	}

	@Test
	void consumersProcessEachEventOnce() {
		UUID eventId = UUID.randomUUID();
		assertThat(markProcessed("test-consumer", eventId)).isTrue();
		assertThat(markProcessed("test-consumer", eventId)).isFalse();
		assertThat(markProcessed("other-consumer", eventId)).isTrue();
	}

	private boolean markProcessed(String consumer, UUID eventId) {
		return Boolean.TRUE.equals(transactions.execute(s -> processedEvents.markProcessed(consumer, eventId)));
	}

	// ------------------------------------------------------------------------------ helpers

	private Account approvedInvestor() throws Exception {
		Account investor = api.register();
		jdbc.update("UPDATE user_profiles SET kyc_status = 'APPROVED' WHERE user_id = ?", investor.id());
		return investor;
	}

	private String placeOrder(Account investor, UUID product, String amount) throws Exception {
		setCartItem(investor, product, amount).andExpect(status().isOk());
		return JsonPath.read(checkout(investor, UUID.randomUUID().toString(), acceptance(product))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
	}

	private ResultActions setCartItem(Account investor, UUID product, String amount) throws Exception {
		return mvc.perform(put("/api/v1/cart/items/{id}", product).header(HttpHeaders.AUTHORIZATION, investor.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"" + amount + "\"}"));
	}

	private static String acceptance(UUID product) {
		return """
				{"acceptedTerms":[{"productId":"%s","termsVersion":"%s"}]}""".formatted(product, OfferingFixtures.TERMS_VERSION);
	}

	private ResultActions checkout(Account investor, String key, String body) throws Exception {
		return mvc.perform(post("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, investor.bearer())
			.header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions startPayment(Account investor, String orderId, String method) throws Exception {
		return mvc.perform(post("/api/v1/orders/{id}/payments", orderId).header(HttpHeaders.AUTHORIZATION, investor.bearer())
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"" + method + "\"}"));
	}

	private ResultActions simulate(Account investor, String paymentId, String outcome) throws Exception {
		return mvc.perform(post("/api/v1/payments/{id}/simulate", paymentId).header(HttpHeaders.AUTHORIZATION, investor.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"" + outcome + "\"}"));
	}

	private ResultActions webhook(byte[] body, String signature) throws Exception {
		var request = post("/api/v1/payments/webhooks/SIMULATOR").contentType(MediaType.APPLICATION_JSON).content(body);
		return mvc.perform(signature == null ? request : request.header("X-Signature", signature));
	}

	private ResultActions confirmTransfer(Account actor, String paymentId, String amount) throws Exception {
		return mvc.perform(post("/api/v1/admin/payments/{id}/confirm", paymentId).header(HttpHeaders.AUTHORIZATION, actor.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"externalReference\":\"BANK-REF-1\",\"amountReceived\":\"" + amount + "\"}"));
	}

	private ResultActions refund(Account actor, String paymentId) throws Exception {
		return mvc.perform(post("/api/v1/admin/payments/{id}/refund", paymentId).header(HttpHeaders.AUTHORIZATION, actor.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"reference\":\"OUT-1\",\"reason\":\"Order already paid\"}"));
	}

	private String paymentStatus(String paymentId) {
		return jdbc.queryForObject("SELECT status FROM payments WHERE id = ?::uuid", String.class, paymentId);
	}

	private int outboxCount(String topic, String aggregateId) {
		Integer count = jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE topic = ? AND aggregate_id = ?",
				Integer.class, topic, aggregateId);
		return count == null ? 0 : count;
	}

}
