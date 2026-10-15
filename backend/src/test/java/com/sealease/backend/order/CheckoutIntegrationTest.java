package com.sealease.backend.order;

import com.jayway.jsonpath.JsonPath;
import com.sealease.backend.order.service.OrderService;
import com.sealease.backend.outbox.OutboxRelay;
import com.sealease.backend.outbox.ProcessedEvents;
import com.sealease.backend.support.IntegrationTest;
import com.sealease.backend.support.InvestorFixtures;
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

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
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
		UUID product = offerings.plan();
		Account investor = approvedInvestor();

		setCartItem(investor, product, 0).andExpect(status().isBadRequest());
		// ₹50,000 a container; 2% rent = ₹1,000 a month. Plans pay rent only: the price is not paid back.
		// Without a lease chosen, the line takes the plan's shortest: 1 year (of 1 to 5).
		setCartItem(investor, product, 2).andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].quantity").value(2))
			.andExpect(jsonPath("$.items[0].pricePerContainer.amount").value("50000.00"))
			.andExpect(jsonPath("$.items[0].monthlyPayout.amount").value("2000.00"))
			.andExpect(jsonPath("$.items[0].tenureYears").value(1))
			.andExpect(jsonPath("$.items[0].minTenureYears").value(1))
			.andExpect(jsonPath("$.items[0].maxTenureYears").value(5))
			.andExpect(jsonPath("$.items[0].totalPayout.amount").value("24000.00"))
			.andExpect(jsonPath("$.total.amount").value("100000.00"))
			.andExpect(jsonPath("$.checkoutReady").value(true));
		// Setting the same plan again changes the line instead of adding one.
		// The investor chooses the lease, in whole years within the plan's limits; changing the quantity keeps it.
		setCartItem(investor, product, 2, 3).andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].tenureYears").value(3))
			.andExpect(jsonPath("$.items[0].totalPayout.amount").value("72000.00"));
		setCartItem(investor, product, 2, 6).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.message").value(containsString("Choose a lease of 1 to 5 years")));
		setCartItem(investor, product, 1).andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].tenureYears").value(3))
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
	void checkoutReservesNoContainersAndIsIdempotent() throws Exception {
		UUID product = offerings.plan();
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
			// Finance assigns the containers after payment.
			.andExpect(jsonPath("$.items[0].containerNumber").doesNotExist())
			.andReturn().getResponse().getContentAsString(), "$.id");

		assertThat(reservedFor(orderId)).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM order_items WHERE order_id = ?::uuid AND container_id IS NULL",
				Integer.class, orderId)).isEqualTo(2);
		mvc.perform(get("/api/v1/cart").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.items.length()").value(0));

		// A retry (lost response) returns the same order; nothing is placed twice.
		checkout(investor, key, acceptance(product)).andExpect(jsonPath("$.id").value(orderId));
		assertThat(ordersOf(investor)).isEqualTo(1);
		checkout(investor, key, acceptance(UUID.randomUUID())).andExpect(status().isConflict());

		assertThat(api.auditCount("ORDER_PLACED", orderId)).isEqualTo(1);
		assertThat(outboxCount("investment.created", orderId)).isEqualTo(1);
	}

	@Test
	void checkoutRequiresTheTermsOfEveryPlanToBeAccepted() throws Exception {
		UUID product = offerings.plan();
		Account investor = approvedInvestor();
		setCartItem(investor, product, 1).andExpect(status().isOk());

		checkout(investor, UUID.randomUUID().toString(), acceptance(UUID.randomUUID()))
			.andExpect(status().isUnprocessableContent());
		assertThat(ordersOf(investor)).isZero();
	}

	@Test
	void salesAreNotLimitedByStock() throws Exception {
		// The only test using tank containers: there are none in stock, and investors can still buy.
		UUID product = offerings.published("TANK_20FT", "80000", "1.5");
		Account investor = approvedInvestor();
		setCartItem(investor, product, 3).andExpect(status().isOk());
		checkout(investor, UUID.randomUUID().toString(), acceptance(product)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.items.length()").value(3));
		setCartItem(investor, product, 51).andExpect(status().isBadRequest());
	}

	@Test
	void aContainerIsAssignedToOnlyOneInvestmentAtATime() throws Exception {
		UUID product = offerings.published("REEFER_40FT", "120000", "2.5");
		InvestorFixtures investors = new InvestorFixtures(mvc, jdbc, api);
		List<String> holdings = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			Account investor = approvedInvestor();
			String orderId = investors.placeOrder(investor, product, 1);
			payInFull(investor, orderId);
			holdings.add(jdbc.queryForObject("SELECT id::text FROM holdings WHERE order_id = ?::uuid", String.class, orderId));
		}

		// Two finance users type the same new number for two investments at once: one gets it.
		String number = OfferingFixtures.containerNumber();
		Account finance = api.admin();
		List<Callable<Integer>> attempts = holdings.subList(0, 2).stream()
			.map(h -> (Callable<Integer>) () -> assign(finance, h, number).andReturn().getResponse().getStatus())
			.toList();
		List<Integer> statuses = new ArrayList<>();
		try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
			for (Future<Integer> result : pool.invokeAll(attempts)) {
				statuses.add(result.get());
			}
		}
		assertThat(statuses).containsExactlyInAnyOrder(204, 409);
		assertThat(jdbc.queryForList("SELECT status FROM holdings WHERE id IN (?::uuid, ?::uuid) ORDER BY status",
				String.class, holdings.get(0), holdings.get(1))).containsExactly("ACTIVE", "AWAITING_CONTAINER");

		// Once its lease has ended the container is free again, and the same number can be assigned anew.
		assign(finance, holdings.get(2), number).andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value(containsString("already leased")));
		jdbc.update("UPDATE containers SET status = 'AVAILABLE' WHERE container_number = ?", number);
		jdbc.update("""
				UPDATE holdings SET status = 'MATURED', matured_at = now()
				WHERE container_id = (SELECT id FROM containers WHERE container_number = ?)""", number);
		assign(finance, holdings.get(2), number).andExpect(status().isNoContent());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM containers WHERE container_number = ?", Integer.class, number))
			.isEqualTo(1);
	}

	@Test
	void concurrentCheckoutsOfOneCartPlaceOneOrder() throws Exception {
		UUID product = offerings.plan();
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
	void cancellingAnUnpaidOrderLeavesStockUntouched() throws Exception {
		UUID product = offerings.plan();
		Account investor = approvedInvestor();
		String orderId = placeOrder(investor, product, 1);

		Account other = approvedInvestor();
		mvc.perform(post("/api/v1/orders/{id}/cancel", orderId).header(HttpHeaders.AUTHORIZATION, other.bearer()))
			.andExpect(status().isNotFound());
		mvc.perform(post("/api/v1/orders/{id}/cancel", orderId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CANCELLED"));
		assertThat(reservedFor(orderId)).isZero();
		startPayment(investor, orderId, "BANK_TRANSFER").andExpect(status().isUnprocessableContent());
	}

	// ---------------------------------------------------------------------- bank payments

	@Test
	void paidInvestmentsAwaitTheContainersFinanceAssignsWhichStartsTheLeaseAndPayouts() throws Exception {
		UUID product = offerings.plan();
		Account investor = approvedInvestor();
		Account finance = api.staff("FINANCE");
		// Leased for 2 years, as the investor chose.
		String orderId = placeOrder(investor, product, 2, 2);

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

		// Paid: two investments awaiting their containers, with no lease and no payouts yet.
		mvc.perform(get("/api/v1/portfolio").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.activeHoldings").value(0))
			.andExpect(jsonPath("$.totalsByCurrency[0].amount").value("100000.00"))
			.andExpect(jsonPath("$.holdings[*].status", containsInAnyOrder("AWAITING_CONTAINER", "AWAITING_CONTAINER")))
			.andExpect(jsonPath("$.holdings[0].container").doesNotExist())
			.andExpect(jsonPath("$.holdings[0].leaseStartsOn").doesNotExist())
			.andExpect(jsonPath("$.holdings[0].monthlyPayout.amount").value("1000.00"));
		assertThat(leases(orderId)).isEmpty();
		mvc.perform(get("/api/v1/orders/{id}", orderId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.status").value("CONFIRMED"))
			.andExpect(jsonPath("$.items[0].containerNumber").doesNotExist());

		// Finance sees them in the queue with the available containers of the plan's type.
		mvc.perform(get("/api/v1/admin/container-assignments").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isForbidden());
		List<String> holdings = jdbc.queryForList("SELECT id::text FROM holdings WHERE order_id = ?::uuid ORDER BY id",
				String.class, orderId);
		mvc.perform(get("/api/v1/admin/container-assignments?size=100").header(HttpHeaders.AUTHORIZATION, finance.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.pending.content[?(@.orderId == '%s')].investorEmail".formatted(orderId))
				.value(containsInAnyOrder(investor.email(), investor.email())));

		// Finance types each container's number: validated, of the plan's type, and not already leased.
		String first = OfferingFixtures.containerNumber();
		// Any format: recorded tidied, as "YARD7A".
		String second = "yard-7a";
		String flatRack = OfferingFixtures.containerNumber();
		jdbc.update("""
				INSERT INTO containers (id, container_number, container_type, status, created_by, version, created_at, updated_at)
				VALUES (gen_random_uuid(), ?, 'FLAT_RACK_20FT', 'AVAILABLE', ?, 0, now(), now())""", flatRack, finance.id());
		assign(investor, holdings.get(0), first).andExpect(status().isForbidden());
		assign(finance, holdings.get(0), " - ").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(containsString("Enter the container number")));
		assign(finance, holdings.get(0), flatRack).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.message").value(containsString("needs a")));
		mvc.perform(post("/api/v1/admin/container-assignments/{id}", holdings.get(0)).header(HttpHeaders.AUTHORIZATION, finance.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"containerNumber\":\"" + first.toLowerCase() + "\",\"currentLocation\":\"Mundra Port, Gujarat\"}"))
			.andExpect(status().isNoContent());
		assign(finance, holdings.get(1), first).andExpect(status().isConflict());
		assign(finance, holdings.get(0), second).andExpect(status().isConflict());
		assign(finance, holdings.get(1), second).andExpect(status().isNoContent());
		assertThat(jdbc.queryForObject("SELECT status FROM containers WHERE container_number = 'YARD7A'", String.class))
			.isEqualTo("ON_LEASE");

		// Each container is now the investor's, by number, on lease from today with its monthly payouts.
		List<String> numbers = jdbc.queryForList("""
				SELECT c.container_number FROM holdings h JOIN containers c ON c.id = h.container_id
				WHERE h.order_id = ?::uuid AND c.status = 'ON_LEASE' ORDER BY c.container_number""", String.class, orderId);
		assertThat(numbers).hasSize(2);
		mvc.perform(get("/api/v1/orders/{id}", orderId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.items[*].containerNumber", containsInAnyOrder(numbers.toArray())));

		LocalDate today = LocalDate.now(ZoneOffset.UTC);
		mvc.perform(get("/api/v1/portfolio").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.activeHoldings").value(2))
			.andExpect(jsonPath("$.holdings[*].container.containerNumber", containsInAnyOrder(numbers.toArray())))
			.andExpect(jsonPath("$.holdings[*].container.currentLocation", hasItem("Mundra Port, Gujarat")))
			.andExpect(jsonPath("$.holdings[0].monthlyRent.amount").value("1000.00"))
			.andExpect(jsonPath("$.holdings[0].monthlyCapitalReturn.amount").value("0.00"))
			.andExpect(jsonPath("$.holdings[0].monthlyPayout.amount").value("1000.00"))
			.andExpect(jsonPath("$.holdings[0].tenureMonths").value(24))
			.andExpect(jsonPath("$.holdings[0].totalPayout.amount").value("24000.00"))
			.andExpect(jsonPath("$.holdings[0].leaseStartsOn").value(today.toString()))
			.andExpect(jsonPath("$.holdings[0].leaseEndsOn").value(today.plusMonths(24).toString()));

		// One rent accrual per container: the lease from the next full hour for 24 months, ₹1,000 a month.
		List<Map<String, Object>> leases = leases(orderId);
		assertThat(leases).hasSize(2).allSatisfy(lease -> {
			Instant start = ((Timestamp) lease.get("starts_at")).toInstant();
			assertThat(start.getEpochSecond() % 3600).isZero();
			assertThat(((Timestamp) lease.get("ends_at")).toInstant())
				.isEqualTo(start.atZone(ZoneOffset.UTC).plusMonths(24).toInstant());
			assertThat((BigDecimal) lease.get("monthly_rent")).isEqualByComparingTo("1000");
		});
		assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ? AND type = 'CONTAINER_ASSIGNED'",
				Integer.class, investor.id())).isEqualTo(2);
		assertThat(api.auditCount("HOLDING_CONTAINER_ASSIGNED", holdings.get(0))).isEqualTo(1);

		String invoice = mvc.perform(get("/api/v1/orders/{id}/invoice", orderId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.invoiceNumber").value(matchesPattern("INV-\\d{4}-\\d{6}")))
			.andExpect(jsonPath("$.total.amount").value("100000.00"))
			.andExpect(jsonPath("$.issuer.name").value("SeaLease Test Ltd"))
			.andExpect(jsonPath("$.buyer.email").value(investor.email()))
			.andExpect(jsonPath("$.lines.length()").value(2))
			.andExpect(jsonPath("$.lines[0].description").value(containsString("24-month lease")))
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
		UUID product = offerings.plan();
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
		UUID product = offerings.plan();
		// An investor with an unpaid order who is then hired into finance.
		Account investor = api.register();
		jdbc.update("UPDATE user_profiles SET kyc_status = 'APPROVED' WHERE user_id = ?", investor.id());
		new InvestorFixtures(mvc, jdbc, api).ensureVerifiedBankAccount(investor);
		String orderId = placeOrder(investor, product, 1);
		String bank = JsonPath.read(startPayment(investor, orderId, "BANK_TRANSFER")
			.andReturn().getResponse().getContentAsString(), "$.id");
		Account finance = api.becomeStaff(investor, "FINANCE");

		confirmTransfer(finance, bank, "50000").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value(containsString("own order")));
	}

	@Test
	void unpaidOrdersExpireAndLatePaymentsAreRefunded() throws Exception {
		UUID product = offerings.plan();
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
		assertThat(paymentStatus(bank)).isEqualTo("CANCELLED");

		// The transfer arrives anyway: recorded, but the money must go back and no investment is created.
		confirmTransfer(finance, bank, "50000").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("REFUND_REQUIRED"));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM holdings WHERE order_id = ?::uuid", Integer.class, orderId)).isZero();

		refund(investor, bank).andExpect(status().isForbidden());
		refund(finance, bank).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REFUNDED"));
	}

	// ------------------------------------------------------------------------------- outbox

	@Test
	void outboxRelayPublishesCommittedEventsToKafka() throws Exception {
		UUID product = offerings.plan();
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

	/** Identity verified and a verified bank account: able to buy. */
	// ------------------------------------------------------------------------- office cash

	@Test
	void anOrderCanBePaidInCashAtTheOfficeWithinThreeDays() throws Exception {
		UUID product = offerings.plan();
		Account investor = approvedInvestor();
		String orderId = placeOrder(investor, product, 1);

		String paymentId = JsonPath.read(startPayment(investor, orderId, "OFFICE_CASH")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.method").value("OFFICE_CASH"))
			.andExpect(jsonPath("$.status").value("PENDING"))
			.andExpect(jsonPath("$.officeCash.reference").value(matchesPattern("OF-[A-Z2-9]{8}")))
			.andExpect(jsonPath("$.officeCash.amount.amount").value("50000.00"))
			.andExpect(jsonPath("$.officeCash.address").value("1 Test Street, Rotterdam, NL"))
			.andExpect(jsonPath("$.officeCash.payBy").isNotEmpty())
			.andExpect(jsonPath("$.bankTransfer").doesNotExist())
			.andReturn().getResponse().getContentAsString(), "$.id");
		// The order waits three days for the investor to come in, instead of the checkout window.
		java.time.Instant expires = java.time.Instant.parse(JsonPath.read(mvc.perform(get("/api/v1/orders/{id}", orderId)
				.header(HttpHeaders.AUTHORIZATION, investor.bearer())).andReturn().getResponse().getContentAsString(), "$.expiresAt"));
		assertThat(expires).isAfter(java.time.Instant.now().plus(java.time.Duration.ofDays(3)).minusSeconds(120));
		// No bank deposit details for cash.
		submitDeposit(investor, paymentId).andExpect(status().isUnprocessableContent());

		// Finance confirms the cash with the receipt number; the amount must match.
		Account finance = api.staff("FINANCE");
		confirmTransfer(finance, paymentId, "40000").andExpect(status().isUnprocessableContent());
		confirmTransfer(investor, paymentId, "50000").andExpect(status().isForbidden());
		confirmTransfer(finance, paymentId, "50000").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SUCCEEDED"))
			.andExpect(jsonPath("$.officeCash").doesNotExist());
		mvc.perform(get("/api/v1/orders/{id}", orderId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.status").value("CONFIRMED"));
	}

	// --------------------------------------------------------------------------------- GST

	@Test
	void gstIsPaidOnThePriceAndTheInvoiceSplitsItByPlaceOfSupply() throws Exception {
		// ₹50,000 a container plus 18% GST (₹9,000); rent stays 2% of ₹50,000.
		UUID product = offerings.publishedWithGst(OfferingFixtures.TYPE, "50000", "18", "2");
		mvc.perform(get("/api/v1/marketplace/{id}", product).header(HttpHeaders.AUTHORIZATION, approvedInvestor().bearer()))
			.andExpect(jsonPath("$.listing.gstRatePercent").value(18))
			.andExpect(jsonPath("$.listing.priceWithGst.amount").value("59000.00"))
			.andExpect(jsonPath("$.listing.monthlyPayout.amount").value("1000.00"));

		// An investor in the seller's state (Maharashtra): CGST 9% + SGST 9%.
		Account local = approvedInvestor();
		jdbc.update("UPDATE user_profiles SET state_region = 'maharashtra', country = 'IN' WHERE user_id = ?", local.id());
		setCartItem(local, product, 2).andExpect(jsonPath("$.subtotal.amount").value("100000.00"))
			.andExpect(jsonPath("$.gstTotal.amount").value("18000.00"))
			.andExpect(jsonPath("$.total.amount").value("118000.00"))
			.andExpect(jsonPath("$.items[0].gstAmount.amount").value("18000.00"))
			.andExpect(jsonPath("$.items[0].amountWithGst.amount").value("118000.00"));
		String orderId = JsonPath.read(checkout(local, UUID.randomUUID().toString(), acceptance(product))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.total.amount").value("118000.00"))
			.andExpect(jsonPath("$.items[0].amount.amount").value("50000.00"))
			.andExpect(jsonPath("$.items[0].gstAmount.amount").value("9000.00"))
			.andExpect(jsonPath("$.items[0].amountWithGst.amount").value("59000.00"))
			.andReturn().getResponse().getContentAsString(), "$.id");
		pay(local, orderId, "118000");
		mvc.perform(get("/api/v1/orders/{id}/invoice", orderId).header(HttpHeaders.AUTHORIZATION, local.bearer()))
			.andExpect(jsonPath("$.taxable.amount").value("100000.00"))
			.andExpect(jsonPath("$.cgst.amount").value("9000.00"))
			.andExpect(jsonPath("$.sgst.amount").value("9000.00"))
			.andExpect(jsonPath("$.igst.amount").value("0.00"))
			.andExpect(jsonPath("$.placeOfSupply").value("Maharashtra (27)"))
			.andExpect(jsonPath("$.total.amount").value("118000.00"))
			.andExpect(jsonPath("$.lines[0].gstRatePercent").value(18))
			.andExpect(jsonPath("$.lines[0].cgst.amount").value("4500.00"))
			.andExpect(jsonPath("$.lines[0].total.amount").value("59000.00"));
		// Rent and the investment are on the price without GST.
		mvc.perform(get("/api/v1/portfolio").header(HttpHeaders.AUTHORIZATION, local.bearer()))
			.andExpect(jsonPath("$.totalsByCurrency[0].amount").value("100000.00"))
			.andExpect(jsonPath("$.holdings[0].monthlyRent.amount").value("1000.00"));

		// An investor registered in another state (from their GSTIN, Karnataka): IGST 18%.
		Account other = approvedInvestor();
		jdbc.update("UPDATE user_profiles SET gst_number = '29ABCDE1234F1Z5', state_region = 'Maharashtra' WHERE user_id = ?",
				other.id());
		setCartItem(other, product, 1).andExpect(status().isOk());
		String otherOrder = JsonPath.read(checkout(other, UUID.randomUUID().toString(), acceptance(product))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
		pay(other, otherOrder, "59000");
		mvc.perform(get("/api/v1/orders/{id}/invoice", otherOrder).header(HttpHeaders.AUTHORIZATION, other.bearer()))
			.andExpect(jsonPath("$.igst.amount").value("9000.00"))
			.andExpect(jsonPath("$.cgst.amount").value("0.00"))
			.andExpect(jsonPath("$.placeOfSupply").value("Karnataka (29)"))
			.andExpect(jsonPath("$.buyer.taxId").value("29ABCDE1234F1Z5"))
			.andExpect(jsonPath("$.total.amount").value("59000.00"));
	}

	@Test
	void cashPaidAtTheOfficeIsWithoutGstAndEarnsHalfAPercentMoreRent() throws Exception {
		// ₹50,000 a container plus 18% GST; the plan pays 2% rent a month.
		UUID product = offerings.publishedWithGst(OfferingFixtures.TYPE, "50000", "18", "2");
		Account investor = approvedInvestor();
		jdbc.update("UPDATE user_profiles SET state_region = 'maharashtra', country = 'IN' WHERE user_id = ?", investor.id());
		String orderId = placeOrder(investor, product, 1);

		// By bank the investor pays GST and earns the plan's rent.
		startPayment(investor, orderId, "BANK_TRANSFER").andExpect(status().isCreated())
			.andExpect(jsonPath("$.gstIncluded").value(true))
			.andExpect(jsonPath("$.rentBonusPercent").value(0))
			.andExpect(jsonPath("$.bankTransfer.amount.amount").value("59000.00"));
		// At the office: the price alone, and 0.50% more rent.
		String paymentId = JsonPath.read(startPayment(investor, orderId, "OFFICE_CASH")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.gstIncluded").value(false))
			.andExpect(jsonPath("$.rentBonusPercent").value(0.5))
			.andExpect(jsonPath("$.officeCash.amount.amount").value("50000.00"))
			.andReturn().getResponse().getContentAsString(), "$.id");

		Account finance = api.staff("FINANCE");
		confirmTransfer(finance, paymentId, "59000").andExpect(status().isUnprocessableContent());
		confirmTransfer(finance, paymentId, "50000").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SUCCEEDED"));

		mvc.perform(get("/api/v1/orders/{id}", orderId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.status").value("CONFIRMED"))
			.andExpect(jsonPath("$.gstWaived").value(true));
		mvc.perform(get("/api/v1/orders/{id}/invoice", orderId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.taxable.amount").value("50000.00"))
			.andExpect(jsonPath("$.cgst.amount").value("0.00"))
			.andExpect(jsonPath("$.sgst.amount").value("0.00"))
			.andExpect(jsonPath("$.total.amount").value("50000.00"));
		// 2.5% of ₹50,000.
		mvc.perform(get("/api/v1/portfolio").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.holdings[0].monthlyRent.amount").value("1250.00"));
	}

	@Test
	void anOfficePaymentStartedOnEarlierTermsIsReplacedOnTheCurrentOnes() throws Exception {
		UUID product = offerings.publishedWithGst(OfferingFixtures.TYPE, "50000", "18", "2");
		Account investor = approvedInvestor();
		String orderId = placeOrder(investor, product, 1);
		String first = JsonPath.read(startPayment(investor, orderId, "OFFICE_CASH")
			.andReturn().getResponse().getContentAsString(), "$.id");
		// As if started before cash at the office was without GST.
		jdbc.update("UPDATE payments SET gst_included = TRUE, rent_bonus_percent = 0, amount = 59000 WHERE id = ?::uuid",
				first);
		String second = JsonPath.read(startPayment(investor, orderId, "OFFICE_CASH")
			.andExpect(jsonPath("$.officeCash.amount.amount").value("50000.00"))
			.andReturn().getResponse().getContentAsString(), "$.id");
		assertThat(second).isNotEqualTo(first);
		assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE id = ?::uuid", String.class, first))
			.isEqualTo("CANCELLED");
	}

	@Test
	void cashAtTheOfficeOnceChosenStaysAndThePaymentItReplacedIsNotConfirmed() throws Exception {
		UUID product = offerings.publishedWithGst(OfferingFixtures.TYPE, "50000", "18", "2");
		Account investor = approvedInvestor();
		String orderId = placeOrder(investor, product, 1);
		String bank = JsonPath.read(startPayment(investor, orderId, "BANK_TRANSFER")
			.andReturn().getResponse().getContentAsString(), "$.id");
		// The investor chooses cash at the office instead: the bank payment is cancelled, saying why.
		String cash = JsonPath.read(startPayment(investor, orderId, "OFFICE_CASH")
			.andReturn().getResponse().getContentAsString(), "$.id");
		assertThat(jdbc.queryForObject("SELECT failure_reason FROM payments WHERE id = ?::uuid", String.class, bank))
			.isEqualTo("Replaced by a cash payment at the office");

		// Cash, once chosen, stays: going back to the bank is refused.
		startPayment(investor, orderId, "BANK_TRANSFER").andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("cancel this order")));
		assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE id = ?::uuid", String.class, cash))
			.isEqualTo("PENDING");

		// Finance cannot confirm the replaced bank payment while the cash one is pending; the cash one confirms.
		Account finance = api.staff("FINANCE");
		confirmTransfer(finance, bank, "59000").andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("confirm that one instead")));
		confirmTransfer(finance, cash, "50000").andExpect(status().isOk());
		assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id = ?::uuid", String.class, orderId))
			.isEqualTo("CONFIRMED");
	}

	/** Pays the order by bank deposit, confirmed by finance for {@code amount}. */
	private void pay(Account investor, String orderId, String amount) throws Exception {
		String paymentId = JsonPath.read(startPayment(investor, orderId, "BANK_TRANSFER")
			.andExpect(jsonPath("$.bankTransfer.amount.amount").value(new java.math.BigDecimal(amount).setScale(2).toPlainString()))
			.andReturn().getResponse().getContentAsString(), "$.id");
		submitDeposit(investor, paymentId).andExpect(status().isOk());
		confirmTransfer(api.staff("FINANCE"), paymentId, amount).andExpect(status().isOk());
	}

	private Account approvedInvestor() throws Exception {
		Account investor = api.register();
		jdbc.update("UPDATE user_profiles SET kyc_status = 'APPROVED' WHERE user_id = ?", investor.id());
		new InvestorFixtures(mvc, jdbc, api).ensureVerifiedBankAccount(investor);
		return investor;
	}

	private String placeOrder(Account investor, UUID product, int containers) throws Exception {
		return placeOrder(investor, product, containers, 1);
	}

	private String placeOrder(Account investor, UUID product, int containers, int tenureYears) throws Exception {
		setCartItem(investor, product, containers, tenureYears).andExpect(status().isOk());
		return JsonPath.read(checkout(investor, UUID.randomUUID().toString(), acceptance(product))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
	}

	private ResultActions setCartItem(Account investor, UUID product, int containers) throws Exception {
		return mvc.perform(put("/api/v1/cart/items/{id}", product).header(HttpHeaders.AUTHORIZATION, investor.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":" + containers + "}"));
	}

	private ResultActions setCartItem(Account investor, UUID product, int containers, int tenureYears) throws Exception {
		return mvc.perform(put("/api/v1/cart/items/{id}", product).header(HttpHeaders.AUTHORIZATION, investor.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"quantity\":" + containers + ",\"tenureYears\":" + tenureYears + "}"));
	}

	/** Containers reserved for the order (only orders placed before manual assignment reserved any). */
	private int reservedFor(String orderId) {
		Integer count = jdbc.queryForObject("SELECT count(*) FROM containers WHERE reserved_order_id = ?::uuid", Integer.class, orderId);
		return count == null ? 0 : count;
	}

	/** The rent accruals of the order's holdings: their leases. */
	private List<Map<String, Object>> leases(String orderId) {
		return jdbc.queryForList("""
				SELECT a.starts_at, a.ends_at, a.monthly_rent FROM rent_accruals a JOIN holdings h ON h.id = a.holding_id
				WHERE h.order_id = ?::uuid""", orderId);
	}

	private ResultActions assign(Account actor, String holdingId, String containerNumber) throws Exception {
		return mvc.perform(post("/api/v1/admin/container-assignments/{id}", holdingId).header(HttpHeaders.AUTHORIZATION, actor.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"containerNumber\":\"" + containerNumber + "\"}"));
	}

	/** The order's owner pays by bank and the admin confirms the full amount. */
	private void payInFull(Account investor, String orderId) throws Exception {
		String amount = jdbc.queryForObject("SELECT round(total_amount, 2)::text FROM orders WHERE id = ?::uuid", String.class, orderId);
		String paymentId = JsonPath.read(startPayment(investor, orderId, "BANK_TRANSFER")
			.andReturn().getResponse().getContentAsString(), "$.id");
		submitDeposit(investor, paymentId).andExpect(status().isOk());
		confirmTransfer(api.admin(), paymentId, amount).andExpect(status().isOk());
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
