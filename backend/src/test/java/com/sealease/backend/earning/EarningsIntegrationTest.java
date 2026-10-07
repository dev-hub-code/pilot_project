package com.sealease.backend.earning;

import com.sealease.backend.earning.service.PayoutService;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Monthly payouts (rent plus capital returned), the ledger, and maturity at the end of the tenure. */
@IntegrationTest
class EarningsIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TransactionTemplate transactions;

	@Autowired
	private PayoutService payouts;

	private TestApi api;
	private OfferingFixtures offerings;
	private InvestorFixtures investors;

	@BeforeEach
	void setUp() throws Exception {
		api = new TestApi(mvc, jdbc);
		offerings = new OfferingFixtures(mvc, api.admin());
		investors = new InvestorFixtures(mvc, jdbc, api);
	}

	// ------------------------------------------------------------------------------ payouts

	@Test
	void payoutsAreCreditedMonthlyAndTheContainerReturnsToStockAfterTheLast() throws Exception {
		// ₹50,000 container at 2% rent over 16 months: ₹1,000 rent + ₹3,125 capital = ₹4,125 a month.
		Account investor = investors.approvedInvestor();
		String orderId = investors.invest(investor, offerings.plan(1), 1);
		UUID holding = jdbc.queryForObject("SELECT id FROM holdings WHERE order_id = ?::uuid", UUID.class, orderId);
		UUID container = jdbc.queryForObject("SELECT container_id FROM holdings WHERE id = ?", UUID.class, holding);

		// Nothing is due on the day of purchase: payouts are paid in arrears.
		payouts.payDue();
		assertThat(paid(holding)).isZero();

		// Three months later.
		backdate(holding, 3);
		assertThat(payouts.payDue()).isGreaterThanOrEqualTo(3);
		assertThat(paid(holding)).isEqualTo(3);
		payouts.payDue();
		assertThat(paid(holding)).as("paying again changes nothing").isEqualTo(3);

		mvc.perform(get("/api/v1/earnings/summary").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.balances[0].amount").value("12375.00"))
			.andExpect(jsonPath("$.rentPaid[0].amount").value("3000.00"))
			.andExpect(jsonPath("$.capitalReturned[0].amount").value("9375.00"))
			.andExpect(jsonPath("$.nextPayout.total.amount").value("4125.00"))
			.andExpect(jsonPath("$.holdings[0].paid").value(3))
			.andExpect(jsonPath("$.holdings[0].installments").value(16))
			.andExpect(jsonPath("$.holdings[0].received.amount").value("12375.00"));
		mvc.perform(get("/api/v1/earnings").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.totalElements").value(16))
			.andExpect(jsonPath("$.content[0].status").value("SCHEDULED"))
			.andExpect(jsonPath("$.content[0].installmentNumber").value(16));
		mvc.perform(get("/api/v1/earnings").param("status", "PAID").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.totalElements").value(3))
			.andExpect(jsonPath("$.content[0].rent.amount").value("1000.00"))
			.andExpect(jsonPath("$.content[0].capital.amount").value("3125.00"))
			.andExpect(jsonPath("$.content[0].total.amount").value("4125.00"))
			.andExpect(jsonPath("$.content[0].containerNumber").isNotEmpty());

		// Each payout is one balanced ledger transaction: rent and capital are platform expenses.
		UUID first = jdbc.queryForObject("SELECT id FROM payout_installments WHERE holding_id = ? AND installment_number = 1",
				UUID.class, holding);
		assertThat(entries(first.toString())).containsExactlyInAnyOrder(
				"DEBIT PLATFORM_RENT_EXPENSE 1000.0000", "DEBIT PLATFORM_CAPITAL_RETURNS 3125.0000",
				"CREDIT INVESTOR_EARNINGS 4125.0000");
		assertThat(api.auditCount("PAYOUT_PAID", first)).isEqualTo(1);
		assertThat(outboxCount("earning.created", first.toString())).isEqualTo(1);

		// The tenure ends: the last payout matures the holding and the container goes back to stock.
		assertThat(jdbc.queryForObject("SELECT status FROM containers WHERE id = ?", String.class, container)).isEqualTo("ON_LEASE");
		backdate(holding, 16);
		payouts.payDue();
		assertThat(paid(holding)).isEqualTo(16);
		assertThat(jdbc.queryForObject("SELECT status FROM holdings WHERE id = ?", String.class, holding)).isEqualTo("MATURED");
		assertThat(jdbc.queryForObject("SELECT status FROM containers WHERE id = ?", String.class, container)).isEqualTo("AVAILABLE");
		assertThat(api.auditCount("HOLDING_MATURED", holding)).isEqualTo(1);
		// 16 × ₹4,125: 32% of the price in rent and the whole price back.
		mvc.perform(get("/api/v1/earnings/summary").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.balances[0].amount").value("66000.00"))
			.andExpect(jsonPath("$.rentPaid[0].amount").value("16000.00"))
			.andExpect(jsonPath("$.capitalReturned[0].amount").value("50000.00"))
			.andExpect(jsonPath("$.nextPayout").doesNotExist());
		mvc.perform(get("/api/v1/portfolio").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.activeHoldings").value(0))
			.andExpect(jsonPath("$.holdings[0].status").value("MATURED"));
		assertLedgerBalanced();
	}

	@Test
	void financeCanRunDuePayoutsNow() throws Exception {
		Account investor = investors.approvedInvestor();
		String orderId = investors.invest(investor, offerings.plan(1), 1);
		UUID holding = jdbc.queryForObject("SELECT id FROM holdings WHERE order_id = ?::uuid", UUID.class, orderId);
		backdate(holding, 1);
		Account finance = api.staff("FINANCE");

		mvc.perform(get("/api/v1/admin/payouts/due").header(HttpHeaders.AUTHORIZATION, finance.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].count").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
		mvc.perform(get("/api/v1/admin/payouts").param("holdingId", holding.toString())
				.header(HttpHeaders.AUTHORIZATION, finance.bearer()))
			.andExpect(jsonPath("$.totalElements").value(16))
			.andExpect(jsonPath("$.content[0].userId").value(investor.id().toString()));

		run(investor).andExpect(status().isForbidden());
		run(api.staff("SUPPORT")).andExpect(status().isForbidden());
		run(finance).andExpect(status().isOk()).andExpect(jsonPath("$.paid").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
		assertThat(paid(holding)).isEqualTo(1);

		// Investors see their own payouts only.
		mvc.perform(get("/api/v1/earnings").header(HttpHeaders.AUTHORIZATION, investors.approvedInvestor().bearer()))
			.andExpect(jsonPath("$.totalElements").value(0));
	}

	// ------------------------------------------------------------------------------- ledger

	@Test
	void adjustmentsAreIdempotentAndNeverOverdrawABalance() throws Exception {
		Account investor = investors.approvedInvestor();
		Account finance = api.staff("FINANCE");
		String key = UUID.randomUUID().toString();

		adjust(finance, investor, "CREDIT", "25", key).andExpect(status().isCreated())
			.andExpect(jsonPath("$.balance.amount").value("25.00"));
		adjust(finance, investor, "CREDIT", "25", key).andExpect(status().isCreated())
			.andExpect(jsonPath("$.balance.amount").value("25.00"));
		adjust(finance, investor, "DEBIT", "30", UUID.randomUUID().toString())
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.message").value(containsString("only ₹25.00")));
		adjust(finance, investor, "DEBIT", "25", UUID.randomUUID().toString())
			.andExpect(jsonPath("$.balance.amount").value("0.00"));
		adjust(finance, finance, "CREDIT", "1", UUID.randomUUID().toString()).andExpect(status().isForbidden());
		adjust(investor, investor, "CREDIT", "1", UUID.randomUUID().toString()).andExpect(status().isForbidden());

		mvc.perform(get("/api/v1/admin/ledger/accounts").param("userId", investor.id().toString())
				.header(HttpHeaders.AUTHORIZATION, finance.bearer()))
			.andExpect(jsonPath("$.content[0].accountType").value("INVESTOR_EARNINGS"))
			.andExpect(jsonPath("$.content[0].balance.amount").value("0.00"));
		assertLedgerBalanced();
	}

	@Test
	void theDatabaseRejectsUnbalancedTransactionsAndChangesToHistory() throws Exception {
		adjust(api.staff("FINANCE"), investors.approvedInvestor(), "CREDIT", "5", UUID.randomUUID().toString())
			.andExpect(status().isCreated());

		assertThatThrownBy(() -> transactions.executeWithoutResult(s -> {
			UUID account = jdbc.queryForObject("""
					INSERT INTO ledger_accounts (id, account_type, currency, created_at)
					VALUES (gen_random_uuid(), 'RENTAL_CASH', 'CHF', now())
					ON CONFLICT DO NOTHING RETURNING id
					""", UUID.class);
			UUID transaction = UUID.randomUUID();
			jdbc.update("""
					INSERT INTO ledger_transactions (id, transaction_type, reference, currency, description, created_at)
					VALUES (?, 'ADJUSTMENT', ?, 'CHF', 'Unbalanced', now())
					""", transaction, transaction.toString());
			jdbc.update("""
					INSERT INTO ledger_entries (id, transaction_id, account_id, direction, amount, created_at)
					VALUES (gen_random_uuid(), ?, ?, 'DEBIT', 10, now())
					""", transaction, account);
		})).hasMessageContaining("unbalanced");

		assertThatThrownBy(() -> jdbc.update("UPDATE ledger_entries SET amount = amount + 1"))
			.hasMessageContaining("append-only");
	}

	// ------------------------------------------------------------------------------ helpers

	/** Moves the holding's schedule into the past, as if the lease had started {@code months} months ago. */
	private void backdate(UUID holding, int months) {
		jdbc.update("""
				UPDATE payout_installments
				SET due_on = (now() AT TIME ZONE 'UTC')::date - make_interval(months => ? - installment_number)
				WHERE holding_id = ?""", months, holding);
	}

	private int paid(UUID holding) {
		Integer count = jdbc.queryForObject("SELECT count(*) FROM payout_installments WHERE holding_id = ? AND status = 'PAID'",
				Integer.class, holding);
		return count == null ? 0 : count;
	}

	/** The ledger entries of a payout, as "DIRECTION ACCOUNT_TYPE amount". */
	private List<String> entries(String installmentId) {
		return jdbc.queryForList("""
				SELECT e.direction || ' ' || a.account_type || ' ' || e.amount
				FROM ledger_entries e
				JOIN ledger_transactions t ON t.id = e.transaction_id
				JOIN ledger_accounts a ON a.id = e.account_id
				WHERE t.transaction_type = 'INVESTOR_PAYOUT' AND t.reference = ?""", String.class, installmentId);
	}

	private ResultActions run(Account actor) throws Exception {
		return mvc.perform(post("/api/v1/admin/payouts/run").header(HttpHeaders.AUTHORIZATION, actor.bearer()));
	}

	private ResultActions adjust(Account actor, Account investor, String direction, String amount, String key)
			throws Exception {
		return mvc.perform(post("/api/v1/admin/ledger/adjustments").header(HttpHeaders.AUTHORIZATION, actor.bearer())
			.header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content("""
					{"userId":"%s","currency":"INR","direction":"%s","amount":"%s","reason":"Goodwill credit"}
					""".formatted(investor.id(), direction, amount)));
	}

	private void assertLedgerBalanced() {
		List<Boolean> balanced = jdbc.queryForList("""
				SELECT sum(CASE direction WHEN 'DEBIT' THEN amount ELSE -amount END) = 0
				FROM ledger_entries e JOIN ledger_transactions t ON t.id = e.transaction_id
				GROUP BY t.currency
				""", Boolean.class);
		assertThat(balanced).isNotEmpty().containsOnly(true);
	}

	private int outboxCount(String topic, String aggregateId) {
		Integer count = jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE topic = ? AND aggregate_id = ?",
				Integer.class, topic, aggregateId);
		return count == null ? 0 : count;
	}

}
