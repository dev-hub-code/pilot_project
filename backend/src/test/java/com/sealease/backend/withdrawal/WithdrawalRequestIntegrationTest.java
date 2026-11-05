package com.sealease.backend.withdrawal;

import com.jayway.jsonpath.JsonPath;
import com.sealease.backend.payment.gateway.RazorpaySignatures;
import com.sealease.backend.support.IntegrationTest;
import com.sealease.backend.support.InvestorFixtures;
import com.sealease.backend.support.TestApi;
import com.sealease.backend.support.TestApi.Account;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Online investors request withdrawals of any amount from a wallet; finance reviews, approves (twice above the
 * threshold), pays through the payout provider, and only the bank's evidence marks them paid.
 */
@IntegrationTest
class WithdrawalRequestIntegrationTest {

	private static final String WEBHOOK_SECRET = "test-payout-webhook-secret";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	private TestApi api;
	private InvestorFixtures investors;
	private Account approver;
	private Account executive;

	@BeforeEach
	void setUp() throws Exception {
		api = new TestApi(mvc, jdbc);
		investors = new InvestorFixtures(mvc, jdbc, api);
		approver = api.staff("FINANCE_APPROVER");
		executive = api.staff("WITHDRAWAL_EXECUTIVE");
	}

	@AfterEach
	void resetSettings() {
		jdbc.update("""
				UPDATE withdrawal_settings SET fee_flat = 0, fee_percent = 0, tax_percent = 0,
				       dual_approval_threshold = NULL WHERE id = 1""");
	}

	// ------------------------------------------------------------------------------------------- request

	@Test
	void anInvestorWithdrawsPartOfAWalletAndTheBackendWorksOutTheCharges() throws Exception {
		charges("10", "0", "1");
		Account investor = withBank();
		credit(investor, "1000", "RENT_BANK");

		mvc.perform(get("/api/v1/withdrawals/quote").param("balance", "RENT_BANK").param("amount", "400")
				.header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.allowed").value(true))
			.andExpect(jsonPath("$.fee.amount").value("10.00"))
			.andExpect(jsonPath("$.tax.amount").value("4.00"))
			.andExpect(jsonPath("$.net.amount").value("386.00"))
			.andExpect(jsonPath("$.remaining.amount").value("600.00"));

		String body = request(investor, "RENT_BANK", "400", key()).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("SUBMITTED"))
			.andExpect(jsonPath("$.amount.amount").value("400.00"))
			.andExpect(jsonPath("$.net.amount").value("386.00"))
			.andExpect(jsonPath("$.bankAccountMasked").value(containsString("••••")))
			.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(body, "$.id");

		assertThat(wallet(investor, "INVESTOR_EARNINGS")).isEqualByComparingTo("600");
		mvc.perform(get("/api/v1/withdrawals/{id}/timeline", id).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$[0].to").value("SUBMITTED"));
		assertThat(notifications(investor, "WITHDRAWAL_SUBMITTED")).isEqualTo(1);
		mvc.perform(get("/api/v1/withdrawals/overview").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.available.amount").value("600.00"))
			.andExpect(jsonPath("$.pending.amount").value("400.00"))
			.andExpect(jsonPath("$.canRequest").value(true));
	}

	@Test
	void theQuoteRefusesWhatCannotBeWithdrawn() throws Exception {
		Account investor = withBank();
		credit(investor, "100", "RENT_BANK");

		quote(investor, "100.01").andExpect(jsonPath("$.allowed").value(false))
			.andExpect(jsonPath("$.problem").value(containsString("available")));
		quote(investor, "10").andExpect(jsonPath("$.allowed").value(false))
			.andExpect(jsonPath("$.problem").value(containsString("minimum")));
		quote(investor, "50.001").andExpect(jsonPath("$.allowed").value(false));
		refused(request(investor, "RENT_BANK", "100.01", key()), "available");
		assertThat(count(investor)).isZero();
	}

	@Test
	void aRetryChangesNothingAndASecondRequestWaits() throws Exception {
		Account investor = withBank();
		credit(investor, "1000", "RENT_BANK");
		String key = key();

		String first = request(investor, "RENT_BANK", "300", key).andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		String again = request(investor, "RENT_BANK", "300", key).andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		assertThat(JsonPath.<String>read(again, "$.id")).isEqualTo(JsonPath.<String>read(first, "$.id"));

		request(investor, "RENT_BANK", "300", key()).andExpect(status().isConflict());
		assertThat(count(investor)).isEqualTo(1);
		assertThat(wallet(investor, "INVESTOR_EARNINGS")).isEqualByComparingTo("700");
	}

	@Test
	void concurrentRequestsNeverSpendTheSameMoneyTwice() throws Exception {
		Account investor = withBank();
		credit(investor, "500", "RENT_BANK");
		ExecutorService pool = Executors.newFixedThreadPool(4);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Integer>> results = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			results.add(pool.submit(() -> {
				start.await();
				return request(investor, "RENT_BANK", "400", key()).andReturn().getResponse().getStatus();
			}));
		}
		start.countDown();
		List<Integer> statuses = new ArrayList<>();
		for (Future<Integer> r : results) {
			statuses.add(r.get());
		}
		pool.shutdown();

		assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
		assertThat(count(investor)).isEqualTo(1);
		assertThat(wallet(investor, "INVESTOR_EARNINGS")).isEqualByComparingTo("100");
	}

	@Test
	void theWithdrawalIsConfirmedWithThePassword() throws Exception {
		Account investor = withBank();
		credit(investor, "1000", "RENT_BANK");

		mvc.perform(post("/api/v1/withdrawals").header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"balance\":\"RENT_BANK\",\"amount\":\"100\",\"password\":\"wrong-password\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(containsString("password")));

		assertThat(count(investor)).isZero();
		// The wrong password counts towards the sign-in lockout, although the request rolled back.
		assertThat(jdbc.queryForObject("SELECT failed_login_attempts FROM users WHERE id = ?", Integer.class,
				investor.id())).isEqualTo(1);
	}

	@Test
	void eligibilityIsCheckedByTheBackend() throws Exception {
		Account network = withBank();
		investors.enrolInNetwork(network);
		credit(network, "1000", "RENT_BANK");
		refused(request(network, "RENT_BANK", "100", key()), "Network investors");

		Account noBank = investors.approvedInvestor();
		credit(noBank, "1000", "RENT_BANK");
		refused(request(noBank, "RENT_BANK", "100", key()), "No verified primary bank account");

		Account unverified = withBank();
		credit(unverified, "1000", "RENT_BANK");
		jdbc.update("UPDATE user_profiles SET kyc_status = 'PENDING' WHERE user_id = ?", unverified.id());
		refused(request(unverified, "RENT_BANK", "100", key()), "Identity verification");

		assertThat(count(network) + count(noBank) + count(unverified)).isZero();
	}

	@Test
	void someoneElsesBankAccountCannotBeUsed() throws Exception {
		Account investor = withBank();
		Account other = withBank();
		credit(investor, "1000", "RENT_BANK");
		UUID othersAccount = jdbc.queryForObject("SELECT id FROM bank_accounts WHERE user_id = ? LIMIT 1", UUID.class,
				other.id());

		mvc.perform(post("/api/v1/withdrawals").header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"balance":"RENT_BANK","amount":"100","bankAccountId":"%s","password":"%s"}"""
					.formatted(othersAccount, TestApi.PASSWORD)))
			.andExpect(status().isNotFound());
		assertThat(count(investor)).isZero();
	}

	// ------------------------------------------------------------------------------------------- review

	@Test
	void reviewedApprovedAndPaidOnlyWithTheBanksReference() throws Exception {
		charges("5", "1", "2");
		Account investor = withBank();
		credit(investor, "2000", "RENT_BANK");
		String id = submitted(investor, "1000");

		act(approver, id, "approve").andExpect(status().isConflict()); // take it first
		act(approver, id, "review").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UNDER_REVIEW"));
		act(approver, id, "approve").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
		note(approver, id, "Checked against the KYC file").andExpect(status().isCreated());

		// An approver cannot pay; the executive initiates payment.
		initiate(approver, id).andExpect(status().isForbidden());
		initiate(executive, id).andExpect(status().isOk()).andExpect(jsonPath("$.provider").value("BANK_SHEET"));
		assertThat(statusOf(id)).isEqualTo("PROCESSING");
		mvc.perform(get("/api/v1/admin/finance-tasks").param("view", "OPEN").param("q", reference(id))
				.header(HttpHeaders.AUTHORIZATION, executive.bearer()))
			.andExpect(jsonPath("$.content[0].amount.amount").value("965.00"));

		// Marking a bank transfer paid needs the bank's reference.
		api.takeFinanceTask(id, executive);
		task(executive, id, "{\"outcome\":\"PAID\"}").andExpect(status().isBadRequest());
		assertThat(statusOf(id)).isEqualTo("PROCESSING");
		task(executive, id, "{\"outcome\":\"PAID\",\"reference\":\"UTR000111\"}").andExpect(status().isOk());
		assertThat(statusOf(id)).isEqualTo("PAID");

		// The ledger: 1000 out of transit; 965 paid, 15 charges, 20 tax.
		assertThat(posted(id, "WITHDRAWAL_FEE_INCOME")).isEqualByComparingTo("15");
		assertThat(posted(id, "TAX_WITHHELD")).isEqualByComparingTo("20");
		assertThat(posted(id, "RENTAL_CASH")).isEqualByComparingTo("965");

		// The investor tracks it without staff names or notes, and gets a receipt.
		mvc.perform(get("/api/v1/withdrawals/{id}/timeline", id).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$[*].to").value(hasItem("PAID")))
			.andExpect(jsonPath("$[*].actorName").value(not(hasItem(org.hamcrest.Matchers.notNullValue()))))
			.andExpect(content().string(not(containsString("KYC file"))));
		mvc.perform(get("/api/v1/withdrawals/{id}/receipt", id).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_PDF));
		for (String type : List.of("WITHDRAWAL_SUBMITTED", "WITHDRAWAL_UNDER_REVIEW", "WITHDRAWAL_APPROVED",
				"WITHDRAWAL_PAYMENT_INITIATED", "WITHDRAWAL_PAID")) {
			assertThat(notifications(investor, type)).as(type).isEqualTo(1);
		}
		mvc.perform(get("/api/v1/admin/withdrawals/{id}", id).header(HttpHeaders.AUTHORIZATION, approver.bearer()))
			.andExpect(jsonPath("$.notes[0].body").value("Checked against the KYC file"))
			.andExpect(jsonPath("$.timeline[*].to").value(hasItem("UNDER_REVIEW")));
	}

	@Test
	void aLargeWithdrawalNeedsASecondDifferentApprover() throws Exception {
		jdbc.update("UPDATE withdrawal_settings SET dual_approval_threshold = 500 WHERE id = 1");
		Account investor = withBank();
		credit(investor, "1000", "RENT_BANK");
		String id = submitted(investor, "600");
		Account second = api.staff("FINANCE_APPROVER");

		act(approver, id, "review").andExpect(status().isOk());
		act(approver, id, "approve").andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
		act(approver, id, "approve").andExpect(status().isUnprocessableContent());
		act(second, id, "approve").andExpect(jsonPath("$.status").value("APPROVED"))
			.andExpect(jsonPath("$.approvals").value(2));
	}

	@Test
	void nobodyReviewsTheirOwnWithdrawalAndViewersCannotDecide() throws Exception {
		Account investor = withBank();
		credit(investor, "1000", "RENT_BANK");
		String id = submitted(investor, "100");
		Account self = api.becomeStaff(investor, "FINANCE_APPROVER");
		act(self, id, "review").andExpect(status().isForbidden());

		Account viewer = api.staff("FINANCE_MANAGER");
		act(viewer, id, "review").andExpect(status().isForbidden());
		decide(viewer, id, "reject", "No").andExpect(status().isForbidden());
		assertThat(statusOf(id)).isEqualTo("SUBMITTED");
	}

	@Test
	void aRejectionNeedsAReasonAndGivesTheMoneyBack() throws Exception {
		Account investor = withBank();
		credit(investor, "1000", "RENT_BANK");
		String id = submitted(investor, "400");

		decide(approver, id, "reject", " ").andExpect(status().isBadRequest());
		decide(approver, id, "reject", "Bank details do not match").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("REJECTED"));
		assertThat(wallet(investor, "INVESTOR_EARNINGS")).isEqualByComparingTo("1000");
		assertThat(notifications(investor, "WITHDRAWAL_REJECTED")).isEqualTo(1);
		act(approver, id, "approve").andExpect(status().isConflict());
	}

	@Test
	void theInvestorCancelsWhileUnderReviewButNotOnceApproved() throws Exception {
		Account investor = withBank();
		credit(investor, "1000", "RENT_BANK");
		String id = submitted(investor, "400");
		cancel(investor, id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
		assertThat(wallet(investor, "INVESTOR_EARNINGS")).isEqualByComparingTo("1000");

		String next = submitted(investor, "400");
		act(approver, next, "review");
		act(approver, next, "approve").andExpect(jsonPath("$.status").value("APPROVED"));
		cancel(investor, next).andExpect(status().isConflict());
	}

	@Test
	void moreInformationIsAskedForAndTheAnswerResumesTheReview() throws Exception {
		Account investor = withBank();
		credit(investor, "1000", "RENT_BANK");
		String id = submitted(investor, "400");
		act(approver, id, "review");

		decide(approver, id, "request-info", "Please confirm the source of these funds").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ON_HOLD"))
			.andExpect(jsonPath("$.infoRequested").value(true));
		assertThat(notifications(investor, "WITHDRAWAL_INFO_REQUIRED")).isEqualTo(1);

		mvc.perform(post("/api/v1/withdrawals/{id}/respond", id).header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Rent from my investments\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UNDER_REVIEW"));
		act(approver, id, "approve").andExpect(jsonPath("$.status").value("APPROVED"));
	}

	// ------------------------------------------------------------------------------------------- webhook

	@Test
	void aVerifiedWebhookPaysOnceAndAnythingDoubtfulIsLeftForFinance() throws Exception {
		Account investor = withBank();
		credit(investor, "1000", "RENT_BANK");
		String id = approvedAndInitiated(investor, "500");
		String ref = reference(id);

		webhook("{\"id\":\"evt-bad\",\"type\":\"payout.paid\"}", "0000").andExpect(status().isUnauthorized());
		webhook(event("evt-%s-1".formatted(ref), "payout.processing", ref, "UTR9", "500.00"))
			.andExpect(jsonPath("$.outcome").value("IGNORED"));
		webhook(event("evt-%s-2".formatted(ref), "payout.paid", ref, "UTR9", "499.00"))
			.andExpect(jsonPath("$.outcome").value("AMOUNT_MISMATCH"));
		assertThat(statusOf(id)).isEqualTo("PROCESSING");

		String paid = event("evt-%s-3".formatted(ref), "payout.paid", ref, "UTR9", "500.00");
		webhook(paid).andExpect(jsonPath("$.outcome").value("PAID"));
		webhook(paid).andExpect(jsonPath("$.outcome").value("DUPLICATE"));
		webhook(event("evt-%s-4".formatted(ref), "payout.failed", ref, null, "500.00"))
			.andExpect(jsonPath("$.outcome").value("NEEDS_RECONCILIATION"));

		assertThat(statusOf(id)).isEqualTo("PAID");
		assertThat(jdbc.queryForObject("SELECT payout_reference FROM withdrawals WHERE id = ?::uuid", String.class, id))
			.isEqualTo("UTR9");
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM ledger_transactions WHERE transaction_type = 'WITHDRAWAL_PAYOUT' AND reference = ?""",
				Integer.class, id)).isEqualTo(1);
		assertThat(notifications(investor, "WITHDRAWAL_PAID")).isEqualTo(1);
	}

	@Test
	void aFailedWebhookHoldsTheMoneyUntilFinanceResolvesIt() throws Exception {
		Account investor = withBank();
		credit(investor, "1000", "RENT_BANK");
		String id = approvedAndInitiated(investor, "500");
		String ref = reference(id);

		webhook(event("evt-%s-f".formatted(ref), "payout.failed", ref, null, "500.00"))
			.andExpect(jsonPath("$.outcome").value("FAILED"));
		assertThat(statusOf(id)).isEqualTo("FAILED");
		assertThat(wallet(investor, "INVESTOR_EARNINGS")).isEqualByComparingTo("500");
	}

	// ------------------------------------------------------------------------------------------- helpers

	private String approvedAndInitiated(Account investor, String amount) throws Exception {
		String id = submitted(investor, amount);
		act(approver, id, "review").andExpect(status().isOk());
		act(approver, id, "approve").andExpect(jsonPath("$.status").value("APPROVED"));
		initiate(executive, id).andExpect(status().isOk());
		return id;
	}

	private String submitted(Account investor, String amount) throws Exception {
		return JsonPath.read(request(investor, "RENT_BANK", amount, key()).andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString(), "$.id");
	}

	private ResultActions request(Account investor, String balance, String amount, String key) throws Exception {
		return mvc.perform(post("/api/v1/withdrawals").header(HttpHeaders.AUTHORIZATION, investor.bearer())
			.header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
			.content("{\"balance\":\"%s\",\"amount\":\"%s\",\"password\":\"%s\"}".formatted(balance, amount,
					TestApi.PASSWORD)));
	}

	private ResultActions quote(Account investor, String amount) throws Exception {
		return mvc.perform(get("/api/v1/withdrawals/quote").param("balance", "RENT_BANK").param("amount", amount)
			.header(HttpHeaders.AUTHORIZATION, investor.bearer())).andExpect(status().isOk());
	}

	private ResultActions cancel(Account investor, String id) throws Exception {
		return mvc.perform(post("/api/v1/withdrawals/{id}/cancel", id).header(HttpHeaders.AUTHORIZATION, investor.bearer()));
	}

	private ResultActions act(Account staff, String id, String action) throws Exception {
		return mvc.perform(post("/api/v1/admin/withdrawals/{id}/" + action, id)
			.header(HttpHeaders.AUTHORIZATION, staff.bearer()));
	}

	private ResultActions decide(Account staff, String id, String action, String reason) throws Exception {
		return mvc.perform(post("/api/v1/admin/withdrawals/{id}/" + action, id)
			.header(HttpHeaders.AUTHORIZATION, staff.bearer()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\":\"%s\"}".formatted(reason)));
	}

	private ResultActions note(Account staff, String id, String body) throws Exception {
		return mvc.perform(post("/api/v1/admin/withdrawals/{id}/notes", id)
			.header(HttpHeaders.AUTHORIZATION, staff.bearer()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\":\"%s\"}".formatted(body)));
	}

	private ResultActions initiate(Account staff, String id) throws Exception {
		return mvc.perform(post("/api/v1/admin/withdrawals/initiate-payment")
			.header(HttpHeaders.AUTHORIZATION, staff.bearer()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"withdrawalIds\":[\"%s\"]}".formatted(id)));
	}

	private ResultActions task(Account staff, String id, String body) throws Exception {
		return mvc.perform(post("/api/v1/admin/finance-tasks/{id}", id).header(HttpHeaders.AUTHORIZATION, staff.bearer())
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions webhook(String body) throws Exception {
		return webhook(body, RazorpaySignatures.sign(WEBHOOK_SECRET, body));
	}

	private ResultActions webhook(String body, String signature) throws Exception {
		return mvc.perform(post("/api/v1/webhooks/payouts").header("X-Payout-Signature", signature)
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private static String event(String id, String type, String reference, String providerReference, String amount) {
		return """
				{"id":"%s","type":"%s","withdrawalReference":"%s",%s"amount":"%s","currency":"INR","reason":"Account closed"}"""
			.formatted(id, type, reference, providerReference == null ? "" : "\"providerReference\":\"" + providerReference + "\",",
					amount);
	}

	private void charges(String flat, String percent, String tax) {
		jdbc.update("UPDATE withdrawal_settings SET fee_flat = ?, fee_percent = ?, tax_percent = ? WHERE id = 1",
				new BigDecimal(flat), new BigDecimal(percent), new BigDecimal(tax));
	}

	private static void refused(ResultActions result, String message) throws Exception {
		result.andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.message").value(containsString(message)));
	}

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

	private BigDecimal wallet(Account investor, String accountType) {
		return jdbc.queryForObject("""
				SELECT coalesce(sum(CASE WHEN e.direction = 'CREDIT' THEN e.amount ELSE -e.amount END), 0)
				FROM ledger_entries e JOIN ledger_accounts a ON a.id = e.account_id
				WHERE a.owner_user_id = ? AND a.account_type = ?""", BigDecimal.class, investor.id(), accountType);
	}

	/** What the payout of withdrawal {@code id} credited to a platform account. */
	private BigDecimal posted(String id, String accountType) {
		return jdbc.queryForObject("""
				SELECT coalesce(sum(e.amount), 0) FROM ledger_entries e
				JOIN ledger_accounts a ON a.id = e.account_id JOIN ledger_transactions t ON t.id = e.transaction_id
				WHERE t.transaction_type = 'WITHDRAWAL_PAYOUT' AND t.reference = ? AND a.account_type = ? AND e.direction = 'CREDIT'""",
				BigDecimal.class, id, accountType);
	}

	private int notifications(Account investor, String type) {
		return jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ? AND type = ?", Integer.class,
				investor.id(), type);
	}

	private String statusOf(String id) {
		return jdbc.queryForObject("SELECT status FROM withdrawals WHERE id = ?::uuid", String.class, id);
	}

	private String reference(String id) {
		return jdbc.queryForObject("SELECT reference FROM withdrawals WHERE id = ?::uuid", String.class, id);
	}

	private int count(Account investor) {
		return jdbc.queryForObject("SELECT count(*) FROM withdrawals WHERE user_id = ?", Integer.class, investor.id());
	}

	private static String key() {
		return UUID.randomUUID().toString();
	}

}
