package com.sealease.backend.referral;

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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase 7: referral codes, the four-level hierarchy, commissions on rental income and rate versions. */
@IntegrationTest
class ReferralIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	private TestApi api;
	private InvestorFixtures investors;
	private OfferingFixtures offerings;
	private Account admin;

	@BeforeEach
	void setUp() throws Exception {
		api = new TestApi(mvc, jdbc);
		admin = api.admin();
		investors = new InvestorFixtures(mvc, jdbc, api);
		offerings = new OfferingFixtures(mvc, admin);
	}

	// --------------------------------------------------------------------------- linking

	@Test
	void aCodeEnteredAtSignUpLinksTheReferrer() throws Exception {
		Account referrer = api.register();
		String code = codeOf(referrer);
		assertThat(code).matches("[2-9A-HJ-NP-Z]{8}");
		assertThat(codeOf(referrer)).isEqualTo(code);

		Account referred = api.register(" " + code.toLowerCase() + " ");
		overview(referred).andExpect(jsonPath("$.referredBy").value("Test I."));
		overview(referrer)
			.andExpect(jsonPath("$.levels[0].members").value(1))
			.andExpect(jsonPath("$.levels[0].ratePercent").value(2.0))
			.andExpect(jsonPath("$.levels[3].ratePercent").value(0.25))
			.andExpect(jsonPath("$.eligible").value(false))
			.andExpect(jsonPath("$.ineligibleReason").value(containsString("Verify your identity")));
		assertThat(api.auditCount("REFERRAL_LINKED", referred.id())).isEqualTo(1);

		// An unknown code fails the registration as a whole: no account is created.
		String email = "user-" + UUID.randomUUID() + "@example.com";
		assertThat(api.registerResult(email, "ZZZZZZZZ").getResponse().getStatus()).isEqualTo(400);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Integer.class, email)).isZero();

		// Codes of accounts that are no longer active stop working.
		jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", referrer.id());
		assertThat(api.registerResult("user-" + UUID.randomUUID() + "@example.com", code).getResponse().getContentAsString())
			.contains("no longer valid");
	}

	// ------------------------------------------------------------------------ commissions

	@Test
	void rentEarnsFourLevelsOfUplinesAndIneligibleUplinesForfeit() throws Exception {
		// top → l4 → l3 → l2 → l1 → investor: "top" is five levels up and earns nothing.
		Account top = investors.approvedInvestor();
		Account l4 = investors.approve(api.register(codeOf(top)));
		Account l3 = api.register(codeOf(l4));                         // identity not verified: forfeits
		Account l2 = investors.approve(api.register(codeOf(l3)));
		Account l1 = investors.approve(api.register(codeOf(l2)));
		Account investor = investors.hni(investors.approve(api.register(codeOf(l1))));

		UUID product = offerings.published("HNI", "10000", "10000", "10000", "1000", 1, "10");
		investors.invest(investor, product, "10000");
		jdbc.update("UPDATE investment_products SET published_at = now() - interval '2 years' WHERE id = ?", product);
		mvc.perform(post("/api/v1/admin/investment-products/{id}/activate", product)
				.header(HttpHeaders.AUTHORIZATION, admin.bearer()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"leaseStartsOn\":\"" + LocalDate.now(ZoneOffset.UTC).minusMonths(1).minusDays(1) + "\"}"))
			.andExpect(status().isOk());
		String receipt = distribute(product);

		// Commission is on the gross share (1 000.00), paid by the platform: 2% / 1% / – / 0.25%.
		earned(l1).andExpect(jsonPath("$.totalEarned[0].amount").value("20.00"));
		earned(l2).andExpect(jsonPath("$.totalEarned[0].amount").value("10.00"));
		earned(l3).andExpect(jsonPath("$.totalEarned.length()").value(0));
		earned(l4).andExpect(jsonPath("$.totalEarned[0].amount").value("2.50"));
		earned(top).andExpect(jsonPath("$.totalEarned.length()").value(0));
		balance(l1).andExpect(jsonPath("$.balances[0].amount").value("20.00"));
		// The referred investor keeps their full net share.
		balance(investor).andExpect(jsonPath("$.balances[0].amount").value("900.00"));

		mvc.perform(get("/api/v1/referrals/earnings").header(HttpHeaders.AUTHORIZATION, l2.bearer()))
			.andExpect(jsonPath("$.content[0].level").value(2))
			.andExpect(jsonPath("$.content[0].sourceName").value("Test I."))
			.andExpect(jsonPath("$.content[0].sourceUserId").doesNotExist())
			.andExpect(jsonPath("$.content[0].base.amount").value("1000.00"))
			.andExpect(jsonPath("$.content[0].amount.amount").value("10.00"));

		String downline = mvc.perform(get("/api/v1/referrals/downline").header(HttpHeaders.AUTHORIZATION, l4.bearer()))
			.andExpect(jsonPath("$.truncated").value(false))
			.andExpect(jsonPath("$.members.length()").value(4))
			.andExpect(jsonPath("$.members[0].parentId").isEmpty())
			.andExpect(jsonPath("$.members[0].id").value(matchesPattern("m\\d+")))
			.andReturn().getResponse().getContentAsString();
		List<Integer> levels = JsonPath.read(downline, "$.members[*].level");
		assertThat(levels).containsExactly(1, 2, 3, 4);
		List<String> earnedFromInvestor = JsonPath.read(downline, "$.members[?(@.level == 4)].earned[0].amount");
		assertThat(earnedFromInvestor).containsExactly("2.50");

		assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE topic = 'referral.earning.created' "
				+ "AND payload ->> 'receiptId' = ?", Integer.class, receipt)).isEqualTo(3);
		assertThat(jdbc.queryForObject("""
				SELECT new_value ->> 'forfeited' FROM audit_logs WHERE action = 'REFERRAL_COMMISSIONS_PAID' AND entity_id = ?
				""", String.class, receipt)).isEqualTo("1");
		assertThat(jdbc.queryForList("""
				SELECT sum(CASE direction WHEN 'DEBIT' THEN amount ELSE -amount END) = 0
				FROM ledger_entries e JOIN ledger_transactions t ON t.id = e.transaction_id GROUP BY t.currency
				""", Boolean.class)).containsOnly(true);

		mvc.perform(get("/api/v1/admin/users/{id}/referrals", investor.id()).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
			.andExpect(jsonPath("$.uplines.length()").value(4))
			.andExpect(jsonPath("$.uplines[0].userId").value(l1.id().toString()))
			.andExpect(jsonPath("$.uplines[3].userId").value(l4.id().toString()));
		mvc.perform(get("/api/v1/admin/users/{id}/referrals", top.id()).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
			.andExpect(jsonPath("$.downlineSize.length()").value(4))
			.andExpect(jsonPath("$.downlineSize[0]").value(1))
			.andExpect(jsonPath("$.downlineSize[3]").value(1));
		mvc.perform(get("/api/v1/admin/referral-earnings").param("beneficiaryId", l1.id().toString())
				.header(HttpHeaders.AUTHORIZATION, admin.bearer()))
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].sourceUserId").value(investor.id().toString()));
	}

	// ------------------------------------------------------------------------------ rates

	@Test
	void ratesAreEffectiveDatedAndOnlyScheduledVersionsCanBeCancelled() throws Exception {
		Instant tomorrow = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

		schedule(admin, Instant.now().minus(1, ChronoUnit.HOURS), "2", "1", "0.5", "0.25")
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(containsString("past")));
		schedule(admin, tomorrow, "10", "10", "0.5", "0").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(containsString("20%")));
		schedule(admin, tomorrow, "11", "0", "0", "0").andExpect(status().isBadRequest());

		String scheduled = JsonPath.read(schedule(admin, tomorrow, "3", "1.5", "0.5", "0.25")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.state").value("SCHEDULED"))
			.andReturn().getResponse().getContentAsString(), "$.id");
		schedule(admin, tomorrow, "3", "1", "0.5", "0.25").andExpect(status().isConflict());

		Account finance = api.staff("FINANCE");
		String history = mvc.perform(get("/api/v1/admin/referral-rates").header(HttpHeaders.AUTHORIZATION, finance.bearer()))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		List<String> inForce = JsonPath.read(history, "$[?(@.state == 'IN_FORCE')].id");
		assertThat(inForce).hasSize(1);
		schedule(finance, tomorrow.plusSeconds(60), "2", "1", "0.5", "0.25").andExpect(status().isForbidden());

		cancel(admin, inForce.getFirst()).andExpect(status().isUnprocessableContent());
		cancel(admin, scheduled).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("CANCELLED"));
		cancel(admin, scheduled).andExpect(status().isConflict());
		assertThat(api.auditCount("REFERRAL_RATES_CANCELLED", scheduled)).isEqualTo(1);
	}

	// ------------------------------------------------------------------------------ helpers

	private String codeOf(Account account) throws Exception {
		return JsonPath.read(overview(account).andReturn().getResponse().getContentAsString(), "$.code");
	}

	private ResultActions overview(Account account) throws Exception {
		return mvc.perform(get("/api/v1/referrals/me").header(HttpHeaders.AUTHORIZATION, account.bearer()))
			.andExpect(status().isOk());
	}

	private ResultActions earned(Account account) throws Exception {
		return overview(account);
	}

	private ResultActions balance(Account account) throws Exception {
		return mvc.perform(get("/api/v1/earnings/summary").header(HttpHeaders.AUTHORIZATION, account.bearer()));
	}

	/** Records rent for period 1 and has a second finance user approve it; returns the receipt id. */
	private String distribute(UUID product) throws Exception {
		Account recorder = api.staff("FINANCE");
		Account approver = api.staff("FINANCE");
		String receipt = JsonPath.read(mvc.perform(post("/api/v1/admin/rentals")
				.header(HttpHeaders.AUTHORIZATION, recorder.bearer()).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"productId":"%s","periodNumber":1,"amount":"1000","receivedOn":"%s","externalReference":"LESSEE-1"}
						""".formatted(product, LocalDate.now(ZoneOffset.UTC))))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.receipt.id");
		mvc.perform(post("/api/v1/admin/rentals/{id}/approve", receipt).header(HttpHeaders.AUTHORIZATION, approver.bearer()))
			.andExpect(status().isOk());
		return receipt;
	}

	private ResultActions schedule(Account actor, Instant from, String... percents) throws Exception {
		return mvc.perform(post("/api/v1/admin/referral-rates").header(HttpHeaders.AUTHORIZATION, actor.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("""
					{"effectiveFrom":"%s","percents":["%s"],"reason":"Quarterly review"}
					""".formatted(from, String.join("\",\"", percents))));
	}

	private ResultActions cancel(Account actor, String versionId) throws Exception {
		return mvc.perform(post("/api/v1/admin/referral-rates/{id}/cancel", versionId)
			.header(HttpHeaders.AUTHORIZATION, actor.bearer()));
	}

}
