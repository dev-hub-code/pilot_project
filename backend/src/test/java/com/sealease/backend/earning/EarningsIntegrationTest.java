package com.sealease.backend.earning;

import com.jayway.jsonpath.JsonPath;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Monthly payouts (rent only on current plans), the ledger, and maturity at the end of the tenure. */
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
	void rentIsCreditedDailyAndTheContainerReturnsToStockAfterTheLastDay() throws Exception {
		// ₹50,000 container at 2% rent, leased for 1 year (12 months): ₹1,000 a month. Plans pay rent only.
		Account investor = investors.approvedInvestor();
		String orderId = investors.invest(investor, offerings.plan(), 1);
		UUID holding = jdbc.queryForObject("SELECT id FROM holdings WHERE order_id = ?::uuid", UUID.class, orderId);
		UUID container = jdbc.queryForObject("SELECT container_id FROM holdings WHERE id = ?", UUID.class, holding);

		// Nothing on the day of purchase: a day is credited once it has ended.
		assertThat(payouts.creditDue()).isZero();
		assertThat(credits(holding)).isZero();

		// Three months later: every day since the lease started is credited, one ledger transaction each.
		int days = backdate(holding, 3);
		assertThat(payouts.creditDue()).isEqualTo(days);
		assertThat(credits(holding)).isEqualTo(days);
		assertThat(payouts.creditDue()).as("crediting again changes nothing").isZero();
		BigDecimal credited = expectedCredited(holding);
		assertThat(credited).isGreaterThan(new BigDecimal("2900")).isLessThan(new BigDecimal("3100"));
		assertThat(jdbc.queryForObject("SELECT sum(rent_amount) FROM rent_credits WHERE holding_id = ?", BigDecimal.class,
				holding)).isEqualByComparingTo(credited);
		int paid = paid(holding);
		assertThat(paid).as("whole periods credited").isBetween(2, 4);

		mvc.perform(get("/api/v1/earnings/summary").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.balances[0].amount").value(credited.setScale(2).toPlainString()))
			.andExpect(jsonPath("$.capitalReturned.length()").value(0))
			.andExpect(jsonPath("$.holdings[0].paid").value(paid))
			.andExpect(jsonPath("$.holdings[0].installments").value(installments(holding)));
		mvc.perform(get("/api/v1/earnings").param("status", "PAID").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.totalElements").value(paid))
			.andExpect(jsonPath("$.content[0].capital.amount").value("0.00"))
			.andExpect(jsonPath("$.content[0].containerNumber").isNotEmpty());

		// Each day is one balanced ledger transaction: the rent is a platform expense (no capital posting).
		Map<String, Object> day = jdbc.queryForMap("""
				SELECT c.installment_id, c.day, c.rent_amount FROM rent_credits c WHERE c.holding_id = ? ORDER BY c.day LIMIT 1""",
				holding);
		String rent = ((BigDecimal) day.get("rent_amount")).setScale(4).toPlainString();
		assertThat(entries(day.get("installment_id") + ":" + day.get("day"))).containsExactlyInAnyOrder(
				"DEBIT PLATFORM_RENT_EXPENSE " + rent, "CREDIT INVESTOR_EARNINGS " + rent);
		UUID first = jdbc.queryForObject("SELECT id FROM payout_installments WHERE holding_id = ? AND installment_number = 1",
				UUID.class, holding);
		assertThat(api.auditCount("PAYOUT_PAID", first)).isEqualTo(1);
		assertThat(outboxCount("earning.created", first.toString())).isEqualTo(1);

		// The lease ends: the last day matures the holding and the container goes back to stock.
		assertThat(jdbc.queryForObject("SELECT status FROM containers WHERE id = ?", String.class, container)).isEqualTo("ON_LEASE");
		backdate(holding, 12);
		payouts.creditDue();
		assertThat(paid(holding)).isEqualTo(installments(holding));
		assertThat(jdbc.queryForObject("SELECT status FROM holdings WHERE id = ?", String.class, holding)).isEqualTo("MATURED");
		assertThat(jdbc.queryForObject("SELECT status FROM containers WHERE id = ?", String.class, container)).isEqualTo("AVAILABLE");
		assertThat(api.auditCount("HOLDING_MATURED", holding)).isEqualTo(1);
		// The whole lease: 12 × ₹1,000, 24% of the price in rent, and nothing more.
		mvc.perform(get("/api/v1/earnings/summary").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.balances[0].amount").value("12000.00"))
			.andExpect(jsonPath("$.rentPaid[0].amount").value("12000.00"))
			.andExpect(jsonPath("$.nextPayout").doesNotExist());
		mvc.perform(get("/api/v1/portfolio").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.activeHoldings").value(0))
			.andExpect(jsonPath("$.holdings[0].status").value("MATURED"));
		assertLedgerBalanced();
	}

	@Test
	void periodsAreCalendarMonthsAndAPartMonthEarnsCommissionInProportion() throws Exception {
		LocalDate today = LocalDate.now(ZoneOffset.UTC);
		Account referrer = investors.approvedInvestor();
		String code = JsonPath.read(mvc.perform(get("/api/v1/referrals/me").header(HttpHeaders.AUTHORIZATION, referrer.bearer()))
			.andReturn().getResponse().getContentAsString(), "$.code");
		Account investor = investors.approve(api.register(code));
		// ₹50,000 container at 2% rent, leased for 1 year: ₹1,000 rent a whole month.
		String orderId = investors.invest(investor, offerings.plan(), 1);
		UUID holding = jdbc.queryForObject("SELECT id FROM holdings WHERE order_id = ?::uuid", UUID.class, orderId);

		// The first period runs to the 1st of next month: its share is its days ÷ the month's days.
		boolean onTheFirst = today.getDayOfMonth() == 1;
		LocalDate firstEnd = today.withDayOfMonth(1).plusMonths(1);
		Map<String, Object> first = jdbc.queryForMap("""
				SELECT period_start, period_end, month_share, rent_amount, installment_count
				FROM payout_installments WHERE holding_id = ? AND installment_number = 1""", holding);
		BigDecimal share = (BigDecimal) first.get("month_share");
		assertThat(first.get("installment_count")).isEqualTo(onTheFirst ? 12 : 13);
		assertThat(first.get("period_start").toString()).isEqualTo(today.toString());
		assertThat(first.get("period_end").toString()).isEqualTo(firstEnd.toString());
		assertThat(share).isEqualByComparingTo(BigDecimal.valueOf(firstEnd.toEpochDay() - today.toEpochDay())
			.divide(BigDecimal.valueOf(today.lengthOfMonth()), 6, RoundingMode.HALF_UP));
		assertThat(jdbc.queryForObject("SELECT sum(month_share) FROM payout_installments WHERE holding_id = ?",
				BigDecimal.class, holding)).isEqualByComparingTo("12");
		assertThat(jdbc.queryForObject("SELECT sum(rent_amount) FROM payout_installments WHERE holding_id = ?",
				BigDecimal.class, holding)).isEqualByComparingTo("12000");
		// Every later period but the last starts on the 1st.
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM payout_installments WHERE holding_id = ? AND installment_number > 1
				AND extract(day FROM period_start) <> 1""", Integer.class, holding)).isZero();

		// Once the first period is fully credited, the referrer earns its share of a month's 2% commission.
		backdate(holding, firstEnd.toEpochDay() - today.toEpochDay());
		payouts.creditDue();
		assertThat(jdbc.queryForObject("SELECT status FROM payout_installments WHERE holding_id = ? AND installment_number = 1",
				String.class, holding)).isEqualTo("PAID");
		BigDecimal base = new BigDecimal("50000").multiply(share).setScale(2, RoundingMode.DOWN);
		mvc.perform(get("/api/v1/referrals/earnings").header(HttpHeaders.AUTHORIZATION, referrer.bearer()))
			.andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.content[0].base.amount").value(base.toPlainString()))
			.andExpect(jsonPath("$.content[0].amount.amount")
				.value(base.multiply(new BigDecimal("0.02")).setScale(2, RoundingMode.DOWN).toPlainString()));
		mvc.perform(get("/api/v1/earnings/summary").header(HttpHeaders.AUTHORIZATION, referrer.bearer()))
			.andExpect(jsonPath("$.wallet[?(@.balance == 'REFERRAL')].amount.amount")
				.value(base.multiply(new BigDecimal("0.02")).setScale(2, RoundingMode.DOWN).toPlainString()));
		assertLedgerBalanced();
	}

	@Test
	void financeCanRunDuePayoutsNow() throws Exception {
		Account investor = investors.approvedInvestor();
		String orderId = investors.invest(investor, offerings.plan(), 1);
		UUID holding = jdbc.queryForObject("SELECT id FROM holdings WHERE order_id = ?::uuid", UUID.class, orderId);
		backdate(holding, 1);
		Account finance = api.staff("FINANCE");

		mvc.perform(get("/api/v1/admin/payouts/due").header(HttpHeaders.AUTHORIZATION, finance.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].count").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
		mvc.perform(get("/api/v1/admin/payouts").param("holdingId", holding.toString())
				.header(HttpHeaders.AUTHORIZATION, finance.bearer()))
			.andExpect(jsonPath("$.totalElements").value(installments(holding)))
			.andExpect(jsonPath("$.content[0].userId").value(investor.id().toString()));

		run(investor).andExpect(status().isForbidden());
		run(api.staff("SUPPORT")).andExpect(status().isForbidden());
		run(finance).andExpect(status().isOk()).andExpect(jsonPath("$.credited").value(org.hamcrest.Matchers.greaterThanOrEqualTo(28)));
		assertThat(paid(holding)).isGreaterThanOrEqualTo(1);

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

	/**
	 * Moves the holding's schedule into the past, as if the lease had started {@code months} months ago; every date
	 * moves by the same number of days, so periods keep their lengths.
	 *
	 * @return the days from the lease start to today
	 */
	private int backdate(UUID holding, int months) {
		LocalDate today = LocalDate.now(ZoneOffset.UTC);
		return backdate(holding, today.toEpochDay() - today.minusMonths(months).toEpochDay());
	}

	/** As {@link #backdate(UUID, int)}, the lease starting {@code days} days ago. */
	private int backdate(UUID holding, long days) {
		LocalDate start = jdbc.queryForObject(
				"SELECT period_start FROM payout_installments WHERE holding_id = ? AND installment_number = 1", LocalDate.class, holding);
		long shift = start.toEpochDay() - (LocalDate.now(ZoneOffset.UTC).toEpochDay() - days);
		jdbc.update("""
				UPDATE payout_installments
				SET period_start = period_start - ?, period_end = period_end - ?, due_on = due_on - ?,
				    credited_through = credited_through - ?
				WHERE holding_id = ?""", (int) shift, (int) shift, (int) shift, (int) shift, holding);
		return (int) days;
	}

	/** What daily crediting should have credited to the holding by today: each period's rent × its elapsed share. */
	private BigDecimal expectedCredited(UUID holding) {
		return jdbc.queryForObject("""
				SELECT sum(CASE WHEN period_end <= today THEN rent_amount
				                WHEN period_start >= today THEN 0
				                ELSE round(rent_amount * (today - period_start) / (period_end - period_start), 2) END)
				FROM payout_installments, (SELECT (now() AT TIME ZONE 'UTC')::date AS today) t
				WHERE holding_id = ?""", BigDecimal.class, holding);
	}

	private int credits(UUID holding) {
		Integer count = jdbc.queryForObject("SELECT count(*) FROM rent_credits WHERE holding_id = ?", Integer.class, holding);
		return count == null ? 0 : count;
	}

	private int installments(UUID holding) {
		Integer count = jdbc.queryForObject("SELECT count(*) FROM payout_installments WHERE holding_id = ?", Integer.class, holding);
		return count == null ? 0 : count;
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
