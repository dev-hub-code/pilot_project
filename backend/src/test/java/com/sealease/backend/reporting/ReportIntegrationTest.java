package com.sealease.backend.reporting;

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

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase 11: statements, financial summary, offerings report, invoice PDFs and the KPI dashboard. */
@IntegrationTest
class ReportIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	private TestApi api;
	private InvestorFixtures investors;
	private Account admin;
	private Account finance;

	@BeforeEach
	void setUp() throws Exception {
		api = new TestApi(mvc, jdbc);
		investors = new InvestorFixtures(mvc, jdbc, api);
		admin = api.admin();
		finance = api.staff("FINANCE");
	}

	// ------------------------------------------------------------------- investor statement

	@Test
	void anInvestorsStatementMatchesTheirLedger() throws Exception {
		Account investor = investors.approvedInvestor();
		credit(investor, "INR", "500");

		String today = today().toString();
		statement(investor, today, today, null).andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("Investor statement"))
			.andExpect(jsonPath("$.subtitle").value(containsString(investor.email())))
			.andExpect(jsonPath("$.sections[0].name").value("Summary (₹)"))
			.andExpect(jsonPath("$.sections[0].rows[0].c4").value("₹0.00"))
			.andExpect(jsonPath("$.sections[0].rows[3].c4").value("₹500.00"))
			.andExpect(jsonPath("$.sections[0].rows[5].c1").value("Closing balance"))
			.andExpect(jsonPath("$.sections[0].rows[5].c4").value("₹500.00"))
			.andExpect(jsonPath("$.sections[0].rows[5].emphasis").value(true))
			.andExpect(jsonPath("$.sections[1].rows.length()").value(1))
			.andExpect(jsonPath("$.sections[1].rows[0].c3").value("Adjustment"));

		byte[] pdf = statement(investor, today, today, "pdf").andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_PDF))
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("statement-" + today)))
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
			.andReturn().getResponse().getContentAsByteArray();
		assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
		String csv = statement(investor, today, today, "csv").andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertThat(csv).contains("\"Summary (₹)\",\"Closing balance\",\"\",\"\",\"₹500.00\"");

		statement(investor, today, today().minusDays(1).toString(), null).andExpect(status().isBadRequest());
		statement(investor, today().minusDays(400).toString(), today, null).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(containsString("366 days")));
	}

	// ------------------------------------------------------------------------ staff reports

	@Test
	void staffReportsNeedReportPermissionsAndDownloadsAreAudited() throws Exception {
		Account investor = investors.approvedInvestor();
		String today = today().toString();
		// Other tests post in the same currency on the same day, so compare totals before and after.
		java.math.BigDecimal adjustmentsBefore = summaryFigure(today, "Adjustments to investors");
		java.math.BigDecimal owedBefore = summaryFigure(today, "Owed to investors");
		credit(investor, "INR", "250");
		assertThat(summaryFigure(today, "Adjustments to investors").subtract(adjustmentsBefore)).isEqualByComparingTo("250");
		assertThat(summaryFigure(today, "Owed to investors").subtract(owedBefore)).isEqualByComparingTo("250");

		Account viewer = api.staff("REPORT_VIEWER");
		mvc.perform(get("/api/v1/admin/reports/offerings").param("format", "pdf").header(HttpHeaders.AUTHORIZATION, viewer.bearer()))
			.andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'REPORT_GENERATED' AND actor_user_id = ?",
				Integer.class, viewer.id())).isEqualTo(1);
		List<String> sections = JsonPath.read(mvc.perform(get("/api/v1/admin/reports/offerings")
				.header(HttpHeaders.AUTHORIZATION, viewer.bearer())).andReturn().getResponse().getContentAsString(), "$.sections[*].name");
		assertThat(sections).containsExactly("Plans", "Payouts due and not yet paid");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'REPORT_GENERATED' AND actor_user_id = ?",
				Integer.class, viewer.id())).isEqualTo(1);

		mvc.perform(get("/api/v1/admin/reports/statement").param("userId", investor.id().toString()).param("from", today)
				.param("to", today).param("format", "csv").header(HttpHeaders.AUTHORIZATION, viewer.bearer()))
			.andExpect(status().isOk()).andExpect(content().string(containsString("₹250.00")));
		mvc.perform(get("/api/v1/admin/reports/offerings").header(HttpHeaders.AUTHORIZATION, api.staff("SUPPORT").bearer()))
			.andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/admin/reports/offerings").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/admin/reports/offerings").param("format", "xlsx").header(HttpHeaders.AUTHORIZATION, viewer.bearer()))
			.andExpect(status().isBadRequest());
	}

	// ------------------------------------------------------------------------------ invoice

	@Test
	void investorsDownloadTheirOwnInvoicesAsPdf() throws Exception {
		Account investor = investors.approvedInvestor();
		UUID product = new OfferingFixtures(mvc, admin).plan(1);
		String orderId = investors.invest(investor, product, 1);

		byte[] pdf = mvc.perform(get("/api/v1/reports/invoices/{id}", orderId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("invoice-INV-")))
			.andReturn().getResponse().getContentAsByteArray();
		assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
		mvc.perform(get("/api/v1/reports/invoices/{id}", orderId).param("format", "json").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.sections[1].rows[1].c2").value("Total"))
			.andExpect(jsonPath("$.sections[1].rows[1].c4").value("₹50,000.00"));

		mvc.perform(get("/api/v1/reports/invoices/{id}", orderId).header(HttpHeaders.AUTHORIZATION, investors.approvedInvestor().bearer()))
			.andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/admin/reports/invoices/{id}", orderId).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
			.andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF));
	}

	// ---------------------------------------------------------------------------- dashboard

	@Test
	void theDashboardShowsOnlyWhatTheViewerMaySee() throws Exception {
		List<String> all = JsonPath.read(mvc.perform(get("/api/v1/admin/dashboard").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$[*].key");
		assertThat(all).containsExactly("investors", "capital", "payouts", "owed", "withdrawals", "support", "leads");

		List<String> sales = JsonPath.read(mvc.perform(get("/api/v1/admin/dashboard")
				.header(HttpHeaders.AUTHORIZATION, api.staff("SALES").bearer())).andReturn().getResponse().getContentAsString(), "$[*].key");
		assertThat(sales).containsExactly("capital", "leads");
	}

	// ----------------------------------------------------------------------------- helpers

	/** One INR figure of today's financial summary, e.g. "₹1,23,450.00" → 123450.00. */
	private java.math.BigDecimal summaryFigure(String day, String label) throws Exception {
		String summary = mvc.perform(get("/api/v1/admin/reports/financial-summary").param("from", day).param("to", day)
				.header(HttpHeaders.AUTHORIZATION, finance.bearer()))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		List<String> values = JsonPath.read(summary, "$.sections[?(@.name == 'Totals (₹)')].rows[*].c4");
		List<String> labels = JsonPath.read(summary, "$.sections[?(@.name == 'Totals (₹)')].rows[*].c1");
		return labels.contains(label)
				? new java.math.BigDecimal(values.get(labels.indexOf(label)).replace("₹", "").replace(",", ""))
				: java.math.BigDecimal.ZERO;
	}

	private ResultActions credit(Account investor, String currency, String amount) throws Exception {
		return mvc.perform(post("/api/v1/admin/ledger/adjustments").header(HttpHeaders.AUTHORIZATION, finance.bearer())
				.header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content("""
						{"userId":"%s","currency":"%s","direction":"CREDIT","amount":"%s","reason":"Report test"}
						""".formatted(investor.id(), currency, amount)))
			.andExpect(status().isCreated());
	}

	private ResultActions statement(Account investor, String from, String to, String format) throws Exception {
		var request = get("/api/v1/reports/statement").param("from", from).param("to", to)
			.header(HttpHeaders.AUTHORIZATION, investor.bearer());
		return mvc.perform(format == null ? request : request.param("format", format));
	}

	private static LocalDate today() {
		return LocalDate.now(ZoneOffset.UTC);
	}

}
