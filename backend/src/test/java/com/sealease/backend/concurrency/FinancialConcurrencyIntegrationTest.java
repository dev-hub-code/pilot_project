package com.sealease.backend.concurrency;

import com.jayway.jsonpath.JsonPath;
import com.sealease.backend.earning.service.PayoutService;
import com.sealease.backend.order.service.OrderService;
import com.sealease.backend.payment.gateway.RazorpayClient;
import com.sealease.backend.payment.gateway.RazorpayClient.RazorpayException;
import com.sealease.backend.payment.gateway.RazorpayClient.VirtualAccount;
import com.sealease.backend.payment.gateway.RazorpaySignatures;
import com.sealease.backend.payment.provider.PaymentProvider.VirtualAccountDetails;
import com.sealease.backend.support.IntegrationTest;
import com.sealease.backend.support.InvestorFixtures;
import com.sealease.backend.support.OfferingFixtures;
import com.sealease.backend.support.TestApi;
import com.sealease.backend.support.TestApi.Account;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.ledger.service.AccountType;
import com.sealease.backend.ledger.service.EarningsBalance;
import com.sealease.backend.ledger.service.LedgerService;
import com.sealease.backend.ledger.service.Posting;
import com.sealease.backend.ledger.service.TransactionType;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Concurrency audit: many simultaneous requests against real PostgreSQL, checking that money is never spent, credited,
 * paid or refunded twice and no balance goes negative. Each race prints its timings, response codes, the connection
 * pool's peak use, the most sessions seen waiting on a lock, and new deadlocks ({@code [concurrency-audit]} lines).
 *
 * <p>Uses the same properties and Razorpay mock as {@code GatewayPaymentIntegrationTest}, so the Spring context is
 * shared rather than started again.
 */
@IntegrationTest
@TestPropertySource(properties = {
		"app.payments.razorpay.key-id=rzp_test_key",
		"app.payments.razorpay.key-secret=test-key-secret",
		"app.payments.razorpay.webhook-secret=test-webhook-secret" })
class FinancialConcurrencyIntegrationTest {

	private static final String RAZORPAY_WEBHOOK_SECRET = "test-webhook-secret";
	private static final String PAYOUT_WEBHOOK_SECRET = "test-payout-webhook-secret";
	/** 100 units of the fixture plan. */
	private static final long ORDER_PAISE = 5_000_000;

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PayoutService payouts;

	@Autowired
	private OrderService orders;

	@Autowired
	private LedgerService ledger;

	@Autowired
	private TransactionTemplate transactions;

	@MockitoBean
	private RazorpayClient razorpay;

	private TestApi api;
	private InvestorFixtures investors;
	private OfferingFixtures offerings;
	private Account approver;
	private Account executive;

	@BeforeEach
	void setUp() throws Exception {
		api = new TestApi(mvc, jdbc);
		investors = new InvestorFixtures(mvc, jdbc, api);
		offerings = new OfferingFixtures(mvc, api.admin());
		api.companyBankAccount();
		approver = api.staff("FINANCE_APPROVER");
		executive = api.staff("WITHDRAWAL_EXECUTIVE");
	}

	@AfterEach
	void resetSettings() {
		jdbc.update("""
				UPDATE withdrawal_settings SET fee_flat = 0, fee_percent = 0, tax_percent = 0,
				       dual_approval_threshold = NULL WHERE id = 1""");
	}

	// ------------------------------------------------------------------------------------------- withdrawals

	@Test
	void hundredSimultaneousWithdrawalRequestsSpendTheWalletOnce() throws Exception {
		Account investor = withBank();
		credit(investor, "1000", "RENT_BANK");

		Race race = race("100 withdrawal requests, one wallet, distinct keys", 100,
				i -> withdraw(investor, "400", key()).andReturn().getResponse().getStatus());

		assertThat(race.count(201)).as("exactly one request reserves the money").isEqualTo(1);
		assertThat(withdrawalsOf(investor)).isEqualTo(1);
		assertThat(wallet(investor, "INVESTOR_EARNINGS")).isEqualByComparingTo("600");
		assertThat(reservesOf(investor)).as("one WITHDRAWAL_RESERVE posting").isEqualTo(1);
		assertNoNegativeInvestorBalance();
	}

	/**
	 * Audit finding H-2: each request holds one pooled connection and opens a second for the password check
	 * (REQUIRES_NEW), so 100 at once exhaust the pool of 20 and most fail after the 10 s connection timeout.
	 */
	@Test
	void aBurstOfWithdrawalRequestsIsAnsweredWithoutServerErrors() throws Exception {
		Account investor = withBank();
		credit(investor, "1000", "RENT_BANK");
		Race race = race("100 withdrawal requests, no server errors", 100,
				i -> withdraw(investor, "400", key()).andReturn().getResponse().getStatus());
		assertThat(race.count(500)).isZero();
		assertThat(race.failures()).isZero();
	}

	@Test
	void hundredRetriesWithOneIdempotencyKeyMakeOneWithdrawal() throws Exception {
		Account investor = withBank();
		credit(investor, "1000", "RENT_BANK");
		String key = key();

		List<String> ids = Collections.synchronizedList(new ArrayList<>());
		Race race = race("100 retries of one withdrawal (same key)", 100, i -> {
			var response = withdraw(investor, "400", key).andReturn().getResponse();
			if (response.getStatus() == 201) {
				ids.add(JsonPath.read(response.getContentAsString(), "$.id"));
			}
			return response.getStatus();
		});

		assertThat(ids).as("every successful retry names the same withdrawal").containsOnly(ids.getFirst());
		assertThat(race.count(201)).isGreaterThanOrEqualTo(1);
		assertThat(withdrawalsOf(investor)).isEqualTo(1);
		assertThat(wallet(investor, "INVESTOR_EARNINGS")).isEqualByComparingTo("600");
	}

	@Test
	void twoApproversNeverBothTakeOrBothGiveTheSecondApproval() throws Exception {
		jdbc.update("UPDATE withdrawal_settings SET dual_approval_threshold = 500 WHERE id = 1");
		Account investor = withBank();
		credit(investor, "1000", "RENT_BANK");
		String id = submitted(investor, "600");
		List<Account> approvers = List.of(approver, api.staff("FINANCE_APPROVER"), api.staff("FINANCE_APPROVER"),
				api.staff("FINANCE_APPROVER"), api.staff("FINANCE_APPROVER"));

		Race take = race("5 approvers take one withdrawal for review", 5,
				i -> act(approvers.get(i), id, "review").andReturn().getResponse().getStatus());
		assertThat(take.count(200)).as("one reviewer holds it").isEqualTo(1);
		UUID reviewer = jdbc.queryForObject("SELECT review_assignee_id FROM withdrawals WHERE id = ?::uuid", UUID.class, id);
		Account holder = approvers.stream().filter(a -> a.id().equals(reviewer)).findFirst().orElseThrow();

		act(holder, id, "approve").andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
		List<Account> others = approvers.stream().filter(a -> !a.id().equals(reviewer)).toList();
		Race second = race("4 approvers give the second approval at once", others.size(),
				i -> act(others.get(i), id, "approve").andReturn().getResponse().getStatus());

		assertThat(second.count(200)).as("exactly one second approval").isEqualTo(1);
		Map<String, Object> row = jdbc.queryForMap(
				"SELECT status, first_approved_by, second_approved_by FROM withdrawals WHERE id = ?::uuid", id);
		assertThat(row.get("status")).isEqualTo("APPROVED");
		assertThat(row.get("second_approved_by")).isNotNull().isNotEqualTo(row.get("first_approved_by"));
	}

	@Test
	void anApprovalRacingTheInvestorsCancellationLeavesTheLedgerConsistent() throws Exception {
		for (int round = 0; round < 5; round++) {
			Account investor = withBank();
			credit(investor, "1000", "RENT_BANK");
			String id = submitted(investor, "400");
			act(approver, id, "review").andExpect(status().isOk());

			race("approve vs investor cancel (round " + round + ")", 2, i -> i == 0
					? act(approver, id, "approve").andReturn().getResponse().getStatus()
					: mvc.perform(post("/api/v1/withdrawals/{id}/cancel", id)
						.header(HttpHeaders.AUTHORIZATION, investor.bearer())).andReturn().getResponse().getStatus());

			String outcome = statusOf(id);
			assertThat(outcome).isIn("APPROVED", "CANCELLED");
			int releases = jdbc.queryForObject("""
					SELECT count(*) FROM ledger_transactions WHERE transaction_type = 'WITHDRAWAL_RELEASE' AND reference = ?""",
					Integer.class, id);
			assertThat(releases).isEqualTo(outcome.equals("CANCELLED") ? 1 : 0);
			assertThat(wallet(investor, "INVESTOR_EARNINGS"))
				.isEqualByComparingTo(outcome.equals("CANCELLED") ? "1000" : "600");
		}
	}

	@Test
	void duplicateAndConcurrentPayoutCallbacksPayOnce() throws Exception {
		Account investor = withBank();
		credit(investor, "1000", "RENT_BANK");
		String id = approvedAndInitiated(investor, "500");
		String ref = reference(id);
		String same = payoutEvent("evt-" + ref + "-paid", ref, "UTR-" + ref);

		Race race = race("30 payout webhooks (20 redeliveries + 10 distinct ids)", 30, i -> {
			String body = i < 20 ? same : payoutEvent("evt-" + ref + "-" + i, ref, "UTR-" + ref);
			return payoutWebhook(body).andReturn().getResponse().getStatus();
		});

		assertThat(race.count(500)).isZero();
		assertThat(statusOf(id)).isEqualTo("PAID");
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM ledger_transactions WHERE transaction_type = 'WITHDRAWAL_PAYOUT' AND reference = ?""",
				Integer.class, id)).as("paid once in the ledger").isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM payout_webhook_events WHERE withdrawal_id = ?::uuid",
				Integer.class, id)).as("each distinct event recorded at most once").isEqualTo(11);
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM payout_webhook_events WHERE withdrawal_id = ?::uuid AND outcome = 'PAID'""",
				Integer.class, id)).isEqualTo(1);
	}

	/**
	 * Audit finding H-1: a delivery that loses the race reads the withdrawal before locking it, and Hibernate refuses the
	 * newer row it then locks (409, retried by the provider). Enable once the webhook locks before it reads.
	 */
	@Test
	void everyConcurrentPayoutCallbackIsAcknowledged() throws Exception {
		Account investor = withBank();
		credit(investor, "1000", "RENT_BANK");
		String id = approvedAndInitiated(investor, "500");
		String ref = reference(id);
		Race race = race("30 payout webhooks, all acknowledged", 30,
				i -> payoutWebhook(payoutEvent("evt-" + ref + "-" + i, ref, "UTR-" + ref)).andReturn().getResponse().getStatus());
		assertThat(race.count(200)).isEqualTo(30);
	}

	@Test
	void retriedPaymentInitiationMakesOneBatch() throws Exception {
		Account investor = withBank();
		credit(investor, "1000", "RENT_BANK");
		String id = submitted(investor, "500");
		act(approver, id, "review").andExpect(status().isOk());
		act(approver, id, "approve").andExpect(status().isOk());

		Race race = race("5 simultaneous initiate-payment calls, one withdrawal", 5,
				i -> initiate(executive, id).andReturn().getResponse().getStatus());

		assertThat(race.count(200)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(DISTINCT batch_id) FROM withdrawals WHERE id = ?::uuid", Integer.class,
				id)).isEqualTo(1);
		assertThat(statusOf(id)).isEqualTo("PROCESSING");
	}

	// ------------------------------------------------------------------------------------------- balances

	@Test
	void concurrentDebitsCreditsAndAWithdrawalNeverOverdrawTheWallet() throws Exception {
		Account investor = withBank();
		credit(investor, "1000", "RENT_BANK");
		Account admin = api.admin();

		List<String> kinds = Collections.synchronizedList(new ArrayList<>());
		race("10 debits of 150 + 10 credits of 10 + 1 withdrawal of 300, one wallet", 21, i -> {
			int code;
			if (i < 10) {
				code = adjust(admin, investor, "DEBIT", "150").andReturn().getResponse().getStatus();
				if (code == 201) {
					kinds.add("debit");
				}
			}
			else if (i < 20) {
				code = adjust(admin, investor, "CREDIT", "10").andReturn().getResponse().getStatus();
				if (code == 201) {
					kinds.add("credit");
				}
			}
			else {
				code = withdraw(investor, "300", key()).andReturn().getResponse().getStatus();
				if (code == 201) {
					kinds.add("withdrawal");
				}
			}
			return code;
		});

		BigDecimal expected = new BigDecimal("1000");
		for (String kind : kinds) {
			expected = expected.add(switch (kind) {
				case "debit" -> new BigDecimal("-150");
				case "credit" -> BigDecimal.TEN;
				default -> new BigDecimal("-300");
			});
		}
		assertThat(kinds).filteredOn("credit"::equals).hasSize(10);
		assertThat(wallet(investor, "INVESTOR_EARNINGS")).as("no lost update").isEqualByComparingTo(expected)
			.isGreaterThanOrEqualTo(BigDecimal.ZERO);
		assertNoNegativeInvestorBalance();
	}

	// ------------------------------------------------------------------------------------------- returns (interest)

	@Test
	void overlappingRentCreditRunsCreditEachHourOnce() throws Exception {
		// A network investor: with Razorpay switched on here, online investors cannot pay by bank as the fixture does.
		Account investor = investors.approvedInvestor();
		investors.enrolInNetwork(investor);
		String orderId = investors.invest(investor, offerings.networkPlan(), 1);
		UUID holding = jdbc.queryForObject("SELECT id FROM holdings WHERE order_id = ?::uuid", UUID.class, orderId);
		backdateMonths(holding, 3);
		BigDecimal threeMonths = jdbc.queryForObject("SELECT monthly_rent * 3 FROM rent_accruals WHERE holding_id = ?",
				BigDecimal.class, holding);

		Race race = race("8 overlapping rent-credit sweeps (scheduled job on 8 instances)", 8, i -> payouts.creditDue());

		assertThat(race.failures()).isZero();
		assertThat(jdbc.queryForObject("SELECT sum(rent_amount) FROM rent_credits WHERE holding_id = ?", BigDecimal.class,
				holding)).as("three months of rent, once").isEqualByComparingTo(threeMonths);
		assertThat(wallet(investor, "INVESTOR_EARNINGS")).isEqualByComparingTo(threeMonths);
		int credits = jdbc.queryForObject("SELECT count(*) FROM rent_credits WHERE holding_id = ?", Integer.class, holding);
		int postings = jdbc.queryForObject("""
				SELECT count(*) FROM ledger_transactions WHERE transaction_type = 'INVESTOR_PAYOUT' AND reference LIKE ?""",
				Integer.class, holding + ":%");
		assertThat(postings).as("one ledger posting per credit").isEqualTo(credits);
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM (SELECT from_at FROM rent_credits WHERE holding_id = ? GROUP BY from_at HAVING count(*) > 1) d""",
				Integer.class, holding)).isZero();
	}

	// ------------------------------------------------------------------------------------------- subscriptions

	@Test
	void hundredSimultaneousSubscriptionsPlaceOneOrderPerKey() throws Exception {
		Account investor = withBank();
		UUID plan = offerings.plan();
		List<String> keys = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			keys.add(key());
		}
		Map<String, List<String>> idsByKey = new TreeMap<>();
		keys.forEach(k -> idsByKey.put(k, Collections.synchronizedList(new ArrayList<>())));

		Race race = race("100 checkouts (10 purchases x 10 retries)", 100, i -> {
			String key = keys.get(i % keys.size());
			var response = mvc.perform(post("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, investor.bearer())
					.header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
					.content("{\"productId\":\"%s\",\"units\":%d,\"acceptTerms\":true}".formatted(plan, 100)))
				.andReturn().getResponse();
			if (response.getStatus() / 100 == 2) {
				idsByKey.get(key).add(JsonPath.read(response.getContentAsString(), "$.id"));
			}
			return response.getStatus();
		});

		assertThat(race.count(500)).isZero();
		assertThat(race.count(409)).isZero();
		idsByKey.values().forEach(ids -> assertThat(ids).isNotEmpty().containsOnly(ids.getFirst()));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM orders WHERE user_id = ?", Integer.class, investor.id()))
			.isEqualTo(10);
		assertThat(jdbc.queryForObject("SELECT count(DISTINCT order_number) FROM orders WHERE user_id = ?", Integer.class,
				investor.id())).isEqualTo(10);
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM orders o WHERE o.user_id = ? AND o.total_amount <> (SELECT sum(i.amount) FROM order_items i
				WHERE i.order_id = o.id)""", Integer.class, investor.id())).isZero();
	}

	@Test
	void overlappingExpirySweepsExpireAnOrderOnce() throws Exception {
		Account investor = withBank();
		String orderId = investors.placeOrder(investor, offerings.plan(), 1);
		jdbc.update("UPDATE orders SET expires_at = now() - interval '1 minute' WHERE id = ?::uuid", orderId);

		race("5 overlapping order-expiry sweeps", 5, i -> orders.expireDue());

		assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id = ?::uuid", String.class, orderId))
			.isEqualTo("EXPIRED");
		assertThat(api.auditCount("ORDER_EXPIRED", UUID.fromString(orderId))).isEqualTo(1);
	}

	// ------------------------------------------------------------------------------------------- deposits

	@Test
	void duplicateAndConcurrentGatewayCallbacksConfirmTheOrderOnce() throws Exception {
		Account investor = approvedOnline();
		String orderId = gatewayOrder(investor);
		String account = virtualAccountFor(investor, orderId);
		String first = "pay_" + suffix();

		Race race = race("30 Razorpay credits (20 redeliveries + 10 other transfers) for one order", 30, i -> {
			String gatewayPayment = i < 20 ? first : "pay_" + suffix();
			return razorpayWebhook(creditedEvent(gatewayPayment, account, ORDER_PAISE)).andReturn().getResponse().getStatus();
		});

		assertThat(race.count(200)).as("every delivery is acknowledged").isEqualTo(30);
		assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id = ?::uuid", String.class, orderId))
			.isEqualTo("CONFIRMED");
		assertThat(paymentsOf(orderId, "SUCCEEDED")).as("one payment pays the order").isEqualTo(1);
		assertThat(paymentsOf(orderId, "REFUND_REQUIRED")).as("each other transfer is set aside to refund")
			.isEqualTo(10);
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM (SELECT external_reference FROM payments WHERE order_id = ?::uuid
				GROUP BY external_reference HAVING count(*) > 1) d""", Integer.class, orderId))
			.as("no gateway payment recorded twice").isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM holdings WHERE order_id = ?::uuid", Integer.class, orderId))
			.as("holdings are created once").isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM invoices WHERE order_id = ?::uuid", Integer.class, orderId))
			.isEqualTo(1);
	}

	@Test
	void aTimedOutPaymentStartIsRetriedSafely() throws Exception {
		Account investor = approvedOnline();
		String orderId = gatewayOrder(investor);
		String account = "va_" + suffix();
		when(razorpay.createVirtualAccount(eq(ORDER_PAISE), anyString(), any(Instant.class), anyMap()))
			.thenThrow(new RazorpayException("Read timed out", null))
			.thenReturn(new VirtualAccount(account,
					new VirtualAccountDetails("2223330012345678", "RATN0VAAPIS", "SeaLease", "RBL Bank", "rpy.sl@icici")));
		String key = key();

		startPayment(investor, orderId, "VIRTUAL_ACCOUNT", key).andExpect(status().isUnprocessableContent());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM payments WHERE order_id = ?::uuid", Integer.class, orderId))
			.as("the timed-out attempt left nothing behind").isZero();

		List<String> ids = Collections.synchronizedList(new ArrayList<>());
		race("10 retries of one payment start (same key)", 10, i -> {
			var response = startPayment(investor, orderId, "VIRTUAL_ACCOUNT", key).andReturn().getResponse();
			if (response.getStatus() / 100 == 2) {
				ids.add(JsonPath.read(response.getContentAsString(), "$.id"));
			}
			return response.getStatus();
		});

		assertThat(ids).isNotEmpty().containsOnly(ids.getFirst());
		assertThat(paymentsOf(orderId, "PENDING")).isEqualTo(1);
	}

	@Test
	void aSlowGatewayCallDoesNotHoldTheOrderLock() throws Exception {
		Account investor = approvedOnline();
		String orderId = gatewayOrder(investor);
		java.util.concurrent.atomic.AtomicReference<String> opened = new java.util.concurrent.atomic.AtomicReference<>();
		when(razorpay.createVirtualAccount(eq(ORDER_PAISE), anyString(), any(Instant.class), anyMap())).thenAnswer(call -> {
			Thread.sleep(1500); // a slow Razorpay answer
			opened.set("va_" + suffix());
			return new VirtualAccount(opened.get(),
					new VirtualAccountDetails("2223330012345678", "RATN0VAAPIS", "SeaLease", "RBL Bank", "rpy.sl@icici"));
		});

		long[] cancelWaitedMs = new long[1];
		Race race = race("payment start (slow gateway) vs order cancel", 2, i -> {
			if (i == 0) {
				return startPayment(investor, orderId, "VIRTUAL_ACCOUNT", key()).andReturn().getResponse().getStatus();
			}
			Thread.sleep(200); // the start is waiting for Razorpay by now
			long t0 = System.nanoTime();
			int code = mvc.perform(post("/api/v1/orders/{id}/cancel", orderId)
					.header(HttpHeaders.AUTHORIZATION, investor.bearer())).andReturn().getResponse().getStatus();
			cancelWaitedMs[0] = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
			return code;
		});

		System.out.printf("[concurrency-audit] order cancel took %d ms while a payment start waited for the gateway%n",
				cancelWaitedMs[0]);
		assertThat(cancelWaitedMs[0]).as("the cancel does not wait for the external call (finding M-1)").isLessThan(1000);
		assertThat(race.count(200)).as("the cancel").isEqualTo(1);
		assertThat(race.count(422)).as("the start finds the order cancelled").isEqualTo(1);
		assertThat(paymentsOf(orderId, "PENDING")).as("no payment recorded for a cancelled order").isZero();
		verify(razorpay, timeout(2000)).closeVirtualAccount(opened.get());
	}

	@Test
	void simultaneousRefundsAreRecordedOnce() throws Exception {
		Account investor = approvedOnline();
		String orderId = gatewayOrder(investor);
		String account = virtualAccountFor(investor, orderId);
		String shortPayment = "pay_" + suffix();
		razorpayWebhook(creditedEvent(shortPayment, account, 4_000_000)).andExpect(status().isOk());
		String paymentId = jdbc.queryForObject("SELECT id::text FROM payments WHERE order_id = ?::uuid AND status = 'REFUND_REQUIRED'",
				String.class, orderId);
		Account admin = api.admin();

		Race race = race("10 simultaneous refunds of one payment", 10,
				i -> mvc.perform(post("/api/v1/admin/payments/{id}/refund", paymentId)
					.header(HttpHeaders.AUTHORIZATION, admin.bearer()).contentType(MediaType.APPLICATION_JSON)
					.content("{\"reference\":\"RFND-%d\",\"reason\":\"Short transfer returned\"}".formatted(i)))
				.andReturn().getResponse().getStatus());

		assertThat(race.count(200)).as("refunded once").isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE id = ?::uuid", String.class, paymentId))
			.isEqualTo("REFUNDED");
		assertThat(api.auditCount("PAYMENT_REFUNDED", UUID.fromString(paymentId))).isEqualTo(1);
		// In the ledger: received once (to refund), refunded once, for the 40,000 that arrived (finding M-7).
		assertThat(jdbc.queryForList("""
				SELECT t.transaction_type || ' ' || a.account_type || ' ' || e.direction || ' ' || e.amount
				FROM ledger_transactions t JOIN ledger_entries e ON e.transaction_id = t.id
				JOIN ledger_accounts a ON a.id = e.account_id WHERE t.reference = ? ORDER BY 1""", String.class, paymentId))
			.containsExactly("DEPOSIT_RECEIVED DEPOSITS_RECEIVED DEBIT 40000.0000",
					"DEPOSIT_RECEIVED DEPOSITS_TO_REFUND CREDIT 40000.0000",
					"DEPOSIT_REFUNDED DEPOSITS_RECEIVED CREDIT 40000.0000",
					"DEPOSIT_REFUNDED DEPOSITS_TO_REFUND DEBIT 40000.0000");
	}

	// ------------------------------------------------------------------------------------------- wallet locks

	@Test
	void aCreditIsNotHeldUpByASpenderHoldingTheWallet() throws Exception {
		UUID user = approvedOnline().id();
		Currency inr = Currency.getInstance("INR");
		Money thousand = Money.of("1000", "INR");
		transactions.executeWithoutResult(s -> adjust(user, Posting.credit(AccountType.INVESTOR_EARNINGS, user, thousand),
				Posting.debit(AccountType.PLATFORM_ADJUSTMENTS, null, thousand)));

		// A spender (a withdrawal, a run) locks the wallet, takes 300 out and has not committed yet.
		CountDownLatch spent = new CountDownLatch(1);
		CountDownLatch commit = new CountDownLatch(1);
		Money spend = Money.of("300", "INR");
		Thread spender = Thread.ofVirtual().start(() -> transactions.executeWithoutResult(s -> {
			ledger.lockInvestorBalance(user, inr, EarningsBalance.RENT_BANK);
			adjust(user, Posting.debit(AccountType.INVESTOR_EARNINGS, user, spend),
					Posting.credit(AccountType.PLATFORM_ADJUSTMENTS, null, spend));
			spent.countDown();
			try {
				commit.await(15, TimeUnit.SECONDS);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}));
		assertThat(spent.await(10, TimeUnit.SECONDS)).isTrue();

		// Rent credited meanwhile does not wait for it (finding M-3).
		Money rent = Money.of("50", "INR");
		long t0 = System.nanoTime();
		transactions.executeWithoutResult(s -> adjust(user, Posting.credit(AccountType.INVESTOR_EARNINGS, user, rent),
				Posting.debit(AccountType.PLATFORM_ADJUSTMENTS, null, rent)));
		long waitedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
		commit.countDown();
		spender.join();
		System.out.printf("[concurrency-audit] a credit took %d ms while a spender held the wallet%n", waitedMs);

		assertThat(waitedMs).as("the credit did not wait for the spender").isLessThan(1000);
		assertThat(ledger.investorBalance(user, inr, EarningsBalance.RENT_BANK)).isEqualTo(Money.of("750", "INR"));
	}

	@Test
	void runningTotalsAlwaysEqualTheEntries() {
		// Finding M-4: balances are read from running totals; they must agree with the entries, for every account.
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM ledger_accounts a
				WHERE a.owner_user_id IS NOT NULL AND EXISTS (
				    SELECT 1 FROM (VALUES ('DEBIT'), ('CREDIT')) d(direction)
				    WHERE coalesce((SELECT total FROM ledger_account_totals t
				                    WHERE t.account_id = a.id AND t.direction = d.direction), 0)
				       <> coalesce((SELECT sum(amount) FROM ledger_entries e
				                    WHERE e.account_id = a.id AND e.direction = d.direction), 0))""", Integer.class))
			.as("accounts whose totals differ from their entries").isZero();
	}

	@Test
	void sessionsGiveUpOnLocksAndIdleTransactions() {
		// Finding M-6: a stuck transaction cannot hold a wallet or order lock indefinitely.
		assertThat(jdbc.queryForObject("SHOW lock_timeout", String.class)).isEqualTo("10s");
		assertThat(jdbc.queryForObject("SHOW idle_in_transaction_session_timeout", String.class)).isEqualTo("1min");
	}

	private void adjust(UUID user, Posting... postings) {
		ledger.post(TransactionType.ADJUSTMENT, user + ":" + suffix(), "Concurrency test", null, List.of(postings));
	}

	// ------------------------------------------------------------------------------------------- race harness

	@FunctionalInterface
	private interface Task {
		int run(int index) throws Exception;
	}

	private record Race(List<Integer> results, int failures) {

		long count(int code) {
			return results.stream().filter(r -> r == code).count();
		}

	}

	/**
	 * Runs {@code n} tasks released together and prints what happened: response codes, wall time, latency percentiles,
	 * the pool's peak active and waiting connections, the most sessions waiting on a lock, and new deadlocks.
	 */
	private Race race(String name, int n, Task task) throws Exception {
		HikariPoolMXBean pool = dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean();
		HikariDataSource hikari = dataSource.unwrap(HikariDataSource.class);
		AtomicInteger maxActive = new AtomicInteger();
		AtomicInteger maxWaiting = new AtomicInteger();
		AtomicInteger maxLockWaiters = new AtomicInteger();
		long deadlocksBefore;
		// A connection of its own, so watching the pool does not use it.
		try (Connection watcher = DriverManager.getConnection(hikari.getJdbcUrl(), hikari.getUsername(), hikari.getPassword())) {
			deadlocksBefore = deadlocks(watcher);
			ScheduledExecutorService sampler = Executors.newSingleThreadScheduledExecutor();
			sampler.scheduleAtFixedRate(() -> {
				maxActive.accumulateAndGet(pool.getActiveConnections(), Math::max);
				maxWaiting.accumulateAndGet(pool.getThreadsAwaitingConnection(), Math::max);
				try (Statement s = watcher.createStatement();
						ResultSet rs = s.executeQuery("SELECT count(DISTINCT pid) FROM pg_locks WHERE NOT granted")) {
					rs.next();
					maxLockWaiters.accumulateAndGet(rs.getInt(1), Math::max);
				}
				catch (Exception ignored) {
					// Sampling is best effort.
				}
			}, 0, 10, TimeUnit.MILLISECONDS);

			ExecutorService executor = Executors.newFixedThreadPool(n);
			CountDownLatch ready = new CountDownLatch(n);
			CountDownLatch go = new CountDownLatch(1);
			List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
			List<Future<Integer>> futures = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				int index = i;
				futures.add(executor.submit(() -> {
					ready.countDown();
					go.await();
					long t0 = System.nanoTime();
					try {
						return task.run(index);
					}
					finally {
						latencies.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0));
					}
				}));
			}
			ready.await();
			long start = System.nanoTime();
			go.countDown();
			List<Integer> results = new ArrayList<>();
			int failures = 0;
			for (Future<Integer> f : futures) {
				try {
					results.add(f.get(120, TimeUnit.SECONDS));
				}
				catch (Exception e) {
					failures++;
					results.add(-1);
				}
			}
			long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
			executor.shutdown();
			sampler.shutdownNow();
			sampler.awaitTermination(1, TimeUnit.SECONDS);
			Thread.sleep(600); // let PostgreSQL publish its statistics
			long newDeadlocks = deadlocks(watcher) - deadlocksBefore;

			List<Long> sorted = new ArrayList<>(latencies);
			Collections.sort(sorted);
			Map<Integer, Long> codes = new TreeMap<>();
			results.forEach(r -> codes.merge(r, 1L, Long::sum));
			System.out.printf("[concurrency-audit] %s | n=%d wall=%dms p50=%dms p95=%dms max=%dms results=%s errors=%d "
					+ "poolMaxActive=%d/%d poolMaxWaiting=%d maxLockWaiters=%d deadlocks=+%d%n", name, n, elapsed,
					percentile(sorted, 50), percentile(sorted, 95), sorted.isEmpty() ? 0 : sorted.getLast(), codes, failures,
					maxActive.get(), hikari.getMaximumPoolSize(), maxWaiting.get(), maxLockWaiters.get(), newDeadlocks);
			assertThat(newDeadlocks).as("no deadlocks in " + name).isZero();
			return new Race(results, failures);
		}
	}

	private static long percentile(List<Long> sorted, int p) {
		if (sorted.isEmpty()) {
			return 0;
		}
		return sorted.get(Math.min(sorted.size() - 1, (int) Math.ceil(p / 100.0 * sorted.size()) - 1));
	}

	private static long deadlocks(Connection connection) throws Exception {
		try (Statement s = connection.createStatement();
				ResultSet rs = s.executeQuery("SELECT deadlocks FROM pg_stat_database WHERE datname = current_database()")) {
			rs.next();
			return rs.getLong(1);
		}
	}

	// ------------------------------------------------------------------------------------------- withdrawal helpers

	private Account withBank() throws Exception {
		Account investor = investors.approvedInvestor();
		investors.ensureVerifiedBankAccount(investor);
		return investor;
	}

	private void credit(Account investor, String amount, String balance) throws Exception {
		mvc.perform(post("/api/v1/admin/ledger/adjustments").header(HttpHeaders.AUTHORIZATION, api.admin().bearer())
				.header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON).content("""
						{"userId":"%s","currency":"INR","direction":"CREDIT","amount":"%s","reason":"Test funding","balance":"%s"}
						""".formatted(investor.id(), amount, balance)))
			.andExpect(status().isCreated());
	}

	private ResultActions adjust(Account admin, Account investor, String direction, String amount) throws Exception {
		return mvc.perform(post("/api/v1/admin/ledger/adjustments").header(HttpHeaders.AUTHORIZATION, admin.bearer())
			.header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON).content("""
					{"userId":"%s","currency":"INR","direction":"%s","amount":"%s","reason":"Concurrency audit","balance":"RENT_BANK"}
					""".formatted(investor.id(), direction, amount)));
	}

	private ResultActions withdraw(Account investor, String amount, String key) throws Exception {
		return mvc.perform(post("/api/v1/withdrawals").header(HttpHeaders.AUTHORIZATION, investor.bearer())
			.header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
			.content("{\"balance\":\"RENT_BANK\",\"amount\":\"%s\",\"password\":\"%s\"}".formatted(amount, TestApi.PASSWORD)));
	}

	private String submitted(Account investor, String amount) throws Exception {
		return JsonPath.read(withdraw(investor, amount, key()).andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString(), "$.id");
	}

	private String approvedAndInitiated(Account investor, String amount) throws Exception {
		String id = submitted(investor, amount);
		act(approver, id, "review").andExpect(status().isOk());
		act(approver, id, "approve").andExpect(jsonPath("$.status").value("APPROVED"));
		initiate(executive, id).andExpect(status().isOk());
		return id;
	}

	private ResultActions act(Account staff, String id, String action) throws Exception {
		return mvc.perform(post("/api/v1/admin/withdrawals/{id}/" + action, id)
			.header(HttpHeaders.AUTHORIZATION, staff.bearer()));
	}

	private ResultActions initiate(Account staff, String id) throws Exception {
		return mvc.perform(post("/api/v1/admin/withdrawals/initiate-payment")
			.header(HttpHeaders.AUTHORIZATION, staff.bearer()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"withdrawalIds\":[\"%s\"]}".formatted(id)));
	}

	private ResultActions payoutWebhook(String body) throws Exception {
		return mvc.perform(post("/api/v1/webhooks/payouts")
			.header("X-Payout-Signature", RazorpaySignatures.sign(PAYOUT_WEBHOOK_SECRET, body))
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private static String payoutEvent(String id, String reference, String utr) {
		return """
				{"id":"%s","type":"payout.paid","withdrawalReference":"%s","providerReference":"%s","amount":"500.00","currency":"INR"}"""
			.formatted(id, reference, utr);
	}

	private int withdrawalsOf(Account investor) {
		return jdbc.queryForObject("SELECT count(*) FROM withdrawals WHERE user_id = ?", Integer.class, investor.id());
	}

	private int reservesOf(Account investor) {
		return jdbc.queryForObject("""
				SELECT count(*) FROM ledger_transactions t JOIN withdrawals w ON t.reference = w.id::text
				WHERE t.transaction_type = 'WITHDRAWAL_RESERVE' AND w.user_id = ?""", Integer.class, investor.id());
	}

	private String statusOf(String id) {
		return jdbc.queryForObject("SELECT status FROM withdrawals WHERE id = ?::uuid", String.class, id);
	}

	private String reference(String id) {
		return jdbc.queryForObject("SELECT reference FROM withdrawals WHERE id = ?::uuid", String.class, id);
	}

	private BigDecimal wallet(Account investor, String accountType) {
		return jdbc.queryForObject("""
				SELECT coalesce(sum(CASE WHEN e.direction = 'CREDIT' THEN e.amount ELSE -e.amount END), 0)
				FROM ledger_entries e JOIN ledger_accounts a ON a.id = e.account_id
				WHERE a.owner_user_id = ? AND a.account_type = ?""", BigDecimal.class, investor.id(), accountType);
	}

	/** No investor earnings account anywhere in the database is below zero. */
	private void assertNoNegativeInvestorBalance() {
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM (
				  SELECT a.id FROM ledger_accounts a JOIN ledger_entries e ON e.account_id = a.id
				  WHERE a.owner_user_id IS NOT NULL
				  GROUP BY a.id HAVING sum(CASE WHEN e.direction = 'CREDIT' THEN e.amount ELSE -e.amount END) < 0) n""",
				Integer.class)).as("negative investor balances").isZero();
	}

	// ------------------------------------------------------------------------------------------- returns helpers

	/** Starts the holding's lease {@code months} months before the current hour, nothing credited yet. */
	private void backdateMonths(UUID holding, int months) {
		Instant hour = Instant.now().truncatedTo(ChronoUnit.HOURS);
		Instant target = hour.atZone(ZoneOffset.UTC).minusMonths(months).toInstant();
		jdbc.update("""
				UPDATE rent_accruals
				SET starts_at = ?,
				    ends_at = (? AT TIME ZONE 'UTC' + make_interval(months => tenure_months)) AT TIME ZONE 'UTC',
				    credited_until = ?
				WHERE holding_id = ? AND credited_until = starts_at""", Timestamp.from(target), Timestamp.from(target),
				Timestamp.from(target), holding);
	}

	// ------------------------------------------------------------------------------------------- deposit helpers

	private Account approvedOnline() throws Exception {
		Account investor = api.register();
		jdbc.update("UPDATE user_profiles SET kyc_status = 'APPROVED' WHERE user_id = ?", investor.id());
		investors.ensureVerifiedBankAccount(investor);
		return investor;
	}

	private String gatewayOrder(Account investor) throws Exception {
		return JsonPath.read(mvc.perform(post("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"productId\":\"%s\",\"units\":100,\"acceptTerms\":true}".formatted(offerings.plan())))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
	}

	/** Opens a virtual account for the order; returns Razorpay's id for it. */
	private String virtualAccountFor(Account investor, String orderId) throws Exception {
		String account = "va_" + suffix();
		when(razorpay.createVirtualAccount(eq(ORDER_PAISE), anyString(), any(Instant.class), anyMap()))
			.thenReturn(new VirtualAccount(account,
					new VirtualAccountDetails("2223330012345678", "RATN0VAAPIS", "SeaLease", "RBL Bank", "rpy.sl@icici")));
		startPayment(investor, orderId, "VIRTUAL_ACCOUNT", key()).andExpect(status().isCreated());
		return account;
	}

	private ResultActions startPayment(Account investor, String orderId, String method, String key) throws Exception {
		return mvc.perform(post("/api/v1/orders/{id}/payments", orderId).header(HttpHeaders.AUTHORIZATION, investor.bearer())
			.header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
			.content("{\"method\":\"" + method + "\"}"));
	}

	private ResultActions razorpayWebhook(String body) throws Exception {
		return mvc.perform(post("/api/v1/webhooks/razorpay").contentType(MediaType.APPLICATION_JSON).content(body)
			.header("X-Razorpay-Signature", RazorpaySignatures.sign(RAZORPAY_WEBHOOK_SECRET, body)));
	}

	private static String creditedEvent(String gatewayPayment, String virtualAccount, long paise) {
		return """
				{"entity":"event","event":"virtual_account.credited","payload":{\
				"payment":{"entity":{"id":"%s","order_id":null,"amount":%d,"currency":"INR","status":"captured","method":"bank_transfer"}},\
				"virtual_account":{"entity":{"id":"%s"}}}}""".formatted(gatewayPayment, paise, virtualAccount);
	}

	private int paymentsOf(String orderId, String status) {
		return jdbc.queryForObject("SELECT count(*) FROM payments WHERE order_id = ?::uuid AND status = ?", Integer.class,
				orderId, status);
	}

	private static String suffix() {
		return UUID.randomUUID().toString().replace("-", "").substring(0, 14);
	}

	private static String key() {
		return UUID.randomUUID().toString();
	}

}
