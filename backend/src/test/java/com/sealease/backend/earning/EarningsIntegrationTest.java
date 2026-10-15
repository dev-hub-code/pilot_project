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
import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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

/** Rent credited hourly over the lease (rent only on current plans), the ledger, and maturity at its end. */
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

	// --------------------------------------------------------------------------------- rent

	@Test
	void rentIsCreditedHourlyAndTheContainerReturnsToStockAfterTheLastHour() throws Exception {
		// ₹50,000 container at 2% rent, leased for 1 year (12 months): ₹1,000 a month. Plans pay rent only.
		Account investor = investors.approvedInvestor();
		String orderId = investors.invest(investor, offerings.plan(), 1);
		UUID holding = jdbc.queryForObject("SELECT id FROM holdings WHERE order_id = ?::uuid", UUID.class, orderId);
		UUID container = jdbc.queryForObject("SELECT container_id FROM holdings WHERE id = ?", UUID.class, holding);

		// One rent accrual for the whole lease, starting at the next full hour: nothing is credited until an hour passes.
		Map<String, Object> lease = jdbc.queryForMap(
				"SELECT starts_at, ends_at, monthly_rent, tenure_months FROM rent_accruals WHERE holding_id = ?", holding);
		Instant start = ((Timestamp) lease.get("starts_at")).toInstant();
		assertThat(start.getEpochSecond() % 3600).isZero();
		assertThat(start).isAfterOrEqualTo(Instant.now().truncatedTo(ChronoUnit.HOURS));
		assertThat(((Timestamp) lease.get("ends_at")).toInstant()).isEqualTo(start.atZone(ZoneOffset.UTC).plusMonths(12).toInstant());
		assertThat((BigDecimal) lease.get("monthly_rent")).isEqualByComparingTo("1000");
		assertThat(payouts.creditDue()).isZero();
		assertThat(credits(holding)).isZero();

		// Three lease months later: every hour is credited, one ledger transaction per calendar month they fall in.
		long hours = backdate(holding, 3);
		Instant hour = Instant.now().truncatedTo(ChronoUnit.HOURS);
		assertThat(payouts.creditDue()).isEqualTo(hours);
		assertThat(credits(holding)).isEqualTo(calendarMonths(hour.minus(hours, ChronoUnit.HOURS), hour));
		assertThat(payouts.creditDue()).as("crediting again changes nothing").isZero();
		// Exactly three months' rent, however long those months are.
		assertThat(jdbc.queryForObject("SELECT sum(rent_amount) FROM rent_credits WHERE holding_id = ?", BigDecimal.class,
				holding)).isEqualByComparingTo("3000");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM rent_credits WHERE holding_id = ? AND date_trunc('month', from_at "
				+ "AT TIME ZONE 'UTC') <> date_trunc('month', (to_at - interval '1 second') AT TIME ZONE 'UTC')", Integer.class,
				holding)).as("no credit spans two months").isZero();

		mvc.perform(get("/api/v1/earnings/summary").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.balances[0].amount").value("3000.00"))
			.andExpect(jsonPath("$.rentPaid[0].amount").value("3000.00"))
			.andExpect(jsonPath("$.capitalReturned.length()").value(0))
			.andExpect(jsonPath("$.leases[0].rentCredited.amount").value("3000.00"))
			.andExpect(jsonPath("$.leases[0].totalRent.amount").value("12000.00"))
			.andExpect(jsonPath("$.leases[0].status").value("RUNNING"))
			.andExpect(jsonPath("$.nextPayout").doesNotExist())
			// Credited up to the current hour: the next credit comes when it ends.
			.andExpect(jsonPath("$.nextCreditAt").value(hour.plus(1, ChronoUnit.HOURS).toString()));
		mvc.perform(get("/api/v1/earnings").param("status", "RUNNING").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].capitalTotal.amount").value("0.00"))
			.andExpect(jsonPath("$.content[0].containerNumber").isNotEmpty())
			.andExpect(jsonPath("$.content[0].userId").doesNotExist());

		// Each credit is one balanced ledger transaction: the rent is a platform expense (no capital posting).
		Map<String, Object> first = jdbc.queryForMap(
				"SELECT from_at, rent_amount FROM rent_credits WHERE holding_id = ? ORDER BY from_at LIMIT 1", holding);
		String rent = ((BigDecimal) first.get("rent_amount")).setScale(4).toPlainString();
		Instant from = ((Timestamp) first.get("from_at")).toInstant();
		assertThat(from).isEqualTo(hour.minus(hours, ChronoUnit.HOURS));
		assertThat(entries(holding + ":" + from)).containsExactlyInAnyOrder(
				"DEBIT PLATFORM_RENT_EXPENSE " + rent, "CREDIT INVESTOR_EARNINGS " + rent);

		assertLedgerBalanced();
	}

	@Test
	void theLastHourMaturesTheHoldingAndTheContainerReturnsToStock() throws Exception {
		Account investor = investors.approvedInvestor();
		String orderId = investors.invest(investor, offerings.plan(), 1);
		UUID holding = jdbc.queryForObject("SELECT id FROM holdings WHERE order_id = ?::uuid", UUID.class, orderId);
		UUID container = jdbc.queryForObject("SELECT container_id FROM holdings WHERE id = ?", UUID.class, holding);
		assertThat(jdbc.queryForObject("SELECT status FROM containers WHERE id = ?", String.class, container)).isEqualTo("ON_LEASE");

		// The whole year has passed: its rent is credited at once and the lease ends.
		backdate(holding, 12);
		payouts.creditDue();
		assertThat(jdbc.queryForObject("SELECT completed_at IS NOT NULL FROM rent_accruals WHERE holding_id = ?",
				Boolean.class, holding)).isTrue();
		assertThat(jdbc.queryForObject("SELECT status FROM holdings WHERE id = ?", String.class, holding)).isEqualTo("MATURED");
		assertThat(jdbc.queryForObject("SELECT status FROM containers WHERE id = ?", String.class, container)).isEqualTo("AVAILABLE");
		assertThat(api.auditCount("HOLDING_MATURED", holding)).isEqualTo(1);
		assertThat(payouts.creditDue()).as("nothing more to credit").isZero();
		// The whole lease: 12 × ₹1,000, 24% of the price in rent, and nothing more.
		mvc.perform(get("/api/v1/earnings/summary").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.balances[0].amount").value("12000.00"))
			.andExpect(jsonPath("$.rentPaid[0].amount").value("12000.00"))
			.andExpect(jsonPath("$.leases[0].status").value("COMPLETED"))
			.andExpect(jsonPath("$.nextCreditAt").doesNotExist());
		mvc.perform(get("/api/v1/portfolio").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.activeHoldings").value(0))
			.andExpect(jsonPath("$.holdings[0].status").value("MATURED"));
		assertLedgerBalanced();
	}

	@Test
	void commissionIsCreditedWithEachHourOfRent() throws Exception {
		Account referrer = investors.approvedInvestor();
		String code = JsonPath.read(mvc.perform(get("/api/v1/referrals/me").header(HttpHeaders.AUTHORIZATION, referrer.bearer()))
			.andReturn().getResponse().getContentAsString(), "$.code");
		Account investor = investors.approve(api.register(code));
		// ₹50,000 container at 2% rent, leased for 1 year: ₹1,000 rent a month; level-1 commission 2% of ₹50,000 a month.
		String orderId = investors.invest(investor, offerings.plan(), 1);
		UUID holding = jdbc.queryForObject("SELECT id FROM holdings WHERE order_id = ?::uuid", UUID.class, orderId);

		// Two lease months: credited in one run per calendar month they cover (at least two), each paying the
		// commission on its hours; together exactly two months' 2%.
		backdate(holding, 2);
		payouts.creditDue();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM referral_earnings WHERE source_holding_id = ?",
				Integer.class, holding)).isEqualTo(credits(holding)).isGreaterThanOrEqualTo(2);
		assertThat(referralBalance(referrer)).isEqualByComparingTo("2000");
		// Staff's commission history adds the hourly commissions up: one entry for the referred container this month.
		mvc.perform(get("/api/v1/admin/referral-earnings").param("beneficiaryId", referrer.id().toString())
				.header(HttpHeaders.AUTHORIZATION, api.staff("FINANCE").bearer()))
			.andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.content[0].month").value(YearMonth.now(ZoneOffset.UTC).toString()))
			.andExpect(jsonPath("$.content[0].base.amount").value("100000.00"))
			.andExpect(jsonPath("$.content[0].amount.amount").value("2000.00"));
		assertLedgerBalanced();
	}

	@Test
	void financeCanRunDueRentNow() throws Exception {
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
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].userId").value(investor.id().toString()));

		run(investor).andExpect(status().isForbidden());
		run(api.staff("SUPPORT")).andExpect(status().isForbidden());
		run(finance).andExpect(status().isOk()).andExpect(jsonPath("$.credited").value(org.hamcrest.Matchers.greaterThanOrEqualTo(28 * 24)));
		assertThat(jdbc.queryForObject("SELECT credited_rent FROM rent_accruals WHERE holding_id = ?", BigDecimal.class,
				holding)).isEqualByComparingTo("1000");

		// Investors see their own leases only.
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
	 * Moves the holding's lease into the past, as if it had started {@code months} lease months before the current
	 * hour. Only for a lease nothing has been credited on yet: a real lease never moves.
	 *
	 * @return the hours from the lease start to the current hour
	 */
	private long backdate(UUID holding, int months) {
		Instant hour = Instant.now().truncatedTo(ChronoUnit.HOURS);
		return backdateHours(holding, ChronoUnit.HOURS.between(hour.atZone(ZoneOffset.UTC).minusMonths(months).toInstant(), hour));
	}

	/** As {@link #backdate(UUID, int)}, the lease starting {@code hours} hours before the current hour. */
	private long backdateHours(UUID holding, long hours) {
		Instant target = Instant.now().truncatedTo(ChronoUnit.HOURS).minus(hours, ChronoUnit.HOURS);
		jdbc.update("""
				UPDATE rent_accruals
				SET starts_at = ?,
				    ends_at = (? AT TIME ZONE 'UTC' + make_interval(months => tenure_months)) AT TIME ZONE 'UTC',
				    credited_until = ?
				WHERE holding_id = ? AND credited_until = starts_at""", Timestamp.from(target), Timestamp.from(target),
				Timestamp.from(target), holding);
		return hours;
	}

	/** Calendar months (UTC) the hours from {@code from} to {@code to} fall in. */
	private static int calendarMonths(Instant from, Instant to) {
		YearMonth first = YearMonth.from(from.atZone(ZoneOffset.UTC));
		YearMonth last = YearMonth.from(to.minusSeconds(1).atZone(ZoneOffset.UTC));
		return (int) ChronoUnit.MONTHS.between(first, last) + 1;
	}

	private BigDecimal referralBalance(Account investor) throws Exception {
		String summary = mvc.perform(get("/api/v1/earnings/summary").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andReturn().getResponse().getContentAsString();
		List<String> amounts = JsonPath.read(summary, "$.wallet[?(@.balance == 'REFERRAL')].amount.amount");
		return amounts.isEmpty() ? BigDecimal.ZERO : new BigDecimal(amounts.getFirst());
	}

	private int credits(UUID holding) {
		Integer count = jdbc.queryForObject("SELECT count(*) FROM rent_credits WHERE holding_id = ?", Integer.class, holding);
		return count == null ? 0 : count;
	}

	/** The ledger entries of a rent credit (by its reference), as "DIRECTION ACCOUNT_TYPE amount". */
	private List<String> entries(String reference) {
		return jdbc.queryForList("""
				SELECT e.direction || ' ' || a.account_type || ' ' || e.amount
				FROM ledger_entries e
				JOIN ledger_transactions t ON t.id = e.transaction_id
				JOIN ledger_accounts a ON a.id = e.account_id
				WHERE t.transaction_type = 'INVESTOR_PAYOUT' AND t.reference = ?""", String.class, reference);
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

}
