package com.sealease.backend.earning;

import com.jayway.jsonpath.JsonPath;
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

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase 6: leases, rental receipts, distribution to investors, the ledger and maturity. */
@IntegrationTest
class EarningsIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TransactionTemplate transactions;

	private TestApi api;
	private OfferingFixtures offerings;
	private InvestorFixtures investors;
	private Account admin;

	@BeforeEach
	void setUp() throws Exception {
		api = new TestApi(mvc, jdbc);
		admin = api.admin();
		offerings = new OfferingFixtures(mvc, admin);
		investors = new InvestorFixtures(mvc, jdbc, api);
	}

	// --------------------------------------------------------------------------- happy path

	@Test
	void rentIsSplitByOwnershipNetOfTheFeeAndTheOfferingMaturesAfterTheLastPeriod() throws Exception {
		// 10 000 USD container, 1 000 USD rent a month for 3 months, 10% management fee.
		UUID product = offerings.published("RETAIL", "10000", "1000", "1000", "1000", 3, "10");
		Account alice = investors.approvedInvestor();
		Account bob = investors.approvedInvestor();
		investors.invest(alice, product, "5000");
		investors.invest(bob, product, "3000");
		Account recorder = api.staff("FINANCE");
		Account approver = api.staff("FINANCE");

		// 20% stays unsold; the lease starts anyway, early enough for all three periods to be due.
		LocalDate start = today().minusMonths(3).minusDays(1);
		backdatePublication(product);
		activate(admin, product, start).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andExpect(jsonPath("$.leaseStartsOn").value(start.toString()))
			.andExpect(jsonPath("$.managementFeePercent").value(10.0));
		mvc.perform(put("/api/v1/cart/items/{id}", product).header(HttpHeaders.AUTHORIZATION, bob.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"1000\"}"))
			.andExpect(status().isUnprocessableContent());

		String due = mvc.perform(get("/api/v1/admin/rentals/due").header(HttpHeaders.AUTHORIZATION, recorder.bearer()))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		List<Integer> duePeriods = JsonPath.read(due, "$[?(@.productId == '%s')].periodNumber".formatted(product));
		assertThat(duePeriods).containsExactly(1, 2, 3);

		// Period 1: recorded by one person, previewed, approved by another.
		String first = id(record(recorder, product, 1, "1000", null).andExpect(status().isCreated())
			.andExpect(jsonPath("$.receipt.status").value("RECORDED"))
			.andExpect(jsonPath("$.preview").value(true))
			.andExpect(jsonPath("$.distribution[0].gross.amount").value("500.00"))
			.andExpect(jsonPath("$.distribution[0].fee.amount").value("50.00"))
			.andExpect(jsonPath("$.distribution[0].net.amount").value("450.00"))
			.andExpect(jsonPath("$.distribution[1].net.amount").value("270.00"))
			.andExpect(jsonPath("$.toInvestors.amount").value("720.00"))
			.andExpect(jsonPath("$.fees.amount").value("80.00"))
			.andExpect(jsonPath("$.retained.amount").value("200.00")));
		record(approver, product, 1, "1000", null).andExpect(status().isConflict());
		approve(recorder, first).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value(containsString("someone other")));
		approve(approver, first).andExpect(status().isOk())
			.andExpect(jsonPath("$.receipt.status").value("DISTRIBUTED"))
			.andExpect(jsonPath("$.preview").value(false))
			.andExpect(jsonPath("$.distribution.length()").value(2));
		approve(approver, first).andExpect(status().isConflict());

		mvc.perform(get("/api/v1/earnings/summary").header(HttpHeaders.AUTHORIZATION, alice.bearer()))
			.andExpect(jsonPath("$.balances[0].amount").value("450.00"))
			.andExpect(jsonPath("$.totalEarned[0].amount").value("450.00"))
			.andExpect(jsonPath("$.holdings[0].payments").value(1));
		mvc.perform(get("/api/v1/earnings").header(HttpHeaders.AUTHORIZATION, bob.bearer()))
			.andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.content[0].periodNumber").value(1))
			.andExpect(jsonPath("$.content[0].gross.amount").value("300.00"))
			.andExpect(jsonPath("$.content[0].fee.amount").value("30.00"))
			.andExpect(jsonPath("$.content[0].net.amount").value("270.00"))
			.andExpect(jsonPath("$.content[0].periodStartsOn").value(start.toString()));
		assertThat(outboxCount("rental.generated", first)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE topic = 'earning.created' "
				+ "AND payload ->> 'productId' = ?", Integer.class, product.toString())).isEqualTo(2);
		assertThat(api.auditCount("RENTAL_DISTRIBUTED", first)).isEqualTo(1);

		// Period 2 was paid short: a note is required.
		record(recorder, product, 2, "900", null).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(containsString("explain the difference")));
		approve(approver, id(record(recorder, product, 2, "900", "Lessee paid 100 short; arrears agreed")
			.andExpect(status().isCreated()))).andExpect(status().isOk())
			.andExpect(jsonPath("$.distribution[0].net.amount").value("405.00"));

		// The last period matures the offering and its holdings.
		approve(approver, id(record(recorder, product, 3, "1000", null))).andExpect(status().isOk());
		mvc.perform(get("/api/v1/admin/investment-products/{id}", product).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
			.andExpect(jsonPath("$.status").value("MATURED"))
			.andExpect(jsonPath("$.maturedAt").isNotEmpty());
		mvc.perform(get("/api/v1/portfolio").header(HttpHeaders.AUTHORIZATION, alice.bearer()))
			.andExpect(jsonPath("$.holdings[0].status").value("MATURED"));
		record(recorder, product, 3, "1000", null).andExpect(status().isUnprocessableContent());

		mvc.perform(get("/api/v1/earnings/summary").header(HttpHeaders.AUTHORIZATION, alice.bearer()))
			.andExpect(jsonPath("$.balances[0].amount").value("1305.00"))
			.andExpect(jsonPath("$.holdings[0].payments").value(3));
		mvc.perform(get("/api/v1/admin/rentals/due").header(HttpHeaders.AUTHORIZATION, recorder.bearer()))
			.andExpect(jsonPath("$[*].productId").value(not(hasItem(product.toString()))));
		assertLedgerBalanced();
	}

	// ------------------------------------------------------------------------------- leases

	@Test
	void onlyFundedOrSettledOfferingsCanStartTheirLease() throws Exception {
		UUID product = offerings.published("RETAIL", "10000", "1000", "1000", "500", 12, "0");
		backdatePublication(product);
		Account investor = investors.approvedInvestor();

		activate(admin, product, today()).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.message").value(containsString("confirmed investors")));
		investors.invest(investor, product, "2000");
		investors.placeOrder(investors.approvedInvestor(), product, "1000");
		activate(admin, product, today()).andExpect(status().isUnprocessableContent());
		activate(investor, product, today()).andExpect(status().isForbidden());

		UUID funded = offerings.published("HNI", "8000", "8000", "8000", "400", 12, "0");
		investors.invest(investors.hniInvestor(), funded, "8000");
		activate(admin, funded, today().minusYears(1)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(containsString("before the offering was published")));
		activate(admin, funded, today()).andExpect(status().isOk())
			.andExpect(jsonPath("$.leaseEndsOn").value(today().plusMonths(12).toString()));
		activate(admin, funded, today()).andExpect(status().isUnprocessableContent());
		assertThat(api.auditCount("PRODUCT_LEASE_ACTIVATED", funded)).isEqualTo(1);
	}

	// ------------------------------------------------------------------------------ rentals

	@Test
	void recordingValidatesThePeriodAndAMistakeCanBeVoided() throws Exception {
		UUID product = activeLease("1200", 6);
		Account finance = api.staff("FINANCE");

		record(finance, product, 0, "1200", null).andExpect(status().isBadRequest());
		record(finance, product, 7, "1200", null).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(containsString("periods 1 to 6")));
		mvc.perform(post("/api/v1/admin/rentals").header(HttpHeaders.AUTHORIZATION, finance.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(rentalBody(product, 1, "1200", null, today().plusDays(1))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(containsString("future")));
		record(finance, product, 1, "1200.005", null).andExpect(status().isBadRequest());

		String wrong = id(record(finance, product, 1, "1200", null).andExpect(status().isCreated()));
		mvc.perform(post("/api/v1/admin/rentals/{id}/reject", wrong).header(HttpHeaders.AUTHORIZATION, finance.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Booked against the wrong offering\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.receipt.status").value("REJECTED"))
			.andExpect(jsonPath("$.distribution.length()").value(0));
		record(finance, product, 1, "1200", null).andExpect(status().isCreated());

		Account investor = investors.approvedInvestor();
		mvc.perform(get("/api/v1/admin/rentals").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/admin/rentals").param("productId", product.toString())
				.header(HttpHeaders.AUTHORIZATION, finance.bearer()))
			.andExpect(jsonPath("$.totalElements").value(2));
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
			.andExpect(jsonPath("$.message").value(containsString("only 25.00 USD")));
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

	/** An offering fully owned by one investor, on lease since well before any period is due. */
	private UUID activeLease(String rental, int months) throws Exception {
		UUID product = offerings.published("HNI", "10000", "10000", "10000", rental, months, "0");
		investors.invest(investors.hniInvestor(), product, "10000");
		backdatePublication(product);
		activate(admin, product, today().minusMonths(months).minusDays(1)).andExpect(status().isOk());
		return product;
	}

	private void backdatePublication(UUID product) {
		jdbc.update("UPDATE investment_products SET published_at = now() - interval '2 years' WHERE id = ?", product);
	}

	private ResultActions activate(Account actor, UUID product, LocalDate startsOn) throws Exception {
		return mvc.perform(post("/api/v1/admin/investment-products/{id}/activate", product)
			.header(HttpHeaders.AUTHORIZATION, actor.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"leaseStartsOn\":\"" + startsOn + "\"}"));
	}

	private ResultActions record(Account actor, UUID product, int period, String amount, String note) throws Exception {
		return mvc.perform(post("/api/v1/admin/rentals").header(HttpHeaders.AUTHORIZATION, actor.bearer())
			.contentType(MediaType.APPLICATION_JSON).content(rentalBody(product, period, amount, note, today())));
	}

	private static String rentalBody(UUID product, int period, String amount, String note, LocalDate receivedOn) {
		return """
				{"productId":"%s","periodNumber":%d,"amount":"%s","receivedOn":"%s","externalReference":"LESSEE-%d"%s}
				""".formatted(product, period, amount, receivedOn, period,
				note == null ? "" : ",\"note\":\"" + note + "\"");
	}

	private ResultActions approve(Account actor, String receiptId) throws Exception {
		return mvc.perform(post("/api/v1/admin/rentals/{id}/approve", receiptId)
			.header(HttpHeaders.AUTHORIZATION, actor.bearer()));
	}

	private ResultActions adjust(Account actor, Account investor, String direction, String amount, String key)
			throws Exception {
		return mvc.perform(post("/api/v1/admin/ledger/adjustments").header(HttpHeaders.AUTHORIZATION, actor.bearer())
			.header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content("""
					{"userId":"%s","currency":"USD","direction":"%s","amount":"%s","reason":"Goodwill credit"}
					""".formatted(investor.id(), direction, amount)));
	}

	private static String id(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.receipt.id");
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

	private static LocalDate today() {
		return LocalDate.now(ZoneOffset.UTC);
	}

}
