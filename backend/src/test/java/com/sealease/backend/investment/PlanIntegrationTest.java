package com.sealease.backend.investment;

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

import java.math.BigDecimal;
import java.util.UUID;

import static com.sealease.backend.support.OfferingFixtures.planBody;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Investment plans: their lifecycle, who sees them, eligibility and what a number of units costs and pays. */
@IntegrationTest
class PlanIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	private TestApi api;
	private Account admin;
	private OfferingFixtures offerings;

	@BeforeEach
	void setUp() throws Exception {
		api = new TestApi(mvc, jdbc);
		admin = api.admin();
		offerings = new OfferingFixtures(mvc, admin);
	}

	@Test
	void planLifecycleDraftPublishCloseWithFrozenTerms() throws Exception {
		String productId = JsonPath.read(create(planBody("ONLINE", "3", 3, 10))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.code").value(matchesPattern("LST-\\d{5,}")))
			.andExpect(jsonPath("$.audience").value("ONLINE"))
			.andExpect(jsonPath("$.tenureYears").value(3))
			.andExpect(jsonPath("$.annualReturnPercent").value(36))
			.andExpect(jsonPath("$.unitPrice").value(500))
			.andExpect(jsonPath("$.minUnits").value(10))
			.andReturn().getResponse().getContentAsString(), "$.id");

		// Invisible to investors while in draft; editable by staff.
		Account investor = approvedInvestor();
		plan(productId, investor).andExpect(status().isNotFound());
		update(productId, planBody("ONLINE", "3.5", 5, 2)).andExpect(status().isOk())
			.andExpect(jsonPath("$.tenureYears").value(5))
			.andExpect(jsonPath("$.annualReturnPercent").value(42))
			.andExpect(jsonPath("$.unitPrice").value(500));

		publish(productId).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OPEN"));
		publish(productId).andExpect(status().isConflict());
		update(productId, planBody("ONLINE", "5", 5, 2)).andExpect(status().isUnprocessableContent());
		cancel(productId).andExpect(status().isUnprocessableContent());

		mvc.perform(post("/api/v1/admin/investment-products/{id}/close", productId).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CLOSED"))
			.andExpect(jsonPath("$.closedAt").isNotEmpty());
		plan(productId, investor).andExpect(status().isOk())
			.andExpect(jsonPath("$.eligibility.eligible").value(false))
			.andExpect(jsonPath("$.eligibility.reasons", hasItem("This plan is not accepting investments")));
		assertThat(api.auditCount("PRODUCT_CLOSED", productId)).isEqualTo(1);

		mvc.perform(post("/api/v1/admin/investment-products/{id}/reopen", productId).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("OPEN"))
			.andExpect(jsonPath("$.closedAt").isEmpty());
		plan(productId, investor).andExpect(status().isOk())
			.andExpect(jsonPath("$.eligibility.reasons", not(hasItem("This plan is not accepting investments"))));
		assertThat(api.auditCount("PRODUCT_REOPENED", productId)).isEqualTo(1);
	}

	@Test
	void plansNeedAValidTenureReturnPriceAndMinimum() throws Exception {
		create(planBody("ONLINE", "3", 0, 1)).andExpect(status().isBadRequest());
		create(planBody("ONLINE", "3", 31, 1)).andExpect(status().isBadRequest());
		create(planBody("ONLINE", "0", 3, 1)).andExpect(status().isBadRequest());
		create(planBody("ONLINE", "3", 3, 0)).andExpect(status().isBadRequest());
		String productId = offerings.draft("ONLINE", "3").toString();
		cancel(productId).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
		publish(productId).andExpect(status().isConflict());
	}

	@Test
	void publishingRequiresApprovalPermission() throws Exception {
		Account administration = api.staff("ADMINISTRATION");
		String productId = JsonPath.read(mvc.perform(post("/api/v1/admin/investment-products")
				.header(HttpHeaders.AUTHORIZATION, administration.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(planBody("ONLINE", "2", 1, 1)))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
		mvc.perform(post("/api/v1/admin/investment-products/{id}/publish", productId)
				.header(HttpHeaders.AUTHORIZATION, administration.bearer()))
			.andExpect(status().isForbidden());
	}

	@Test
	void plansAreListedShortestTenureFirst() throws Exception {
		String five = offerings.published("ONLINE", "4", 5).toString();
		String three = offerings.published("ONLINE", "3", 3).toString();
		Account investor = approvedInvestor();
		String page = mvc.perform(get("/api/v1/plans").param("size", "200").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[?(@.id == '%s')].annualReturnPercent".formatted(three), contains(36.0)))
			.andExpect(jsonPath("$.content[?(@.id == '%s')].unitPrice.amount".formatted(five), contains("500.00")))
			.andReturn().getResponse().getContentAsString();
		assertThat(page.indexOf(three)).isLessThan(page.indexOf(five));
	}

	@Test
	void eligibilityExplainsWhyAnInvestorCannotInvest() throws Exception {
		UUID product = offerings.plan();
		Account unverified = api.register();
		plan(product.toString(), unverified)
			.andExpect(jsonPath("$.eligibility.eligible").value(false))
			.andExpect(jsonPath("$.eligibility.reasons", hasItem("Verify your identity before investing")));

		// Verified identity alone is not enough: a verified bank account is needed too.
		Account noBankAccount = api.register();
		jdbc.update("UPDATE user_profiles SET kyc_status = 'APPROVED' WHERE user_id = ?", noBankAccount.id());
		plan(product.toString(), noBankAccount)
			.andExpect(jsonPath("$.eligibility.eligible").value(false))
			.andExpect(jsonPath("$.eligibility.reasons", contains("Add a bank account and have it verified before investing")));

		plan(product.toString(), approvedInvestor()).andExpect(jsonPath("$.eligibility.eligible").value(true));
	}

	@Test
	void aQuoteShowsWhatUnitsCostAndPay() throws Exception {
		UUID product = offerings.published("ONLINE", "3", 3, 10);
		Account investor = approvedInvestor();
		// 200 × ₹500 = ₹1,00,000 at 3% a month: ₹3,000 for 36 months.
		quote(product, investor, "200").andExpect(status().isOk())
			.andExpect(jsonPath("$.valid").value(true))
			.andExpect(jsonPath("$.amount.amount").value("100000.00"))
			.andExpect(jsonPath("$.unitPrice.amount").value("500.00"))
			.andExpect(jsonPath("$.annualReturnPercent").value(36))
			.andExpect(jsonPath("$.monthlyReturns.amount").value("3000.00"))
			.andExpect(jsonPath("$.payouts").value(36))
			.andExpect(jsonPath("$.totalReturns.amount").value("108000.00"));
		quote(product, investor, "9")
			.andExpect(jsonPath("$.valid").value(false))
			.andExpect(jsonPath("$.problems[0]").value(containsString("Buy at least 10 units")));
		quote(product, investor, "0").andExpect(status().isBadRequest());
	}

	@Test
	void anAnnualReturnIsPaidMonthlyWithoutLosingPrecision() throws Exception {
		UUID product = offerings.published("ONLINE", "1.25", 3);
		jdbc.update("UPDATE investment_products SET annual_return_percent = 16 WHERE id = ?", product);
		Account investor = approvedInvestor();
		// ₹1,00,000 at 16% a year: ₹1,333.33 a month, not the ₹1,330 a rate cut to 1.33% would pay.
		quote(product, investor, "200")
			.andExpect(jsonPath("$.annualReturnPercent").value(16))
			.andExpect(jsonPath("$.monthlyReturns.amount").value("1333.33"));
		String orderId = new InvestorFixtures(mvc, jdbc, api).placeOrder(investor, product, 2);
		assertThat(jdbc.queryForObject("SELECT monthly_rent_percent FROM order_items WHERE order_id = ?::uuid",
				BigDecimal.class, orderId)).isEqualByComparingTo("1.33333333");
	}

	@Test
	void anInvestmentCannotGoOverThePlatformLimit() throws Exception {
		UUID product = offerings.plan();
		Account investor = approvedInvestor();
		quote(product, investor, "2000001")
			.andExpect(jsonPath("$.valid").value(false))
			.andExpect(jsonPath("$.problems[0]").value(containsString("buy fewer units")));
	}

	@Test
	void theWebsiteListsOpenOnlinePlansWithoutSigningIn() throws Exception {
		offerings.published("ONLINE", "3", 3);
		mvc.perform(get("/api/v1/public/plans"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].tenureYears").isNumber())
			.andExpect(jsonPath("$[0].annualReturnPercent").isNumber())
			.andExpect(jsonPath("$[0].unitPrice.amount").isNotEmpty())
			.andExpect(jsonPath("$[0].minUnits").isNumber())
			.andExpect(jsonPath("$[0].id").doesNotExist());
		mvc.perform(post("/api/v1/public/plans")).andExpect(status().isUnauthorized());
	}

	@Test
	void eachInvestorTypeSeesAndBuysOnlyItsOwnPlans() throws Exception {
		UUID online = offerings.published("ONLINE", "3");
		UUID network = offerings.published("NETWORK", "7.25");
		Account onlineInvestor = approvedInvestor();
		Account networkInvestor = approvedInvestor();
		new InvestorFixtures(mvc, jdbc, api).enrolInNetwork(networkInvestor);

		mvc.perform(get("/api/v1/plans").param("size", "200").header(HttpHeaders.AUTHORIZATION, onlineInvestor.bearer()))
			.andExpect(jsonPath("$.content[*].id", hasItem(online.toString())))
			.andExpect(jsonPath("$.content[*].id", not(hasItem(network.toString()))));
		mvc.perform(get("/api/v1/plans").param("size", "200").header(HttpHeaders.AUTHORIZATION, networkInvestor.bearer()))
			.andExpect(jsonPath("$.content[*].id", hasItem(network.toString())))
			.andExpect(jsonPath("$.content[*].id", not(hasItem(online.toString()))));

		plan(network.toString(), onlineInvestor).andExpect(status().isNotFound());
		mvc.perform(post("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, onlineInvestor.bearer())
				.header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"productId":"%s","units":1,"acceptTerms":true}""".formatted(network)))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.message").value(containsString("not offered to your investor type")));
		String code = jdbc.queryForObject("SELECT code FROM investment_products WHERE id = ?", String.class, network);
		mvc.perform(get("/api/v1/open/plans/{code}", code)).andExpect(status().isNotFound());

		// Each order is fixed at the rate of its plan.
		String orderId = new InvestorFixtures(mvc, jdbc, api).placeOrder(networkInvestor, network, 1);
		assertThat(jdbc.queryForObject("SELECT monthly_rent_percent FROM order_items WHERE order_id = ?::uuid",
				BigDecimal.class, orderId)).isEqualByComparingTo("7.25");
	}

	/** Identity verified and a verified bank account: able to buy. */
	private Account approvedInvestor() throws Exception {
		Account investor = api.register();
		jdbc.update("UPDATE user_profiles SET kyc_status = 'APPROVED' WHERE user_id = ?", investor.id());
		new InvestorFixtures(mvc, jdbc, api).ensureVerifiedBankAccount(investor);
		return investor;
	}

	private ResultActions plan(String productId, Account viewer) throws Exception {
		return mvc.perform(get("/api/v1/plans/{id}", productId).header(HttpHeaders.AUTHORIZATION, viewer.bearer()));
	}

	private ResultActions quote(UUID productId, Account viewer, String units) throws Exception {
		return mvc.perform(get("/api/v1/plans/{id}/quote", productId).param("units", units)
			.header(HttpHeaders.AUTHORIZATION, viewer.bearer()));
	}

	private ResultActions create(String body) throws Exception {
		return mvc.perform(post("/api/v1/admin/investment-products").header(HttpHeaders.AUTHORIZATION, admin.bearer())
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions update(String productId, String body) throws Exception {
		return mvc.perform(put("/api/v1/admin/investment-products/{id}", productId).header(HttpHeaders.AUTHORIZATION, admin.bearer())
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions publish(String productId) throws Exception {
		return mvc.perform(post("/api/v1/admin/investment-products/{id}/publish", productId)
			.header(HttpHeaders.AUTHORIZATION, admin.bearer()));
	}

	private ResultActions cancel(String productId) throws Exception {
		return mvc.perform(post("/api/v1/admin/investment-products/{id}/cancel", productId)
			.header(HttpHeaders.AUTHORIZATION, admin.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Not going ahead\"}"));
	}

}
