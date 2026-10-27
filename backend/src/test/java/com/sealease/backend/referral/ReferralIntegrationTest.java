package com.sealease.backend.referral;

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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Referral codes, the four-level hierarchy, monthly commissions on referred investors' investments, and rate versions. */
@IntegrationTest
class ReferralIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PayoutService payouts;

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

		Account referred = api.register(code);
		overview(referred).andExpect(jsonPath("$.referredBy").value("Test I."));
		overview(referrer)
			.andExpect(jsonPath("$.levels[0].members").value(1))
			.andExpect(jsonPath("$.levels[0].ratePercent").value(2.0))
			.andExpect(jsonPath("$.levels[3].ratePercent").value(0.25))
			.andExpect(jsonPath("$.eligible").value(false))
			.andExpect(jsonPath("$.ineligibleReason").value(containsString("Verify your identity")));
		assertThat(api.auditCount("REFERRAL_LINKED", referred.id())).isEqualTo(1);

		// Codes are capitals only: lower case or padding is refused, not corrected.
		String lower = "user-" + UUID.randomUUID() + "@example.com";
		assertThat(api.registerResult(lower, code.toLowerCase()).getResponse().getStatus()).isEqualTo(400);
		assertThat(api.registerResult(lower, " " + code + " ").getResponse().getStatus()).isEqualTo(400);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Integer.class, lower)).isZero();

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
	void investmentsEarnFourLevelsOfUplinesMonthlyAndIneligibleUplinesForfeit() throws Exception {
		// top → l4 → l3 → l2 → l1 → investor: "top" is five levels up and earns nothing.
		Account top = investors.approvedInvestor();
		Account l4 = investors.approve(api.register(codeOf(top)));
		Account l3 = api.register(codeOf(l4));                         // identity not verified: forfeits
		Account l2 = investors.approve(api.register(codeOf(l3)));
		Account l1 = investors.approve(api.register(codeOf(l2)));
		Account investor = investors.approve(api.register(codeOf(l1)));

		// ₹50,000 container at 2%, leased for 1 year: each payout is ₹1,000 rent.
		String orderId = investors.invest(investor, offerings.plan(), 1);
		String receipt = payFirstPayout(orderId);

		// Commission is on the investment (₹50,000.00), every month, paid by the platform: 2% / 1% / – / 0.25%.
		earned(l1).andExpect(jsonPath("$.totalEarned[0].amount").value("1000.00"));
		earned(l2).andExpect(jsonPath("$.totalEarned[0].amount").value("500.00"));
		earned(l3).andExpect(jsonPath("$.totalEarned.length()").value(0));
		earned(l4).andExpect(jsonPath("$.totalEarned[0].amount").value("125.00"));
		earned(top).andExpect(jsonPath("$.totalEarned.length()").value(0));
		balance(l1).andExpect(jsonPath("$.balances[0].amount").value("1000.00"))
			.andExpect(jsonPath("$.referralEarned[0].amount").value("1000.00"));
		// The referred investor keeps their full payout.
		balance(investor).andExpect(jsonPath("$.balances[0].amount").value("1000.00"));

		// Investors see their totals, not a commission history; staff do.
		mvc.perform(get("/api/v1/referrals/earnings").header(HttpHeaders.AUTHORIZATION, l2.bearer()))
			.andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/admin/referral-earnings").param("beneficiaryId", l2.id().toString())
				.header(HttpHeaders.AUTHORIZATION, api.staff("ADMIN", "FINANCE").bearer()))
			.andExpect(jsonPath("$.content[0].level").value(2))
			.andExpect(jsonPath("$.content[0].sourceUserId").value(investor.id().toString()))
			.andExpect(jsonPath("$.content[0].base.amount").value("50000.00"))
			.andExpect(jsonPath("$.content[0].amount.amount").value("500.00"));

		// Each month's commission, split by level.
		mvc.perform(get("/api/v1/referrals/monthly").header(HttpHeaders.AUTHORIZATION, l4.bearer()))
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].month").value(matchesPattern("\\d{4}-\\d{2}")))
			.andExpect(jsonPath("$[0].levels.length()").value(4))
			.andExpect(jsonPath("$[0].levels[0].amount").value("0.00"))
			.andExpect(jsonPath("$[0].levels[3].amount").value("125.00"))
			.andExpect(jsonPath("$[0].total.amount").value("125.00"));
		mvc.perform(get("/api/v1/referrals/monthly").header(HttpHeaders.AUTHORIZATION, l3.bearer()))
			.andExpect(jsonPath("$.length()").value(0));

		String downline = mvc.perform(get("/api/v1/referrals/downline").header(HttpHeaders.AUTHORIZATION, l4.bearer()))
			.andExpect(jsonPath("$.truncated").value(false))
			.andExpect(jsonPath("$.members.length()").value(4))
			.andExpect(jsonPath("$.members[0].parentId").isEmpty())
			.andExpect(jsonPath("$.members[0].id").value(matchesPattern("m\\d+")))
			.andReturn().getResponse().getContentAsString();
		List<Integer> levels = JsonPath.read(downline, "$.members[*].level");
		assertThat(levels).containsExactly(1, 2, 3, 4);
		List<String> earnedFromInvestor = JsonPath.read(downline, "$.members[?(@.level == 4)].earned[0].amount");
		assertThat(earnedFromInvestor).containsExactly("125.00");

		// Each member opens on its own, with what was earned from them (no commission ledger).
		List<String> investorId = JsonPath.read(downline, "$.members[?(@.level == 4)].id");
		mvc.perform(get("/api/v1/referrals/members/{id}", investorId.getFirst()).header(HttpHeaders.AUTHORIZATION, l4.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.level").value(4))
			.andExpect(jsonPath("$.displayName").value("Test I."));
		mvc.perform(get("/api/v1/referrals/members/{id}/earnings", investorId.getFirst())
				.header(HttpHeaders.AUTHORIZATION, l4.bearer()))
			.andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/referrals/members/m5").header(HttpHeaders.AUTHORIZATION, l4.bearer()))
			.andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/referrals/members/m1").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isNotFound());

		// One event per credit of rent (one per calendar month it covers), with its commissions.
		assertThat(jdbc.queryForList("SELECT payload ->> 'commissions' FROM outbox_events WHERE topic = 'referral.earning.created' "
				+ "AND payload ->> 'holdingId' = ?", String.class, receipt)).isNotEmpty().containsOnly("3");
		assertThat(jdbc.queryForList("""
				SELECT new_value ->> 'forfeited' FROM audit_logs WHERE action = 'REFERRAL_COMMISSIONS_PAID'
				AND new_value ->> 'holdingId' = ?""", String.class, receipt)).isNotEmpty().containsOnly("1");
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

		Account finance = api.staff("ADMIN", "FINANCE");
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

	// ------------------------------------------------------------------ arranging downlines

	@Test
	void aSuperAdminMovesAMemberWithTheirDownlineToAnotherReferrer() throws Exception {
		Account top = api.register();
		Account a = api.register(codeOf(top));
		Account b = api.register(codeOf(a));
		Account d = api.register(codeOf(b));
		Account other = api.register();

		downline(top).andExpect(status().isOk())
			.andExpect(jsonPath("$.members.length()").value(3))
			.andExpect(jsonPath("$.members[1].userId").value(b.id().toString()))
			.andExpect(jsonPath("$.members[1].referrerId").value(a.id().toString()))
			.andExpect(jsonPath("$.members[1].level").value(2))
			.andExpect(jsonPath("$.members[1].email").value(b.email()));

		move(b, other, "Signed up with the wrong code").andExpect(status().isNoContent());
		// b's own downline (d) goes with them.
		downline(top).andExpect(jsonPath("$.members.length()").value(1));
		downline(other)
			.andExpect(jsonPath("$.members.length()").value(2))
			.andExpect(jsonPath("$.members[0].userId").value(b.id().toString()))
			.andExpect(jsonPath("$.members[1].userId").value(d.id().toString()))
			.andExpect(jsonPath("$.members[1].level").value(2));
		overview(d).andExpect(jsonPath("$.referredBy").value("Test I."));
		overview(other).andExpect(jsonPath("$.levels[0].members").value(1)).andExpect(jsonPath("$.levels[1].members").value(1));
		assertThat(jdbc.queryForObject("SELECT referrer_id FROM referrals WHERE user_id = ?", UUID.class, b.id()))
			.isEqualTo(other.id());
		assertThat(jdbc.queryForMap("SELECT from_referrer_id, to_referrer_id, moved_by, reason FROM referral_moves WHERE user_id = ?",
				b.id()))
			.containsEntry("from_referrer_id", a.id()).containsEntry("to_referrer_id", other.id())
			.containsEntry("moved_by", admin.id()).containsEntry("reason", "Signed up with the wrong code");
		assertThat(api.auditCount("REFERRAL_MOVED", b.id())).isEqualTo(1);
	}

	@Test
	void aSuperAdminRemovesAMemberFromADownlineAndPlacesThemInAnother() throws Exception {
		Account top = api.register();
		Account a = api.register(codeOf(top));
		Account b = api.register(codeOf(a));
		Account other = api.register();

		move(a, null, "Left the network").andExpect(status().isNoContent());
		// a keeps their own downline, but nobody refers them any more.
		downline(top).andExpect(jsonPath("$.members.length()").value(0));
		downline(a)
			.andExpect(jsonPath("$.investor.referrerId").doesNotExist())
			.andExpect(jsonPath("$.members[0].userId").value(b.id().toString()));
		overview(a).andExpect(jsonPath("$.referredBy").doesNotExist());
		move(a, null, "Again").andExpect(status().isUnprocessableEntity());

		// Someone nobody referred is placed under a referrer, with their downline.
		move(a, other, "Joined Other's team").andExpect(status().isNoContent());
		downline(other)
			.andExpect(jsonPath("$.investor.name").value("Test Investor"))
			.andExpect(jsonPath("$.members.length()").value(2));
		downline(a).andExpect(jsonPath("$.investor.referrerId").value(other.id().toString()));
		assertThat(jdbc.queryForList("SELECT to_referrer_id FROM referral_moves WHERE user_id = ? ORDER BY moved_at",
				a.id())).extracting(r -> r.get("to_referrer_id")).containsExactly(null, other.id());
		assertThat(api.auditCount("REFERRAL_MOVED", a.id())).isEqualTo(2);
	}

	@Test
	void movesThatWouldBreakTheHierarchyAreRefused() throws Exception {
		Account top = api.register();
		Account a = api.register(codeOf(top));
		Account b = api.register(codeOf(a));

		// Below themselves (a cycle), under themselves, to the referrer they already have, or a member with no referrer.
		move(a, b, "x").andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.message").value(containsString("cannot move below themselves")));
		move(a, a, "x").andExpect(status().isUnprocessableEntity());
		move(b, a, "x").andExpect(status().isUnprocessableEntity());
		move(top, a, "x").andExpect(status().isUnprocessableEntity());
		move(b, top, " ").andExpect(status().isBadRequest());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM referral_moves WHERE user_id IN (?, ?, ?)", Integer.class,
				top.id(), a.id(), b.id())).isZero();
		// The table itself allows only the referrer to change.
		assertThat(org.assertj.core.api.Assertions.catchThrowable(
				() -> jdbc.update("UPDATE referrals SET code = 'ZZZZZZZZ' WHERE user_id = ?", b.id())))
			.hasMessageContaining("only the referrer may change");
	}

	@Test
	void staffWhoSeeUsersSeeDownlinesButOnlyHoldersOfTheTreePermissionArrangeThem() throws Exception {
		Account top = api.register();
		Account a = api.register(codeOf(top));
		Account other = api.register();
		Account support = api.staff("SUPPORT");

		mvc.perform(get("/api/v1/admin/users/{id}/downline", top.id()).header(HttpHeaders.AUTHORIZATION, support.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.members[0].userId").value(a.id().toString()));
		mvc.perform(get("/api/v1/admin/users/{id}/downline", top.id()).header(HttpHeaders.AUTHORIZATION, top.bearer()))
			.andExpect(status().isForbidden());
		mvc.perform(post("/api/v1/admin/users/{id}/referrer", a.id()).header(HttpHeaders.AUTHORIZATION, support.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"referrerId\":\"%s\",\"reason\":\"x\"}".formatted(other.id())))
			.andExpect(status().isForbidden());
		mvc.perform(post("/api/v1/admin/users/{id}/referrer", a.id()).header(HttpHeaders.AUTHORIZATION, top.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"referrerId\":\"%s\",\"reason\":\"x\"}".formatted(other.id())))
			.andExpect(status().isForbidden());
	}

	private ResultActions downline(Account investor) throws Exception {
		return mvc.perform(get("/api/v1/admin/users/{id}/downline", investor.id())
			.header(HttpHeaders.AUTHORIZATION, api.admin().bearer()));
	}

	private ResultActions move(Account member, Account referrer, String reason) throws Exception {
		return mvc.perform(post("/api/v1/admin/users/{id}/referrer", member.id())
			.header(HttpHeaders.AUTHORIZATION, api.admin().bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content(referrer == null ? "{\"referrerId\":null,\"reason\":\"%s\"}".formatted(reason)
					: "{\"referrerId\":\"%s\",\"reason\":\"%s\"}".formatted(referrer.id(), reason)));
	}

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

	/**
	 * Moves the order's lease back so it started exactly one lease month before the current hour, and credits that
	 * month: its rent pays referral commission. Returns the holding id.
	 */
	private String payFirstPayout(String orderId) {
		UUID holding = jdbc.queryForObject("SELECT id FROM holdings WHERE order_id = ?::uuid", UUID.class, orderId);
		jdbc.update("""
				UPDATE rent_accruals
				SET starts_at = start, ends_at = (start AT TIME ZONE 'UTC' + make_interval(months => tenure_months)) AT TIME ZONE 'UTC',
				    credited_until = start
				FROM (SELECT (date_trunc('hour', now() AT TIME ZONE 'UTC') - interval '1 month') AT TIME ZONE 'UTC' AS start) t
				WHERE holding_id = ?""", holding);
		payouts.creditDue();
		return holding.toString();
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
