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
import org.junit.jupiter.api.AfterEach;
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
import static org.hamcrest.Matchers.contains;
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

	@Test
	void ordersBelowThePlansMinimumUnitsCannotBePlaced() throws Exception {
		UUID product = offerings.published(OfferingFixtures.TYPE, "3", 3, 10);
		Account investor = approvedInvestor();
		checkout(investor, UUID.randomUUID().toString(), buy(product, 9)).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.message").value(containsString("Buy at least 10 units")));
		// 10 × ₹500 = ₹5,000 at the plan's 3% a month for 3 years.
		String orderId = JsonPath.read(checkout(investor, UUID.randomUUID().toString(), buy(product, 10))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.total.amount").value("5000.00"))
			.andExpect(jsonPath("$.items[0].units").value(10))
			.andExpect(jsonPath("$.items[0].unitPrice.amount").value("500.00"))
			.andExpect(jsonPath("$.items[0].monthlyRentPercent").value(3))
			.andExpect(jsonPath("$.items[0].tenureMonths").value(36))
			.andReturn().getResponse().getContentAsString(), "$.id");
		assertThat(api.auditCount("ORDER_PLACED", orderId)).isEqualTo(1);
	}

	@Test
	void buyingNeedsAWholeUnitAndVerifiedIdentity() throws Exception {
		UUID product = offerings.plan();
		checkout(approvedInvestor(), UUID.randomUUID().toString(), purchase(product, 0)).andExpect(status().isBadRequest());
		checkout(api.register(), UUID.randomUUID().toString(), purchase(product, 1)).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.message").value(containsString("Verify your identity")));
	}

	// ----------------------------------------------------------------------------- checkout

	@Test
	void checkoutReservesNoContainersAndIsIdempotent() throws Exception {
		UUID product = offerings.plan();
		Account investor = approvedInvestor();

		mvc.perform(post("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(purchase(product, 2)))
			.andExpect(status().isBadRequest());

		String key = UUID.randomUUID().toString();
		String orderId = JsonPath.read(checkout(investor, key, purchase(product, 2))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
			.andExpect(jsonPath("$.orderNumber").value(matchesPattern("ORD-\\d{6,}")))
			.andExpect(jsonPath("$.total.amount").value("100000.00"))
			.andExpect(jsonPath("$.items.length()").value(1))
			.andReturn().getResponse().getContentAsString(), "$.id");

		assertThat(reservedFor(orderId)).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM order_items WHERE order_id = ?::uuid AND container_id IS NULL",
				Integer.class, orderId)).isEqualTo(1);

		// A retry (lost response) returns the same order; nothing is placed twice.
		checkout(investor, key, purchase(product, 2)).andExpect(jsonPath("$.id").value(orderId));
		assertThat(ordersOf(investor)).isEqualTo(1);
		checkout(investor, key, purchase(product, 3)).andExpect(status().isConflict());

		assertThat(api.auditCount("ORDER_PLACED", orderId)).isEqualTo(1);
		assertThat(outboxCount("investment.created", orderId)).isEqualTo(1);
	}

	@Test
	void checkoutRequiresThePlansTermsToBeAccepted() throws Exception {
		UUID product = offerings.plan();
		Account investor = approvedInvestor();
		checkout(investor, UUID.randomUUID().toString(), """
				{"productId":"%s","units":1,"acceptTerms":false}""".formatted(product))
			.andExpect(status().isBadRequest());
		assertThat(ordersOf(investor)).isZero();
	}

	@Test
	void salesAreNotLimitedByStock() throws Exception {
		UUID product = offerings.plan();
		Account investor = approvedInvestor();
		checkout(investor, UUID.randomUUID().toString(), purchase(product, 3)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.items.length()").value(1));
	}

	@Test
	void concurrentRetriesOfOnePurchasePlaceOneOrder() throws Exception {
		UUID product = offerings.plan();
		Account investor = approvedInvestor();
		String key = UUID.randomUUID().toString();

		List<Callable<Integer>> attempts = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			attempts.add(() -> checkout(investor, key, purchase(product, 1)).andReturn().getResponse().getStatus());
		}
		List<Integer> statuses = new ArrayList<>();
		try (ExecutorService pool = Executors.newFixedThreadPool(5)) {
			for (Future<Integer> result : pool.invokeAll(attempts)) {
				statuses.add(result.get());
			}
		}
		assertThat(statuses).doesNotContain(500, 409);
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
		UUID product = offerings.published(OfferingFixtures.TYPE, "2", 2);
		Account investor = approvedInvestor();
		Account finance = api.staff("ADMIN", "FINANCE");
		// A two-year plan.
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

		// Paid: the investment awaits its agreement, with no term and no payouts yet.
		mvc.perform(get("/api/v1/portfolio").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.activeHoldings").value(0))
			.andExpect(jsonPath("$.totalsByCurrency[0].amount").value("100000.00"))
			.andExpect(jsonPath("$.holdings[*].status", contains("AWAITING_ACCEPTANCE")))
			.andExpect(jsonPath("$.holdings[0].leaseStartsOn").doesNotExist())
			.andExpect(jsonPath("$.holdings[0].monthlyPayout.amount").value("2000.00"));
		assertThat(leases(orderId)).isEmpty();
		mvc.perform(get("/api/v1/orders/{id}", orderId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.status").value("CONFIRMED"));
		List<String> holdings = jdbc.queryForList("SELECT id::text FROM holdings WHERE order_id = ?::uuid ORDER BY id",
				String.class, orderId);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM lease_agreements WHERE order_id = ?::uuid", Integer.class, orderId))
			.isEqualTo(1);
		for (String holding : holdings) {
			new InvestorFixtures(mvc, jdbc, api).acceptAgreement(investor, UUID.fromString(holding));
		}

		LocalDate today = LocalDate.now(ZoneOffset.UTC);
		mvc.perform(get("/api/v1/portfolio").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.activeHoldings").value(1))
			.andExpect(jsonPath("$.holdings[0].monthlyRent.amount").value("2000.00"))
			.andExpect(jsonPath("$.holdings[0].monthlyCapitalReturn.amount").value("0.00"))
			.andExpect(jsonPath("$.holdings[0].monthlyPayout.amount").value("2000.00"))
			.andExpect(jsonPath("$.holdings[0].tenureMonths").value(24))
			.andExpect(jsonPath("$.holdings[0].totalPayout.amount").value("48000.00"))
			.andExpect(jsonPath("$.holdings[0].leaseStartsOn").value(today.toString()))
			.andExpect(jsonPath("$.holdings[0].leaseEndsOn").value(today.plusMonths(24).toString()));

		// One accrual: the term from the next full hour for 24 months, ₹2,000 a month.
		List<Map<String, Object>> leases = leases(orderId);
		assertThat(leases).hasSize(1).allSatisfy(lease -> {
			Instant start = ((Timestamp) lease.get("starts_at")).toInstant();
			assertThat(start.getEpochSecond() % 3600).isZero();
			assertThat(((Timestamp) lease.get("ends_at")).toInstant())
				.isEqualTo(start.atZone(ZoneOffset.UTC).plusMonths(24).toInstant());
			assertThat((BigDecimal) lease.get("monthly_rent")).isEqualByComparingTo("2000");
		});
		assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ? AND type = 'AGREEMENT_ISSUED'",
				Integer.class, investor.id())).isEqualTo(1);

		String invoice = mvc.perform(get("/api/v1/orders/{id}/invoice", orderId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.invoiceNumber").value(matchesPattern("INV-\\d{4}-\\d{6}")))
			.andExpect(jsonPath("$.total.amount").value("100000.00"))
			.andExpect(jsonPath("$.issuer.name").value("SeaLease Test Ltd"))
			.andExpect(jsonPath("$.buyer.email").value(investor.email()))
			.andExpect(jsonPath("$.lines.length()").value(1))
			.andExpect(jsonPath("$.lines[0].description").value(containsString("24-month investment")))
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
		Account finance = api.becomeStaff(investor, "ADMIN", "FINANCE");

		confirmTransfer(finance, bank, "50000").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value(containsString("own order")));
	}

	@Test
	void unpaidOrdersExpireAndLatePaymentsAreRefunded() throws Exception {
		UUID product = offerings.plan();
		Account investor = approvedInvestor();
		Account finance = api.staff("ADMIN", "FINANCE");
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
		refund(api.admin(), bank).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REFUNDED"));
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
	void onlineInvestorsCannotPayInCash() throws Exception {
		Account investor = approvedInvestor();
		String orderId = placeOrder(investor, offerings.plan(), 1);

		// Without the gateway set up, online investors pay by bank transfer only.
		mvc.perform(get("/api/v1/payments/methods").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.methods", contains("BANK_TRANSFER")));
		startPayment(investor, orderId, "OFFICE_CASH").andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.message", containsString("Cash payment is not available")));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM payments WHERE order_id = ?::uuid", Integer.class, orderId))
			.isZero();
	}

	@Test
	void anOrderCanBePaidInCashAtTheOfficeWithinThreeDays() throws Exception {
		UUID product = offerings.networkPlan();
		Account investor = networkInvestor();
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
		Account finance = api.staff("ADMIN", "FINANCE");
		confirmTransfer(finance, paymentId, "40000").andExpect(status().isUnprocessableContent());
		confirmTransfer(investor, paymentId, "50000").andExpect(status().isForbidden());
		confirmTransfer(finance, paymentId, "50000").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SUCCEEDED"))
			.andExpect(jsonPath("$.officeCash").doesNotExist());
		mvc.perform(get("/api/v1/orders/{id}", orderId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.status").value("CONFIRMED"));
	}

	// --------------------------------------------------------------------------------- price and invoice

	@Test
	void theInvestorPaysThePriceAndTheInvoiceListsIt() throws Exception {
		// ₹50,000 a container; rent is 2% of it.
		UUID product = offerings.plan();
		Account investor = approvedInvestor();
		String orderId = JsonPath.read(checkout(investor, UUID.randomUUID().toString(), purchase(product, 2))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.total.amount").value("100000.00"))
			.andExpect(jsonPath("$.items[0].amount.amount").value("100000.00"))
			.andReturn().getResponse().getContentAsString(), "$.id");
		pay(investor, orderId, "100000");
		mvc.perform(get("/api/v1/orders/{id}/invoice", orderId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.total.amount").value("100000.00"))
			.andExpect(jsonPath("$.lines.length()").value(1))
			.andExpect(jsonPath("$.lines[0].amount.amount").value("100000.00"));
		mvc.perform(get("/api/v1/portfolio").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.totalsByCurrency[0].amount").value("100000.00"))
			.andExpect(jsonPath("$.holdings[0].monthlyRent.amount").value("2000.00"));
	}

	@Test
	void cashPaidAtTheOfficeIsThePriceAndEarnsThePlansRent() throws Exception {
		// ₹50,000 a container; the plan pays 2% rent a month.
		UUID product = offerings.networkPlan();
		Account investor = networkInvestor();
		String orderId = placeOrder(investor, product, 1);

		// By bank and at the office the investor pays the same price.
		startPayment(investor, orderId, "BANK_TRANSFER").andExpect(status().isCreated())
			.andExpect(jsonPath("$.bankTransfer.amount.amount").value("50000.00"));
		String paymentId = JsonPath.read(startPayment(investor, orderId, "OFFICE_CASH")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.officeCash.amount.amount").value("50000.00"))
			.andReturn().getResponse().getContentAsString(), "$.id");

		Account finance = api.staff("ADMIN", "FINANCE");
		confirmTransfer(finance, paymentId, "59000").andExpect(status().isUnprocessableContent());
		confirmTransfer(finance, paymentId, "50000").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SUCCEEDED"));

		mvc.perform(get("/api/v1/orders/{id}", orderId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.status").value("CONFIRMED"));
		mvc.perform(get("/api/v1/orders/{id}/invoice", orderId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.total.amount").value("50000.00"));
		// The plan's 2% of ₹50,000: paying in cash earns no extra rent.
		mvc.perform(get("/api/v1/portfolio").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.holdings[0].monthlyRent.amount").value("1000.00"));
	}

	@Test
	void cashAtTheOfficeOnceChosenStaysAndThePaymentItReplacedIsNotConfirmed() throws Exception {
		UUID product = offerings.networkPlan();
		Account investor = networkInvestor();
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
		Account finance = api.staff("ADMIN", "FINANCE");
		confirmTransfer(finance, bank, "50000").andExpect(status().isConflict())
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
		confirmTransfer(api.staff("ADMIN", "FINANCE"), paymentId, amount).andExpect(status().isOk());
	}

	private Account approvedInvestor() throws Exception {
		Account investor = api.register();
		jdbc.update("UPDATE user_profiles SET kyc_status = 'APPROVED' WHERE user_id = ?", investor.id());
		new InvestorFixtures(mvc, jdbc, api).ensureVerifiedBankAccount(investor);
		return investor;
	}

	/** Approved and on the network scheme: network investors may pay in cash at the office; online investors may not. */
	private Account networkInvestor() throws Exception {
		Account investor = approvedInvestor();
		new InvestorFixtures(mvc, jdbc, api).enrolInNetwork(investor);
		return investor;
	}

	private String placeOrder(Account investor, UUID product, int units) throws Exception {
		return JsonPath.read(checkout(investor, UUID.randomUUID().toString(), purchase(product, units))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
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

	/** Buys lots of ₹50,000. */
	private static String purchase(UUID product, int lots) {
		return buy(product, lots * OfferingFixtures.LOT);
	}

	private static String buy(UUID product, int units) {
		return """
				{"productId":"%s","units":%d,"acceptTerms":true}""".formatted(product, units);
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
		api.takePayment(paymentId, actor);
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
