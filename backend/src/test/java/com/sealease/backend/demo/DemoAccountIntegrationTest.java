package com.sealease.backend.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.sealease.backend.support.IntegrationTest;
import com.sealease.backend.support.InvestorFixtures;
import com.sealease.backend.support.OfferingFixtures;
import com.sealease.backend.support.TestApi;
import com.sealease.backend.support.TestApi.Account;

/** Super administrators give a demo account dummy orders and a downline; its own pages show the figures. */
@IntegrationTest
class DemoAccountIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	private TestApi api;
	private InvestorFixtures investors;
	private Account admin;
	private UUID plan;

	@BeforeEach
	void setUp() throws Exception {
		api = new TestApi(mvc, jdbc);
		admin = api.admin();
		investors = new InvestorFixtures(mvc, jdbc, api);
		// ₹50,000 a container, 1 to 5 years.
		plan = new OfferingFixtures(mvc, admin).plan();
	}

	@Test
	void aDemoAccountShowsDummyFiguresAndIsNeverPaidOut() throws Exception {
		Account investor = investors.approvedInvestor();
		investors.ensureVerifiedBankAccount(investor);
		LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
		YearMonth month = YearMonth.from(today);
		LocalDate start = month.minusMonths(4).atDay(1);
		LocalDate end = month.plusMonths(2).atEndOfMonth();

		// Super administrators only.
		mvc.perform(get("/api/v1/admin/demo-accounts").header(HttpHeaders.AUTHORIZATION, api.staff("ADMIN").bearer()))
			.andExpect(status().isForbidden());
		send(post("/api/v1/admin/demo-accounts"), """
				{"userId":"%s","startsOn":"%s","endsOn":"%s"}""".formatted(api.staff("SUPPORT").id(), start, end))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.message").value(containsString("Only an investor")));

		String base = "/api/v1/admin/demo-accounts/" + investor.id();
		send(post("/api/v1/admin/demo-accounts"), """
				{"userId":"%s","startsOn":"%s","endsOn":"%s"}""".formatted(investor.id(), start, end))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.months.length()").value(7));
		send(post("/api/v1/admin/demo-accounts"), """
				{"userId":"%s","startsOn":"%s","endsOn":"%s"}""".formatted(investor.id(), start, end))
			.andExpect(status().isConflict());

		// Own order: 2 × ₹50,000 = ₹1,00,000 at its rent band's rate, from the frame's start.
		String added = send(post(base + "/orders"), order(null, 2, start)).andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		BigDecimal band = new BigDecimal(JsonPath.read(added, "$.orders[0].rentPercent").toString());
		String rent = new BigDecimal("100000").multiply(band).movePointLeft(2).setScale(2).toPlainString();
		assertThat(band).isEqualByComparingTo(rentFor(added, "100000"));
		send(get(base), "").andExpect(jsonPath("$.orders[0].monthlyRent.amount").value(rent))
			.andExpect(jsonPath("$.orders[0].productId").value(plan.toString()))
			.andExpect(jsonPath("$.orders[0].tenureMonths").value(12))
			.andExpect(jsonPath("$.plans[?(@.id == '%s')].price.amount".formatted(plan)).value(org.hamcrest.Matchers.hasItem("50000.00")));
		send(post(base + "/orders"), order(null, 1, start).replace("\"tenureYears\":1", "\"tenureYears\":9"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.message").value(containsString("1 to 5 years")));
		send(post(base + "/orders"), order(null, 1, start.minusDays(1)))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.message").value(containsString("within the time frame")));

		// Asha (level 1) invests ₹50,000 from the start: 2% commission = ₹1,000 a month.
		String asha = JsonPath.read(send(post(base + "/members"), """
				{"name":"Asha Demo","joinedOn":"%s"}""".formatted(start)).andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString(), "$.members[0].id");
		send(post(base + "/members"), """
				{"parentId":"%s","name":"Too Early","joinedOn":"%s"}""".formatted(asha, start.minusDays(1)))
			.andExpect(status().isUnprocessableContent());
		send(post(base + "/orders"), order(asha, 1, start)).andExpect(status().isOk())
			.andExpect(jsonPath("$.members[0].level").value(1));

		mvc.perform(get("/api/v1/admin/demo-accounts").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.userId == '%s')].orders".formatted(investor.id())).value(org.hamcrest.Matchers.hasItem(2)))
			.andExpect(jsonPath("$[?(@.userId == '%s')].userCode".formatted(investor.id()))
				.value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.startsWith("SL"))));

		// The investor's own pages.
		page(investor, "/api/v1/auth/me").andExpect(jsonPath("$.demo").value(true));
		page(investor, "/api/v1/portfolio")
			.andExpect(jsonPath("$.totalsByCurrency[0].amount").value("100000.00"))
			.andExpect(jsonPath("$.activeHoldings").value(2))
			.andExpect(jsonPath("$.holdings.length()").value(2));
		page(investor, "/api/v1/orders")
			.andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.content[0].orderNumber").value(startsWith("ORD-9")))
			.andExpect(jsonPath("$.content[0].status").value("CONFIRMED"))
			.andExpect(jsonPath("$.content[0].items[0].productId").value(plan.toString()));
		// Rent for each of the four past months on the 1st; commission on the 5th, this month's once the 5th has come.
		int referralPayouts = today.getDayOfMonth() >= 5 ? 4 : 3;
		page(investor, "/api/v1/withdrawals?size=50")
			.andExpect(jsonPath("$.totalElements").value(4 + referralPayouts))
			.andExpect(jsonPath("$.content[?(@.balance == 'RENT_BANK')].amount.amount", everyItem(is(rent))))
			.andExpect(jsonPath("$.content[?(@.balance == 'REFERRAL')].amount.amount", everyItem(is("1000.00"))))
			.andExpect(jsonPath("$.content[0].status").value("PAID"));
		// The statement lists the same payouts, then what was paid out in all.
		page(investor, "/api/v1/reports/statement?from=%s&to=%s".formatted(start, today))
			.andExpect(jsonPath("$.sections[0].rows.length()").value(4 + referralPayouts + 1))
			.andExpect(jsonPath("$.sections[0].rows[0].c2").value(startsWith("WD-D")));
		page(investor, "/api/v1/referrals/me")
			.andExpect(jsonPath("$.levels[0].members").value(1))
			.andExpect(jsonPath("$.eligible").value(true));
		page(investor, "/api/v1/referrals/downline").andExpect(jsonPath("$.members[0].displayName").value("Asha Demo"));
		page(investor, "/api/v1/referrals/monthly").andExpect(jsonPath("$[0].levels.length()").value(4));
		page(investor, "/api/v1/earnings/summary")
			.andExpect(jsonPath("$.totalWithdrawn[0].amount")
				.value(new BigDecimal(rent).multiply(BigDecimal.valueOf(4)).add(BigDecimal.valueOf(1000L * referralPayouts))
					.setScale(2).toPlainString()));
		page(investor, "/api/v1/earnings/monthly?from=" + start.toString().substring(0, 7) + "&to=" + month)
			.andExpect(jsonPath("$[0].rentPaid.amount").value(rent));

		// A real balance stays where it is: withdrawal runs leave demo accounts out.
		mvc.perform(post("/api/v1/admin/ledger/adjustments").header(HttpHeaders.AUTHORIZATION, admin.bearer())
				.header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content("""
						{"userId":"%s","currency":"INR","direction":"CREDIT","amount":"700","reason":"Test funding","balance":"RENT_BANK"}
						""".formatted(investor.id())))
			.andExpect(status().isCreated());
		mvc.perform(post("/api/v1/admin/withdrawal-runs").param("kind", "RENT").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.skipped[?(@.userId == '%s')].reason".formatted(investor.id()))
				.value(org.hamcrest.Matchers.hasItem("A demo account is not paid out")));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM withdrawals WHERE user_id = ?", Integer.class, investor.id()))
			.isZero();
		assertThat(api.auditCount("DEMO_ACCOUNT_CHANGED", investor.id())).isEqualTo(4);

		// Removing a member removes their orders; unmarking brings back the real figures.
		send(delete(base + "/members/" + asha), "").andExpect(status().isOk())
			.andExpect(jsonPath("$.orders.length()").value(1))
			.andExpect(jsonPath("$.members.length()").value(0));
		mvc.perform(delete(base).header(HttpHeaders.AUTHORIZATION, admin.bearer())).andExpect(status().isNoContent());
		page(investor, "/api/v1/auth/me").andExpect(jsonPath("$.demo").value(false));
		page(investor, "/api/v1/orders").andExpect(jsonPath("$.content.length()").value(0));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM demo_orders WHERE account_id = ?", Integer.class, investor.id()))
			.isZero();
	}

	private String order(String memberId, int containers, LocalDate startsOn) {
		return """
				{%s"productId":"%s","containers":%d,"tenureYears":1,"startsOn":"%s"}""".formatted(
				memberId == null ? "" : "\"memberId\":\"" + memberId + "\",", plan, containers, startsOn);
	}

	/** The rate of the band (listed with the account) an order total of {@code amount} falls in. */
	private static BigDecimal rentFor(String view, String amount) {
		java.util.List<java.util.Map<String, Object>> bands = JsonPath.read(view, "$.rentBands");
		BigDecimal total = new BigDecimal(amount);
		BigDecimal rate = null;
		for (java.util.Map<String, Object> band : bands) {
			BigDecimal from = new BigDecimal(((java.util.Map<?, ?>) band.get("fromAmount")).get("amount").toString());
			Object to = band.get("toAmount");
			BigDecimal upTo = to == null ? null : new BigDecimal(((java.util.Map<?, ?>) to).get("amount").toString());
			if (from.compareTo(total) <= 0 && (upTo == null || total.compareTo(upTo.add(BigDecimal.ONE)) < 0)) {
				rate = new BigDecimal(band.get("monthlyRentPercent").toString());
			}
		}
		return rate;
	}

	private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
			String body) throws Exception {
		return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, admin.bearer()).contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private ResultActions page(Account account, String path) throws Exception {
		return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)
			.header(HttpHeaders.AUTHORIZATION, account.bearer())).andExpect(status().isOk());
	}

}
