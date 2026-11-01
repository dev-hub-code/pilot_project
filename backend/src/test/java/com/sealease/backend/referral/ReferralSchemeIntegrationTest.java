package com.sealease.backend.referral;

import com.jayway.jsonpath.JsonPath;
import com.sealease.backend.earning.service.PayoutService;
import com.sealease.backend.referral.service.ReferralService;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The two referral schemes: network investors earn monthly on four levels, online investors once on level 1. */
@IntegrationTest
class ReferralSchemeIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PayoutService payouts;

	@Autowired
	private ReferralService referrals;

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

	// ------------------------------------------------------------------------- online scheme

	@Test
	void anOnlineReferrerEarnsOnceOnTheWholeFirstOrderWhenItsLeaseStarts() throws Exception {
		Account referrer = investors.approvedInvestor();
		Account referred = investors.approve(api.register(code(referrer)));
		UUID plan = offerings.plan();

		// Paid, but the lease has not started: nothing yet.
		String orderId = investors.paidOrder(referred, plan, 2);
		assertThat(earnedBy(referrer)).isZero();

		List<UUID> holdings = investors.awaitingAcceptance(orderId);
		investors.acceptAgreement(referred, holdings.get(0));
		// 2% of the whole order.
		assertThat(earnedBy(referrer)).isEqualByComparingTo("2000");
		assertThat(commissions(referrer)).containsExactly("ONE_TIME/1");

		// A later order and months of returns pay nothing more.
		String second = investors.invest(referred, plan, 1);
		creditFirstMonth(second);
		assertThat(earnedBy(referrer)).isEqualByComparingTo("2000");
		assertThat(commissions(referrer)).hasSize(1);
		mvc.perform(get("/api/v1/referrals/me").header(HttpHeaders.AUTHORIZATION, referrer.bearer()))
			.andExpect(jsonPath("$.scheme").value("ONLINE"))
			.andExpect(jsonPath("$.oneTimePercent").value(2.0))
			.andExpect(jsonPath("$.totalEarned[0].amount").value("2000.00"));
		assertThat(jdbc.queryForList("""
				SELECT sum(CASE direction WHEN 'DEBIT' THEN amount ELSE -amount END) = 0
				FROM ledger_entries e JOIN ledger_transactions t ON t.id = e.transaction_id GROUP BY t.currency
				""", Boolean.class)).containsOnly(true);
	}

	@Test
	void anOnlineReferrerEarnsNothingBelowLevelOne() throws Exception {
		Account top = investors.approvedInvestor();
		Account middle = investors.approve(api.register(code(top)));
		Account investor = investors.approve(api.register(code(middle)));

		investors.invest(investor, offerings.plan(), 1);

		assertThat(earnedBy(middle)).isEqualByComparingTo("1000");
		assertThat(earnedBy(top)).isZero();
	}

	@Test
	void theOneTimeRateIsConfiguredApartFromTheMonthlyRates() throws Exception {
		String response = mvc.perform(post("/api/v1/admin/referral-rates").header(HttpHeaders.AUTHORIZATION, admin.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("""
					{"effectiveFrom":"%s","percents":["2","1","0.5","0.25"],"oneTimePercent":"3","reason":"One-time review"}
					""".formatted(Instant.now().plus(3650, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS))))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.oneTimePercent").value(3))
			.andExpect(jsonPath("$.percents[0]").value(2))
			.andReturn().getResponse().getContentAsString();
		mvc.perform(post("/api/v1/admin/referral-rates/{id}/cancel", JsonPath.<String>read(response, "$.id"))
			.header(HttpHeaders.AUTHORIZATION, admin.bearer())).andExpect(status().isOk());
	}

	@Test
	void anIneligibleOnlineReferrerIsNotPaid() throws Exception {
		Account referrer = api.register();
		Account referred = investors.approve(api.register(code(referrer)));

		investors.invest(referred, offerings.plan(), 1);

		assertThat(earnedBy(referrer)).isZero();
	}

	// ------------------------------------------------------------------------ network scheme

	@Test
	void networkUplinesEarnMonthlyAndNeverOnce() throws Exception {
		Account top = network(investors.approvedInvestor());
		Account online = investors.approve(api.register(code(top)));
		Account investor = investors.approve(api.register(code(online)));

		String orderId = investors.invest(investor, offerings.plan(), 1);
		assertThat(earnedBy(top)).isZero();
		creditFirstMonth(orderId);

		// The online investor between them keeps their one payment; the network investor earns level 2 monthly (1%).
		assertThat(commissions(top)).containsOnly("MONTHLY/2");
		assertThat(earnedBy(top)).isEqualByComparingTo("500");
		assertThat(commissions(online)).containsExactly("ONE_TIME/1");
		assertThat(earnedBy(online)).isEqualByComparingTo("1000");
	}

	@Test
	void anInvestorReferredByANetworkInvestorIsNotPaidOneTimeForThem() throws Exception {
		Account referrer = network(investors.approvedInvestor());
		Account referred = investors.approve(api.register(code(referrer)));

		investors.invest(referred, offerings.plan(), 1);

		assertThat(commissions(referrer)).isEmpty();
	}

	// ------------------------------------------------------------------- made by administrators

	@Test
	void anAdministratorMakesANetworkInvestorWhoseSignupsStayOnline() throws Exception {
		UUID id = createInvestor(false, "").andExpect(status().isCreated())
			.andExpect(jsonPath("$.temporaryPassword").isNotEmpty())
			.andReturn().getResponse().getContentAsString().transform(ReferralSchemeIntegrationTest::userId);

		scheme(id).andExpect(jsonPath("$.scheme").value("NETWORK"))
			.andExpect(jsonPath("$.signupsJoinNetwork").value(false));
		assertThat(jdbc.queryForObject("SELECT must_change_password FROM users WHERE id = ?", Boolean.class, id)).isTrue();
		assertThat(api.auditCount("INVESTOR_ACCOUNT_CREATED", id)).isEqualTo(1);

		Account signup = api.register(referrals.codeOf(id));
		scheme(signup.id()).andExpect(jsonPath("$.scheme").value("ONLINE"));
		scheme(api.register().id()).andExpect(jsonPath("$.scheme").value("ONLINE"));
	}

	@Test
	void signupsOfANetworkInvestorCanJoinTheNetworkAndPassItOn() throws Exception {
		UUID id = createInvestor(true, "").andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString().transform(ReferralSchemeIntegrationTest::userId);

		Account signup = api.register(referrals.codeOf(id));
		scheme(signup.id()).andExpect(jsonPath("$.scheme").value("NETWORK"))
			.andExpect(jsonPath("$.signupsJoinNetwork").value(true));
		Account next = api.register(referrals.codeOf(signup.id()));
		scheme(next.id()).andExpect(jsonPath("$.scheme").value("NETWORK"));
	}

	@Test
	void aNetworkInvestorKeepsTheirOwnSettingWhenReferredByOneWhoseSignupsJoin() throws Exception {
		UUID top = createInvestor(true, "").andReturn().getResponse().getContentAsString()
			.transform(ReferralSchemeIntegrationTest::userId);

		UUID id = createInvestor(false, referrals.codeOf(top)).andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString().transform(ReferralSchemeIntegrationTest::userId);

		scheme(id).andExpect(jsonPath("$.scheme").value("NETWORK"))
			.andExpect(jsonPath("$.signupsJoinNetwork").value(false));
	}

	@Test
	void onlyHoldersOfThePermissionMakeInvestors() throws Exception {
		mvc.perform(post("/api/v1/admin/investors").header(HttpHeaders.AUTHORIZATION, api.register().bearer())
			.contentType(MediaType.APPLICATION_JSON).content(investorJson(false, "")))
			.andExpect(status().isForbidden());
	}

	// ----------------------------------------------------------------------------- helpers

	private static UUID userId(String json) {
		return UUID.fromString(JsonPath.read(json, "$.userId"));
	}

	private ResultActions createInvestor(boolean signupsJoinNetwork, String referralCode) throws Exception {
		return mvc.perform(post("/api/v1/admin/investors").header(HttpHeaders.AUTHORIZATION, admin.bearer())
			.contentType(MediaType.APPLICATION_JSON).content(investorJson(signupsJoinNetwork, referralCode)));
	}

	private static String investorJson(boolean signupsJoinNetwork, String referralCode) {
		String phone = "9" + String.format("%09d", Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000_000L));
		return """
				{"email":"network-%s@example.com","firstName":"Nia","lastName":"Network","phone":"%s","phoneCountry":"IN",
				 "referralCode":"%s","signupsJoinNetwork":%s}
				""".formatted(UUID.randomUUID(), phone, referralCode, signupsJoinNetwork);
	}

	private ResultActions scheme(UUID userId) throws Exception {
		return mvc.perform(get("/api/v1/admin/users/{id}/referrals", userId).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
			.andExpect(status().isOk());
	}

	private Account network(Account account) {
		jdbc.update("INSERT INTO network_investors (user_id, signups_join_network, created_at) VALUES (?, false, now())",
				account.id());
		return account;
	}

	private String code(Account account) {
		return referrals.codeOf(account.id());
	}

	private BigDecimal earnedBy(Account account) {
		return jdbc.queryForObject("SELECT coalesce(sum(amount), 0) FROM referral_earnings WHERE beneficiary_user_id = ?",
				BigDecimal.class, account.id());
	}

	private List<String> commissions(Account account) {
		return jdbc.queryForList("SELECT kind || '/' || level FROM referral_earnings WHERE beneficiary_user_id = ?",
				String.class, account.id());
	}

	/** Moves the order's lease back so it started one lease month ago, and credits that month. */
	private void creditFirstMonth(String orderId) {
		payFirstPayout(orderId);
	}

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

}
