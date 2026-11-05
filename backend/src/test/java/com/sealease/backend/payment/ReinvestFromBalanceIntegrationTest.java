package com.sealease.backend.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.jayway.jsonpath.JsonPath;
import com.sealease.backend.reconciliation.ReconciliationService;
import com.sealease.backend.support.IntegrationTest;
import com.sealease.backend.support.InvestorFixtures;
import com.sealease.backend.support.OfferingFixtures;
import com.sealease.backend.support.TestApi;
import com.sealease.backend.support.TestApi.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Online investors pay for an investment from their available balance (returns, then referral commission), with
 * their password. The order is confirmed at once, the ledger moves the amount from their earnings to invested capital,
 * and the balance can never be spent twice: not by a retry, and not by a withdrawal racing the payment.
 */
@IntegrationTest
class ReinvestFromBalanceIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ReconciliationService reconciliation;

	private TestApi api;
	private InvestorFixtures investors;
	private OfferingFixtures offerings;

	@BeforeEach
	void setUp() throws Exception {
		api = new TestApi(mvc, jdbc);
		investors = new InvestorFixtures(mvc, jdbc, api);
		offerings = new OfferingFixtures(mvc, api.admin());
	}

	@Test
	void anOnlineInvestorPaysAnOrderFromTheirReturnsThenTheirCommission() throws Exception {
		Account investor = investors.approvedInvestor();
		credit(investor, "30000", "RENT_BANK");
		credit(investor, "25000", "REFERRAL");
		String orderId = investors.placeOrder(investor, offerings.plan(), 1); // ₹50,000

		mvc.perform(get("/api/v1/payments/methods").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.methods", hasItem("WALLET")))
			.andExpect(jsonPath("$.availableBalance.amount").value("55000.00"));

		String body = pay(investor, orderId, TestApi.PASSWORD, key()).andExpect(status().isCreated())
			.andExpect(jsonPath("$.method").value("WALLET"))
			.andExpect(jsonPath("$.status").value("SUCCEEDED"))
			.andExpect(jsonPath("$.amount.amount").value("50000.00"))
			.andReturn().getResponse().getContentAsString();
		String paymentId = JsonPath.read(body, "$.id");

		assertThat(orderStatus(orderId)).isEqualTo("CONFIRMED");
		assertThat(wallet(investor, "INVESTOR_EARNINGS")).isEqualByComparingTo("0");
		assertThat(wallet(investor, "INVESTOR_REFERRAL_EARNINGS")).isEqualByComparingTo("5000");
		// Earnings became invested capital; no money arrived, so no deposit is posted.
		assertThat(posted(paymentId, "REINVESTMENT", "INVESTOR_PRINCIPAL", "CREDIT")).isEqualByComparingTo("50000");
		assertThat(posted(paymentId, "DEPOSIT_RECEIVED", "DEPOSITS_RECEIVED", "DEBIT")).isEqualByComparingTo("0");
		// Returns on these units are paid like those on units paid by bank.
		assertThat(jdbc.queryForObject("SELECT count(*) FROM holdings WHERE order_id = ?::uuid AND NOT paid_at_office",
				Integer.class, orderId)).isPositive();
		// Reconciliation finds its posting.
		assertThat(reconciliation.report().unpostedDeposits())
			.noneMatch(d -> d.paymentId().toString().equals(paymentId));

		mvc.perform(get("/api/v1/payments/methods").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.availableBalance.amount").value("5000.00"));
	}

	@Test
	void aRetryReturnsTheSamePaymentAndSpendsNothingMore() throws Exception {
		Account investor = investors.approvedInvestor();
		credit(investor, "120000", "RENT_BANK");
		String orderId = investors.placeOrder(investor, offerings.plan(), 1);
		String key = key();

		String first = pay(investor, orderId, TestApi.PASSWORD, key).andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		String again = pay(investor, orderId, TestApi.PASSWORD, key).andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		assertThat(JsonPath.<String>read(again, "$.id")).isEqualTo(JsonPath.<String>read(first, "$.id"));

		pay(investor, orderId, TestApi.PASSWORD, key()).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.message", containsString("no longer awaiting payment")));
		assertThat(wallet(investor, "INVESTOR_EARNINGS")).isEqualByComparingTo("70000");
		assertThat(payments(orderId)).isEqualTo(1);
	}

	@Test
	void aBalanceThatDoesNotCoverTheOrderPaysNothing() throws Exception {
		Account investor = investors.approvedInvestor();
		credit(investor, "30000", "RENT_BANK");
		credit(investor, "19999.99", "REFERRAL");
		String orderId = investors.placeOrder(investor, offerings.plan(), 1);

		pay(investor, orderId, TestApi.PASSWORD, key()).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.message", containsString("does not cover")));

		assertThat(orderStatus(orderId)).isEqualTo("PENDING_PAYMENT");
		assertThat(payments(orderId)).isZero();
		assertThat(wallet(investor, "INVESTOR_EARNINGS")).isEqualByComparingTo("30000");
		assertThat(wallet(investor, "INVESTOR_REFERRAL_EARNINGS")).isEqualByComparingTo("19999.99");
	}

	@Test
	void theInvestorsPasswordIsRequired() throws Exception {
		Account investor = investors.approvedInvestor();
		credit(investor, "60000", "RENT_BANK");
		String orderId = investors.placeOrder(investor, offerings.plan(), 1);

		pay(investor, orderId, "Wrong-Password-1", key()).andExpect(status().is4xxClientError());
		// The ordinary way to start a payment does not pay from the balance.
		mvc.perform(post("/api/v1/orders/{id}/payments", orderId).header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"WALLET\"}"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.message", containsString("password")));

		assertThat(orderStatus(orderId)).isEqualTo("PENDING_PAYMENT");
		assertThat(payments(orderId)).isZero();
		assertThat(wallet(investor, "INVESTOR_EARNINGS")).isEqualByComparingTo("60000");
	}

	@Test
	void aBankPaymentTheInvestorReportedIsVerifiedFirst() throws Exception {
		Account investor = investors.approvedInvestor();
		credit(investor, "60000", "RENT_BANK");
		String orderId = investors.placeOrder(investor, offerings.plan(), 1);
		UUID companyAccount = api.companyBankAccount();
		String payment = mvc.perform(post("/api/v1/orders/{id}/payments", orderId)
				.header(HttpHeaders.AUTHORIZATION, investor.bearer()).header("Idempotency-Key", key())
				.contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"BANK_TRANSFER\"}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		String paymentId = JsonPath.read(payment, "$.id");
		mvc.perform(post("/api/v1/payments/{id}/deposit", paymentId).header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"companyBankAccountId":"%s","mode":"ONLINE","reference":"UTR-%s"}"""
					.formatted(companyAccount, UUID.randomUUID())))
			.andExpect(status().isOk());

		pay(investor, orderId, TestApi.PASSWORD, key()).andExpect(status().isConflict())
			.andExpect(jsonPath("$.message", containsString("verified")));
		assertThat(orderStatus(orderId)).isEqualTo("PENDING_PAYMENT");
		assertThat(wallet(investor, "INVESTOR_EARNINGS")).isEqualByComparingTo("60000");
	}

	@Test
	void networkInvestorsCannotPayFromTheirBalance() throws Exception {
		Account investor = investors.approvedInvestor();
		investors.enrolInNetwork(investor);
		credit(investor, "60000", "RENT_BANK");
		String orderId = investors.placeOrder(investor, offerings.networkPlan(), 1);

		mvc.perform(get("/api/v1/payments/methods").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.methods", not(hasItem("WALLET"))))
			.andExpect(jsonPath("$.availableBalance").doesNotExist());
		pay(investor, orderId, TestApi.PASSWORD, key()).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.message", containsString("online investors only")));
		assertThat(payments(orderId)).isZero();
	}

	@Test
	void anotherInvestorsOrderCannotBePaid() throws Exception {
		Account owner = investors.approvedInvestor();
		Account other = investors.approvedInvestor();
		credit(other, "60000", "RENT_BANK");
		String orderId = investors.placeOrder(owner, offerings.plan(), 1);

		pay(other, orderId, TestApi.PASSWORD, key()).andExpect(status().isNotFound());
		assertThat(wallet(other, "INVESTOR_EARNINGS")).isEqualByComparingTo("60000");
	}

	@Test
	void aWithdrawalRacingThePaymentCannotSpendTheSameBalance() throws Exception {
		Account investor = investors.approvedInvestor();
		investors.ensureVerifiedBankAccount(investor);
		credit(investor, "50000", "RENT_BANK");
		String orderId = investors.placeOrder(investor, offerings.plan(), 1);

		List<Integer> statuses = together(
				() -> pay(investor, orderId, TestApi.PASSWORD, key()).andReturn().getResponse().getStatus(),
				() -> mvc.perform(post("/api/v1/withdrawals").header(HttpHeaders.AUTHORIZATION, investor.bearer())
						.header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON).content("""
								{"balance":"RENT_BANK","amount":"50000","password":"%s"}""".formatted(TestApi.PASSWORD)))
					.andReturn().getResponse().getStatus());

		// Exactly one of them spent the ₹50,000; the wallet never went below zero.
		assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(1);
		assertThat(wallet(investor, "INVESTOR_EARNINGS")).isEqualByComparingTo("0");
		boolean paid = statuses.get(0) == 201;
		assertThat(orderStatus(orderId)).isEqualTo(paid ? "CONFIRMED" : "PENDING_PAYMENT");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM withdrawals WHERE user_id = ?", Integer.class, investor.id()))
			.isEqualTo(paid ? 0 : 1);
	}

	// ------------------------------------------------------------------------------------------- helpers

	private ResultActions pay(Account investor, String orderId, String password, String key) throws Exception {
		return mvc.perform(post("/api/v1/orders/{id}/payments/balance", orderId)
			.header(HttpHeaders.AUTHORIZATION, investor.bearer()).header("Idempotency-Key", key)
			.contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"%s\"}".formatted(password)));
	}

	private void credit(Account investor, String amount, String balance) throws Exception {
		mvc.perform(post("/api/v1/admin/ledger/adjustments").header(HttpHeaders.AUTHORIZATION, api.admin().bearer())
				.header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON).content("""
						{"userId":"%s","currency":"INR","direction":"CREDIT","amount":"%s","reason":"Test funding","balance":"%s"}
						""".formatted(investor.id(), amount, balance)))
			.andExpect(status().isCreated());
	}

	private BigDecimal wallet(Account investor, String accountType) {
		return jdbc.queryForObject("""
				SELECT coalesce(sum(CASE WHEN e.direction = 'CREDIT' THEN e.amount ELSE -e.amount END), 0)
				FROM ledger_entries e JOIN ledger_accounts a ON a.id = e.account_id
				WHERE a.owner_user_id = ? AND a.account_type = ?""", BigDecimal.class, investor.id(), accountType);
	}

	private BigDecimal posted(String paymentId, String type, String accountType, String direction) {
		return jdbc.queryForObject("""
				SELECT coalesce(sum(e.amount), 0) FROM ledger_entries e
				JOIN ledger_accounts a ON a.id = e.account_id JOIN ledger_transactions t ON t.id = e.transaction_id
				WHERE t.transaction_type = ? AND t.reference = ? AND a.account_type = ? AND e.direction = ?""",
				BigDecimal.class, type, paymentId, accountType, direction);
	}

	private String orderStatus(String orderId) {
		return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?::uuid", String.class, orderId);
	}

	private int payments(String orderId) {
		return jdbc.queryForObject("SELECT count(*) FROM payments WHERE order_id = ?::uuid", Integer.class, orderId);
	}

	/** Runs the calls at the same moment; their results, in order. */
	@SafeVarargs
	private static <T> List<T> together(Callable<T>... calls) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(calls.length);
		try {
			CountDownLatch go = new CountDownLatch(1);
			List<Future<T>> futures = new ArrayList<>();
			for (Callable<T> call : calls) {
				futures.add(executor.submit(() -> {
					go.await();
					return call.call();
				}));
			}
			go.countDown();
			List<T> results = new ArrayList<>();
			for (Future<T> f : futures) {
				results.add(f.get(60, TimeUnit.SECONDS));
			}
			return results;
		}
		finally {
			executor.shutdownNow();
		}
	}

	private static String key() {
		return UUID.randomUUID().toString();
	}

}
