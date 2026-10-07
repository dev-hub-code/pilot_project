package com.sealease.backend.withdrawal;

import com.jayway.jsonpath.JsonPath;
import com.sealease.backend.support.IntegrationTest;
import com.sealease.backend.support.InvestorFixtures;
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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase 8: withdrawal requests, approvals, payout batches and reconciliation. */
@IntegrationTest
class WithdrawalIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	private TestApi api;
	private InvestorFixtures investors;
	private Account finance;

	@BeforeEach
	void setUp() throws Exception {
		api = new TestApi(mvc, jdbc);
		investors = new InvestorFixtures(mvc, jdbc, api);
		finance = api.staff("FINANCE");
	}

	private record Investor(Account account, String bankAccountId) {
	}

	// ---------------------------------------------------------------------------- requests

	@Test
	void requestingReservesTheBalanceAndIsIdempotent() throws Exception {
		Investor investor = funded("INR", "500", "Test Investor");

		request(investor, "40", key()).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(containsString("minimum withdrawal is ₹50.00")));
		request(investor, "600", key()).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.message").value(containsString("available balance is ₹500.00")));

		String key = key();
		String id = JsonPath.read(request(investor, "300", key).andExpect(status().isCreated())
			.andExpect(jsonPath("$.reference").value(matchesPattern("WD-\\d{6,}")))
			.andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
			.andExpect(jsonPath("$.requiredApprovals").value(1))
			.andExpect(jsonPath("$.bankAccountMasked").value(matchesPattern("•••• \\w{4}")))
			.andReturn().getResponse().getContentAsString(), "$.id");
		balance(investor).andExpect(jsonPath("$.balances[0].amount").value("200.00"));

		request(investor, "300", key).andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(id));
		request(investor, "250", key).andExpect(status().isConflict());
		request(investor, "100", key()).andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value(containsString("already have a INR withdrawal")));

		approve(investor.account(), id).andExpect(status().isForbidden());
		approve(finance, id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
		approve(finance, id).andExpect(status().isConflict());
		assertThat(outboxCount("withdrawal.requested", id)).isEqualTo(1);
		assertThat(outboxCount("withdrawal.approved", id)).isEqualTo(1);

		mvc.perform(get("/api/v1/withdrawals").header(HttpHeaders.AUTHORIZATION, investor.account().bearer()))
			.andExpect(jsonPath("$.content[0].id").value(id))
			.andExpect(jsonPath("$.content[0].firstApprovedBy").doesNotExist());
	}

	@Test
	void onlyVerifiedAccountsOfTheInvestorCanBePaid() throws Exception {
		Account investor = investors.approvedInvestor();
		String unverified = addBankAccount(investor, "INR", "Test Investor");
		credit(investor, "INR", "500");
		mvc.perform(post("/api/v1/withdrawals").header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON)
				.content(body(unverified, "100")))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.message").value(containsString("verified bank accounts")));

		Investor other = funded("INR", "100", "Test Investor");
		mvc.perform(post("/api/v1/withdrawals").header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON)
				.content(body(other.bankAccountId(), "100")))
			.andExpect(status().isNotFound());
	}

	// --------------------------------------------------------------------------- approvals

	@Test
	void largeWithdrawalsNeedTwoDifferentApprovers() throws Exception {
		Investor investor = funded("INR", "20000", "Test Investor");
		String id = id(request(investor, "15000", key()).andExpect(jsonPath("$.requiredApprovals").value(2)));
		Account second = api.staff("FINANCE");

		approve(finance, id).andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
			.andExpect(jsonPath("$.approvals").value(1));
		approve(finance, id).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value(containsString("second approver")));
		approve(second, id).andExpect(jsonPath("$.status").value("APPROVED"))
			.andExpect(jsonPath("$.approvals").value(2));
	}

	@Test
	void cancellingOrRejectingReturnsTheMoney() throws Exception {
		Investor investor = funded("INR", "1000", "Test Investor");

		String first = id(request(investor, "400", key()));
		mvc.perform(post("/api/v1/withdrawals/{id}/cancel", first).header(HttpHeaders.AUTHORIZATION, investor.account().bearer()))
			.andExpect(jsonPath("$.status").value("CANCELLED"));
		balance(investor).andExpect(jsonPath("$.balances[0].amount").value("1000.00"));

		String second = id(request(investor, "400", key()));
		reject(finance, second).andExpect(jsonPath("$.status").value("REJECTED"))
			.andExpect(jsonPath("$.rejectionReason").value("Account under review"));
		balance(investor).andExpect(jsonPath("$.balances[0].amount").value("1000.00"));
		reject(finance, second).andExpect(status().isUnprocessableContent());

		String third = id(request(investor, "400", key()));
		approve(finance, third).andExpect(status().isOk());
		mvc.perform(post("/api/v1/withdrawals/{id}/cancel", third).header(HttpHeaders.AUTHORIZATION, investor.account().bearer()))
			.andExpect(status().isUnprocessableContent());
		assertThat(api.auditCount("WITHDRAWAL_REJECTED", second)).isEqualTo(1);
		assertLedgerBalanced();
	}

	// ----------------------------------------------------------------------------- payouts

	@Test
	void approvedWithdrawalsArePaidInBatchesAndReconciled() throws Exception {
		// One currency for the whole platform: a batch takes every approved withdrawal, so clear out
		// any left by other tests (rejecting returns their money, keeping the ledger consistent).
		for (String leftover : jdbc.queryForList("SELECT id::text FROM withdrawals WHERE status = 'APPROVED'", String.class)) {
			reject(finance, leftover).andExpect(status().isOk());
		}
		java.math.BigDecimal inTransitBefore = inTransit();
		Investor alice = funded("INR", "1000", "Alice Example");
		Investor bob = funded("INR", "1000", "Bob Example");
		Investor carol = funded("INR", "1000", "=HYPERLINK(\\\"http://x\\\")");
		String a = approved(alice, "100");
		String b = approved(bob, "200");
		String c = approved(carol, "300");
		Account investorOnly = alice.account();

		mvc.perform(post("/api/v1/admin/withdrawal-batches").header(HttpHeaders.AUTHORIZATION, investorOnly.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"currency\":\"INR\"}"))
			.andExpect(status().isForbidden());
		String draft = batchId(createBatch().andExpect(status().isCreated())
			.andExpect(jsonPath("$.batch.itemCount").value(3))
			.andExpect(jsonPath("$.batch.total.amount").value("600.00")));
		createBatch().andExpect(status().isUnprocessableContent());
		reject(finance, a).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.message").value(containsString("Cancel its batch first")));
		batchAction(draft, "cancel").andExpect(jsonPath("$.batch.status").value("CANCELLED"));

		String batch = batchId(createBatch().andExpect(status().isCreated()));
		item(batch, a, "paid", "{\"payoutReference\":\"BANK-1\"}").andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.message").value(containsString("sent to the bank first")));

		String csv = mvc.perform(get("/api/v1/admin/withdrawal-batches/{id}/file", batch)
				.header(HttpHeaders.AUTHORIZATION, finance.bearer()))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString(".csv")))
			.andReturn().getResponse().getContentAsString();
		assertThat(csv.lines()).hasSize(4);
		assertThat(csv).startsWith("reference,beneficiary_name,account_number")
			.contains("\"Alice Example\"", "\"026009593\"", ",100.00,INR,", "\"SeaLease withdrawal WD-")
			// A beneficiary name starting with "=" must not become a spreadsheet formula.
			.contains("\"'=HYPERLINK(");
		assertThat(api.auditCount("WITHDRAWAL_BATCH_EXPORTED", batch)).isEqualTo(1);

		batchAction(batch, "sent").andExpect(jsonPath("$.batch.status").value("SENT"))
			.andExpect(jsonPath("$.batch.outstanding").value(3))
			.andExpect(jsonPath("$.items[0].status").value("PROCESSING"));
		batchAction(batch, "cancel").andExpect(status().isUnprocessableContent());

		item(batch, a, "paid", "{\"payoutReference\":\"BANK-1\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.batch.paid").value(1));
		item(batch, a, "paid", "{}").andExpect(status().isConflict());
		item(batch, b, "failed", "{\"reason\":\"Account closed at the beneficiary bank\"}")
			.andExpect(jsonPath("$.batch.failed").value(1));
		balance(bob).andExpect(jsonPath("$.balances[0].amount").value("1000.00"));
		mvc.perform(post("/api/v1/admin/withdrawal-batches/{id}/settle", batch).header(HttpHeaders.AUTHORIZATION, finance.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"payoutReference\":\"BANK-STATEMENT-7\"}"))
			.andExpect(jsonPath("$.batch.status").value("CLOSED"))
			.andExpect(jsonPath("$.batch.paid").value(2))
			.andExpect(jsonPath("$.batch.outstanding").value(0));

		balance(alice).andExpect(jsonPath("$.balances[0].amount").value("900.00"));
		balance(carol).andExpect(jsonPath("$.balances[0].amount").value("700.00"));
		mvc.perform(get("/api/v1/withdrawals").header(HttpHeaders.AUTHORIZATION, carol.account().bearer()))
			.andExpect(jsonPath("$.content[0].status").value("PAID"))
			.andExpect(jsonPath("$.content[0].payoutReference").value("BANK-STATEMENT-7"));
		assertThat(outboxCount("withdrawal.completed", a)).isEqualTo(1);
		assertThat(outboxCount("withdrawal.failed", b)).isEqualTo(1);
		// Nothing of this batch is left in transit once every item is reconciled.
		assertThat(inTransit()).isEqualByComparingTo(inTransitBefore);
		assertLedgerBalanced();
	}

	// ----------------------------------------------------------------------------- helpers

	private java.math.BigDecimal inTransit() {
		return jdbc.queryForObject("""
				SELECT coalesce(sum(CASE e.direction WHEN 'CREDIT' THEN e.amount ELSE -e.amount END), 0)
				FROM ledger_entries e JOIN ledger_accounts acc ON acc.id = e.account_id
				WHERE acc.account_type = 'WITHDRAWALS_IN_TRANSIT'
				""", java.math.BigDecimal.class);
	}

	/** An approved investor with a verified bank account in the currency and a credited balance. */
	private Investor funded(String currency, String balance, String holder) throws Exception {
		Account investor = investors.approvedInvestor();
		String bankAccountId = addBankAccount(investor, currency, holder);
		mvc.perform(post("/api/v1/admin/bank-accounts/{id}/verify", bankAccountId).header(HttpHeaders.AUTHORIZATION, finance.bearer()))
			.andExpect(status().isOk());
		credit(investor, currency, balance);
		return new Investor(investor, bankAccountId);
	}

	private String addBankAccount(Account investor, String currency, String holder) throws Exception {
		String number = String.valueOf(ThreadLocalRandom.current().nextLong(100_000_000_000L, 999_999_999_999L));
		return JsonPath.read(mvc.perform(post("/api/v1/users/me/bank-accounts").header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"accountHolderName":"%s","bankName":"Test Bank","country":"US","currency":"%s",
						 "accountNumber":"%s","routingCode":"026009593"}
						""".formatted(holder, currency, number)))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
	}

	private void credit(Account investor, String currency, String amount) throws Exception {
		mvc.perform(post("/api/v1/admin/ledger/adjustments").header(HttpHeaders.AUTHORIZATION, finance.bearer())
				.header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON).content("""
						{"userId":"%s","currency":"%s","direction":"CREDIT","amount":"%s","reason":"Test funding"}
						""".formatted(investor.id(), currency, amount)))
			.andExpect(status().isCreated());
	}

	private String approved(Investor investor, String amount) throws Exception {
		String id = id(request(investor, amount, key()).andExpect(status().isCreated()));
		approve(finance, id).andExpect(jsonPath("$.status").value("APPROVED"));
		return id;
	}

	private ResultActions request(Investor investor, String amount, String key) throws Exception {
		return mvc.perform(post("/api/v1/withdrawals").header(HttpHeaders.AUTHORIZATION, investor.account().bearer())
			.header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
			.content(body(investor.bankAccountId(), amount)));
	}

	private static String body(String bankAccountId, String amount) {
		return "{\"bankAccountId\":\"" + bankAccountId + "\",\"amount\":\"" + amount + "\"}";
	}

	private ResultActions approve(Account actor, String id) throws Exception {
		return mvc.perform(post("/api/v1/admin/withdrawals/{id}/approve", id).header(HttpHeaders.AUTHORIZATION, actor.bearer()));
	}

	private ResultActions reject(Account actor, String id) throws Exception {
		return mvc.perform(post("/api/v1/admin/withdrawals/{id}/reject", id).header(HttpHeaders.AUTHORIZATION, actor.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Account under review\"}"));
	}

	private ResultActions createBatch() throws Exception {
		return mvc.perform(post("/api/v1/admin/withdrawal-batches").header(HttpHeaders.AUTHORIZATION, finance.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"currency\":\"INR\"}"));
	}

	private ResultActions batchAction(String batchId, String action) throws Exception {
		return mvc.perform(post("/api/v1/admin/withdrawal-batches/{id}/" + action, batchId)
			.header(HttpHeaders.AUTHORIZATION, finance.bearer()));
	}

	private ResultActions item(String batchId, String withdrawalId, String outcome, String json) throws Exception {
		return mvc.perform(post("/api/v1/admin/withdrawal-batches/{b}/items/{w}/" + outcome, batchId, withdrawalId)
			.header(HttpHeaders.AUTHORIZATION, finance.bearer()).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private ResultActions balance(Investor investor) throws Exception {
		return mvc.perform(get("/api/v1/earnings/summary").header(HttpHeaders.AUTHORIZATION, investor.account().bearer()));
	}

	private static String id(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
	}

	private static String batchId(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.batch.id");
	}

	private static String key() {
		return UUID.randomUUID().toString();
	}

	private int outboxCount(String topic, String aggregateId) {
		Integer count = jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE topic = ? AND aggregate_id = ?",
				Integer.class, topic, aggregateId);
		return count == null ? 0 : count;
	}

	private void assertLedgerBalanced() {
		List<Boolean> balanced = jdbc.queryForList("""
				SELECT sum(CASE direction WHEN 'DEBIT' THEN amount ELSE -amount END) = 0
				FROM ledger_entries e JOIN ledger_transactions t ON t.id = e.transaction_id GROUP BY t.currency
				""", Boolean.class);
		assertThat(balanced).containsOnly(true);
	}

}
