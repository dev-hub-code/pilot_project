package com.sealease.backend.order;

import com.jayway.jsonpath.JsonPath;
import com.sealease.backend.order.service.OrderService;
import com.sealease.backend.outbox.OutboxRelay;
import com.sealease.backend.outbox.ProcessedEvents;
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

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cart, checkout, container reservation, payment, allocation, invoices and the outbox. */
@IntegrationTest
class CheckoutIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private OrderService orderService;

	@Autowired
	private OutboxRelay relay;

	@Autowired
	private ProcessedEvents processedEvents;

	@Autowired
	private TransactionTemplate transactions;

	@Autowired
	private ConsumerFactory<String, String> consumers;

	private TestApi api;
	private OfferingFixtures offerings;
	private UUID companyAccount;

	@BeforeEach
	void setUp() throws Exception {
		api = new TestApi(mvc, jdbc);
		offerings = new OfferingFixtures(mvc, api.admin());
		companyAccount = api.companyBankAccount();
	}

	// --------------------------------------------------------------------------------- cart

	@Test
	void cartHoldsContainersOfAPlanAndShowsThePayout() throws Exception {
		UUID product = offerings.plan(2);
		Account investor = approvedInvestor();

		setCartItem(investor, product, 0).andExpect(status().isBadRequest());
		// ₹50,000 a container; 2% rent + 100/16 = 6.25% capital a month = ₹4,125 a month for 16 months.
		setCartItem(investor, product, 2).andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].quantity").value(2))
			.andExpect(jsonPath("$.items[0].pricePerContainer.amount").value("50000.00"))
			.andExpect(jsonPath("$.items[0].monthlyPayout.amount").value("8250.00"))
			.andExpect(jsonPath("$.items[0].tenureMonths").value(16))
			.andExpect(jsonPath("$.items[0].totalPayout.amount").value("132000.00"))
			.andExpect(jsonPath("$.total.amount").value("100000.00"))
			.andExpect(jsonPath("$.checkoutReady").value(true));
		// Setting the same plan again changes the line instead of adding one.
		setCartItem(investor, product, 1).andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.total.amount").value("50000.00"));

		Account unverified = api.register();
		setCartItem(unverified, product, 1).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.message").value(containsString("Verify your identity")));

		mvc.perform(delete("/api/v1/cart/items/{id}", product).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.items.length()").value(0))
			.andExpect(jsonPath("$.checkoutReady").value(false));
	}

	// ----------------------------------------------------------------------------- checkout

	@Test
	void checkoutReservesContainersAndIsIdempotent() throws Exception {
		UUID product = offerings.plan(2);
		Account investor = approvedInvestor();
		setCartItem(investor, product, 2).andExpect(status().isOk());

		mvc.perform(post("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(acceptance(product)))
			.andExpect(status().isBadRequest());

		String key = UUID.randomUUID().toString();
		String orderId = JsonPath.read(checkout(investor, key, acceptance(product))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
			.andExpect(jsonPath("$.orderNumber").value(matchesPattern("ORD-\\d{6,}")))
			.andExpect(jsonPath("$.total.amount").value("100000.00"))
			.andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.items[0].containerType").value(OfferingFixtures.TYPE))
			// The container number is revealed only once the order is paid.
			.andExpect(jsonPath("$.items[0].containerNumber").doesNotExist())
			.andReturn().getResponse().getContentAsString(), "$.id");

		assertThat(containerStatuses(orderId)).containsExactly("RESERVED", "RESERVED");
		mvc.perform(get("/api/v1/cart").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.items.length()").value(0));

		// A retry (lost response) returns the same order; nothing is reserved twice.
		checkout(investor, key, acceptance(product)).andExpect(jsonPath("$.id").value(orderId));
		assertThat(containerStatuses(orderId)).hasSize(2);
		checkout(investor, key, acceptance(UUID.randomUUID())).andExpect(status().isConflict());

		assertThat(api.auditCount("ORDER_PLACED", orderId)).isEqualTo(1);
		assertThat(outboxCount("investment.created", orderId)).isEqualTo(1);
	}

	@Test
	void checkoutRequiresTheTermsOfEveryPlanToBeAccepted() throws Exception {
		UUID product = offerings.plan(1);
		Account investor = approvedInvestor();
		setCartItem(investor, product, 1).andExpect(status().isOk());

		checkout(investor, UUID.randomUUID().toString(), acceptance(UUID.randomUUID()))
			.andExpect(status().isUnprocessableContent());
		assertThat(ordersOf(investor)).isZero();
	}

	@Test
	void checkoutNeedsEnoughContainersInStock() throws Exception {
		// The only test using tank containers, so their stock is known.
		UUID product = offerings.published("TANK_20FT", "80000", "1.5");
		Account investor = approvedInvestor();
		setCartItem(investor, product, 1).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.message").value(containsString("No containers are available")));

		List<UUID> stock = offerings.containers("TANK_20FT", 1);
		setCartItem(investor, product, 2).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.message").value(containsString("Only 1 container(s)")));
		setCartItem(investor, product, 1).andExpect(status().isOk());

		// Sent for repair after it went into the cart: checkout fails and reserves nothing.
		jdbc.update("UPDATE containers SET status = 'MAINTENANCE' WHERE id = ?", stock.getFirst());
		checkout(investor, UUID.randomUUID().toString(), acceptance(product)).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.message").value(containsString("No containers are available")));
		assertThat(ordersOf(investor)).isZero();
	}

	@Test
	void twoInvestorsCannotBuyTheSameLastContainer() throws Exception {
		// The only test using 40ft reefers: exactly one is in stock.
		offerings.containers("REEFER_40FT", 1);
		UUID product = offerings.published("REEFER_40FT", "120000", "2.5");
		Account first = approvedInvestor();
		Account second = approvedInvestor();
		setCartItem(first, product, 1).andExpect(status().isOk());
		setCartItem(second, product, 1).andExpect(status().isOk());

		List<Callable<Integer>> attempts = List.of(
				() -> checkout(first, UUID.randomUUID().toString(), acceptance(product)).andReturn().getResponse().getStatus(),
				() -> checkout(second, UUID.randomUUID().toString(), acceptance(product)).andReturn().getResponse().getStatus());
		List<Integer> statuses = new ArrayList<>();
		try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
			for (Future<Integer> result : pool.invokeAll(attempts)) {
				statuses.add(result.get());
			}
		}
		assertThat(statuses).containsExactlyInAnyOrder(201, 422);
	}

	@Test
	void concurrentCheckoutsOfOneCartPlaceOneOrder() throws Exception {
		UUID product = offerings.plan(1);
		Account investor = approvedInvestor();
		setCartItem(investor, product, 1).andExpect(status().isOk());

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
		assertThat(ordersOf(investor)).isEqualTo(1);
	}

	@Test
	void cancellingAnOrderReturnsItsContainersToStock() throws Exception {
		UUID product = offerings.plan(1);
		Account investor = approvedInvestor();
		String orderId = placeOrder(investor, product, 1);

		Account other = approvedInvestor();
		mvc.perform(post("/api/v1/orders/{id}/cancel", orderId).header(HttpHeaders.AUTHORIZATION, other.bearer()))
			.andExpect(status().isNotFound());
		mvc.perform(post("/api/v1/orders/{id}/cancel", orderId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CANCELLED"));
		assertThat(containerStatuses(orderId)).containsExactly("AVAILABLE");
		startPayment(investor, orderId, "BANK_TRANSFER").andExpect(status().isUnprocessableContent());
	}

	// ---------------------------------------------------------------------- bank payments

	@Test
	void paymentAllocatesTheContainersLeasesThemAndSchedulesMonthlyPayouts() throws Exception {
		UUID product = offerings.plan(2);
		Account investor = approvedInvestor();
		Account finance = api.staff("FINANCE");
		String orderId = placeOrder(investor, product, 2);

		String paymentId = JsonPath.read(startPayment(investor, orderId, "BANK_TRANSFER")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("PENDING"))
			.andExpect(jsonPath("$.bankTransfer.reference").value(matchesPattern("SL-[A-Z2-9]{10}")))
			.andExpect(jsonPath("$.bankTransfer.amount.amount").value("100000.00"))
			.andExpect(jsonPath("$.bankTransfer.accounts[?(@.id == '%s')].ifscCode".formatted(companyAccount))
				.value("HDFC0001234"))
			.andReturn().getResponse().getContentAsString(), "$.id");
		submitDeposit(investor, paymentId).andExpect(status().isOk());

		confirmTransfer(investor, paymentId, "100000").andExpect(status().isForbidden());
		confirmTransfer(finance, paymentId, "99999.99").andExpect(status().isUnprocessableContent());
		confirmTransfer(finance, paymentId, "100000").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SUCCEEDED"))
			.andExpect(jsonPath("$.externalReference").value("BANK-REF-1"));
		confirmTransfer(finance, paymentId, "100000").andExpect(status().isConflict());

		// Each container is now the investor's, by number, and on lease.
		List<String> numbers = jdbc.queryForList("""
				SELECT c.container_number FROM order_items i JOIN containers c ON c.id = i.container_id
				WHERE i.order_id = ?::uuid ORDER BY c.container_number""", String.class, orderId);
		assertThat(containerStatuses(orderId)).containsExactly("ON_LEASE", "ON_LEASE");
		mvc.perform(get("/api/v1/orders/{id}", orderId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.status").value("CONFIRMED"))
			.andExpect(jsonPath("$.items[*].containerNumber", containsInAnyOrder(numbers.toArray())));

		LocalDate today = LocalDate.now(ZoneOffset.UTC);
		mvc.perform(get("/api/v1/portfolio").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.activeHoldings").value(2))
			.andExpect(jsonPath("$.totalsByCurrency[0].amount").value("100000.00"))
			.andExpect(jsonPath("$.holdings[*].container.containerNumber", containsInAnyOrder(numbers.toArray())))
			.andExpect(jsonPath("$.holdings[0].monthlyRent.amount").value("1000.00"))
			.andExpect(jsonPath("$.holdings[0].monthlyCapitalReturn.amount").value("3125.00"))
			.andExpect(jsonPath("$.holdings[0].monthlyPayout.amount").value("4125.00"))
			.andExpect(jsonPath("$.holdings[0].tenureMonths").value(16))
			.andExpect(jsonPath("$.holdings[0].totalPayout.amount").value("66000.00"))
			.andExpect(jsonPath("$.holdings[0].leaseStartsOn").value(today.toString()))
			.andExpect(jsonPath("$.holdings[0].leaseEndsOn").value(today.plusMonths(16).toString()));

		List<LocalDate> schedule = jdbc.queryForList("""
				SELECT p.due_on FROM payout_installments p JOIN holdings h ON h.id = p.holding_id
				WHERE h.order_id = ?::uuid AND p.status = 'SCHEDULED' ORDER BY p.due_on""", LocalDate.class, orderId);
		assertThat(schedule).hasSize(32);
		assertThat(schedule.getFirst()).isEqualTo(today.plusMonths(1));
		assertThat(schedule.getLast()).isEqualTo(today.plusMonths(16));

		String invoice = mvc.perform(get("/api/v1/orders/{id}/invoice", orderId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.invoiceNumber").value(matchesPattern("INV-\\d{4}-\\d{6}")))
			.andExpect(jsonPath("$.total.amount").value("100000.00"))
			.andExpect(jsonPath("$.issuer.name").value("SeaLease Test Ltd"))
			.andExpect(jsonPath("$.buyer.email").value(investor.email()))
			.andExpect(jsonPath("$.lines.length()").value(2))
			.andExpect(jsonPath("$.lines[0].description").value(containsString("16-month lease")))
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
	void cardPaymentsAreNotAccepted() throws Exception {
		UUID product = offerings.plan(1);
		Account investor = approvedInvestor();
		String orderId = placeOrder(investor, product, 1);

		startPayment(investor, orderId, "CARD").andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.message").value(containsString("pay by bank")));
		// The former card gateway callback is gone, and not public.
		mvc.perform(post("/api/v1/payments/webhooks/SIMULATOR").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void staffCannotConfirmPaymentsForTheirOwnOrders() throws Exception {
		UUID product = offerings.plan(1);
		// An investor with an unpaid order who is then hired into finance.
		Account investor = api.register();
		jdbc.update("UPDATE user_profiles SET kyc_status = 'APPROVED' WHERE user_id = ?", investor.id());
		String orderId = placeOrder(investor, product, 1);
		String bank = JsonPath.read(startPayment(investor, orderId, "BANK_TRANSFER")
			.andReturn().getResponse().getContentAsString(), "$.id");
		Account finance = api.becomeStaff(investor, "FINANCE");

		confirmTransfer(finance, bank, "50000").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value(containsString("own order")));
	}

	@Test
	void unpaidOrdersExpireAndReturnTheirContainers() throws Exception {
		UUID product = offerings.plan(1);
		Account investor = approvedInvestor();
		Account finance = api.staff("FINANCE");
		String orderId = placeOrder(investor, product, 1);
		String bank = JsonPath.read(startPayment(investor, orderId, "BANK_TRANSFER")
			.andReturn().getResponse().getContentAsString(), "$.id");

		jdbc.update("UPDATE orders SET expires_at = now() - interval '1 minute' WHERE id = ?::uuid", orderId);
		startPayment(investor, orderId, "BANK_TRANSFER").andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.message").value(containsString("payment window")));
		assertThat(orderService.expireDue()).isGreaterThanOrEqualTo(1);

		mvc.perform(get("/api/v1/orders/{id}", orderId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.status").value("EXPIRED"));
		assertThat(containerStatuses(orderId)).containsExactly("AVAILABLE");
		assertThat(paymentStatus(bank)).isEqualTo("CANCELLED");

		// The transfer arrives anyway: recorded, but the money must go back and nothing is allocated.
		confirmTransfer(finance, bank, "50000").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("REFUND_REQUIRED"));
		assertThat(containerStatuses(orderId)).containsExactly("AVAILABLE");

		refund(investor, bank).andExpect(status().isForbidden());
		refund(finance, bank).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REFUNDED"));
	}

	// ------------------------------------------------------------------------------- outbox

	@Test
	void outboxRelayPublishesCommittedEventsToKafka() throws Exception {
		UUID product = offerings.plan(1);
		Account investor = approvedInvestor();
		String orderId = placeOrder(investor, product, 1);

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

	private String placeOrder(Account investor, UUID product, int containers) throws Exception {
		setCartItem(investor, product, containers).andExpect(status().isOk());
		return JsonPath.read(checkout(investor, UUID.randomUUID().toString(), acceptance(product))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
	}

	private ResultActions setCartItem(Account investor, UUID product, int containers) throws Exception {
		return mvc.perform(put("/api/v1/cart/items/{id}", product).header(HttpHeaders.AUTHORIZATION, investor.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":" + containers + "}"));
	}

	/** Statuses of the containers of the order, in a stable order. */
	private List<String> containerStatuses(String orderId) {
		return jdbc.queryForList("""
				SELECT c.status FROM order_items i JOIN containers c ON c.id = i.container_id
				WHERE i.order_id = ?::uuid ORDER BY c.id""", String.class, orderId);
	}

	private int ordersOf(Account investor) {
		Integer count = jdbc.queryForObject("SELECT count(*) FROM orders WHERE user_id = ?", Integer.class, investor.id());
		return count == null ? 0 : count;
	}

	private static String acceptance(UUID product) {
		return """
				{"acceptedTerms":["%s"]}""".formatted(product);
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

	private ResultActions submitDeposit(Account investor, String paymentId) throws Exception {
		return mvc.perform(post("/api/v1/payments/{id}/deposit", paymentId).header(HttpHeaders.AUTHORIZATION, investor.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"companyBankAccountId":"%s","mode":"ONLINE","reference":"UTR1234567890"}""".formatted(companyAccount)));
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
