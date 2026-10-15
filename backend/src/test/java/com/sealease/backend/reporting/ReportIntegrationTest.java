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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
		finance = api.staff("ADMIN", "FINANCE");
	}

	// ------------------------------------------------------------------- investor statement

	@Test
	void anInvestorsStatementListsTheirWithdrawals() throws Exception {
		Account investor = investors.approvedInvestor();
		investors.ensureVerifiedBankAccount(investor);
		credit(investor, "INR", "500", "RENT_BANK");
		String today = today().toString();
		// Nothing withdrawn yet: the statement is empty.
		statement(investor, today, today, null).andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("Withdrawal statement"))
			.andExpect(jsonPath("$.sections.length()").value(1))
			.andExpect(jsonPath("$.sections[0].name").value("Withdrawals"))
			.andExpect(jsonPath("$.sections[0].rows.length()").value(0));

		// A rent withdrawal run takes the ₹500: it is listed, in progress.
		Account finance = api.staff("ADMIN", "FINANCE");
		mvc.perform(post("/api/v1/admin/withdrawal-runs").param("kind", "RENT").header(HttpHeaders.AUTHORIZATION, finance.bearer()))
			.andExpect(status().isCreated());
		String withdrawal = jdbc.queryForObject("SELECT id::text FROM withdrawals WHERE user_id = ?::uuid", String.class,
				investor.id());
		String reference = jdbc.queryForObject("SELECT reference FROM withdrawals WHERE id = ?::uuid", String.class, withdrawal);
		statement(investor, today, today, null)
			.andExpect(jsonPath("$.subtitle").value(containsString(investor.email())))
			.andExpect(jsonPath("$.sections[0].rows[0].c2").value(reference + " · Rent"))
			.andExpect(jsonPath("$.sections[0].rows[0].c3").value(containsString("In progress")))
			.andExpect(jsonPath("$.sections[0].rows[0].c4").value("₹500.00"))
			.andExpect(jsonPath("$.sections[0].rows[1].c1").value("In progress"))
			.andExpect(jsonPath("$.sections[0].rows[1].emphasis").value(true));

		// Finance pays it: the statement shows it paid, and the total paid out.
		api.takeFinanceTask(withdrawal, finance);
		mvc.perform(post("/api/v1/admin/finance-tasks/{id}", withdrawal).header(HttpHeaders.AUTHORIZATION, finance.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"PAID\",\"reference\":\"UTR-9\"}"))
			.andExpect(status().isOk());
		statement(investor, today, today, null)
			.andExpect(jsonPath("$.sections[0].rows[0].c3").value(containsString("Paid")))
			.andExpect(jsonPath("$.sections[0].rows[1].c1").value("Paid out"))
			.andExpect(jsonPath("$.sections[0].rows[1].c4").value("₹500.00"));

		byte[] pdf = statement(investor, today, today, "pdf").andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_PDF))
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("statement-" + today)))
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
			.andReturn().getResponse().getContentAsByteArray();
		assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
		String csv = statement(investor, today, today, "csv").andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertThat(csv).contains("\"Withdrawals\",\"Paid out\",\"\",\"\",\"₹500.00\"");

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
		credit(investor, "INR", "250", "RENT_BANK");
		credit(investor, "INR", "100", "REFERRAL");
		credit(investor, "INR", "50", "RENT_CASH");
		assertThat(summaryFigure(today, "Adjustments to investors").subtract(adjustmentsBefore)).isEqualByComparingTo("400");
		assertThat(summaryFigure(today, "Owed to investors").subtract(owedBefore)).isEqualByComparingTo("400");

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
			.andExpect(status().isOk()).andExpect(content().string(containsString("Withdrawals")));
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
		UUID product = new OfferingFixtures(mvc, admin).plan();
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
		assertThat(all).containsExactly("investors", "capital", "payouts", "owed", "withdrawals", "leads", "security");

		List<String> sales = JsonPath.read(mvc.perform(get("/api/v1/admin/dashboard")
				.header(HttpHeaders.AUTHORIZATION, api.staff("SALES").bearer())).andReturn().getResponse().getContentAsString(), "$[*].key");
		assertThat(sales).containsExactly("capital", "leads");

		// Support may look up users, investments and withdrawals for tickets, but sees no business figures.
		Account support = api.staff("SUPPORT");
		mvc.perform(get("/api/v1/admin/dashboard").header(HttpHeaders.AUTHORIZATION, support.bearer()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
		String charts = mvc.perform(get("/api/v1/admin/dashboard/charts").header(HttpHeaders.AUTHORIZATION, support.bearer()))
			.andReturn().getResponse().getContentAsString();
		assertThat((Object) JsonPath.read(charts, "$.invested")).isNull();
		assertThat((Object) JsonPath.read(charts, "$.investors")).isNull();

		// Nor when they also verify KYC: the roles' permissions add up, and neither brings the figures.
		Account supportAndVerification = api.staff("SUPPORT", "VERIFICATION");
		mvc.perform(get("/api/v1/admin/dashboard").header(HttpHeaders.AUTHORIZATION, supportAndVerification.bearer()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
		String both = mvc.perform(get("/api/v1/admin/dashboard/charts").header(HttpHeaders.AUTHORIZATION, supportAndVerification.bearer()))
			.andReturn().getResponse().getContentAsString();
		assertThat((Object) JsonPath.read(both, "$.invested")).isNull();
		assertThat((Object) JsonPath.read(both, "$.investors")).isNull();
	}

	@Test
	void supportSeesItsTicketWorkload() throws Exception {
		Account agent = api.staff("SUPPORT");
		Map<String, Integer> before = supportWorkload(agent);
		assertThat(before).containsEntry("assignedToMe", 0).containsEntry("doneThisMonthByMe", 0);
		mvc.perform(get("/api/v1/admin/dashboard/workload").header(HttpHeaders.AUTHORIZATION, agent.bearer()))
			.andExpect(jsonPath("$.financeTasks").doesNotExist()).andExpect(jsonPath("$.financeTickets").doesNotExist());

		String ticket = JsonPath.read(mvc.perform(multipart("/api/v1/support/tickets").param("subject", "Statement question")
				.param("category", "OTHER").param("message", "Please help.")
				.header(HttpHeaders.AUTHORIZATION, api.register().bearer()))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.ticket.id");
		mvc.perform(post("/api/v1/admin/support/tickets/{id}/assign", ticket).header(HttpHeaders.AUTHORIZATION, agent.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"assigneeId\":\"" + agent.id() + "\"}"))
			.andExpect(status().isOk());
		Map<String, Integer> assigned = supportWorkload(agent);
		assertThat(assigned.get("pending")).isEqualTo(before.get("pending") + 1);
		assertThat(assigned).containsEntry("assignedToMe", 1);
		// The priority table: every priority, most urgent first; the new ticket is NORMAL and the agent's.
		String workload = mvc.perform(get("/api/v1/admin/dashboard/workload").header(HttpHeaders.AUTHORIZATION, agent.bearer()))
			.andReturn().getResponse().getContentAsString();
		assertThat(JsonPath.<List<String>>read(workload, "$.supportByPriority[*].priority"))
			.containsExactly("URGENT", "HIGH", "NORMAL", "LOW");
		assertThat(JsonPath.<List<Integer>>read(workload, "$.supportByPriority[?(@.priority == 'NORMAL')].assignedToMe"))
			.containsExactly(1);
		assertThat(JsonPath.<List<Integer>>read(workload, "$.supportByPriority[?(@.priority != 'NORMAL')].assignedToMe"))
			.containsOnly(0);

		mvc.perform(post("/api/v1/admin/support/tickets/{id}/status", ticket).header(HttpHeaders.AUTHORIZATION, agent.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVED\"}"))
			.andExpect(status().isOk());
		Map<String, Integer> done = supportWorkload(agent);
		assertThat(done.get("pending")).isEqualTo(before.get("pending"));
		assertThat(done.get("doneToday")).isEqualTo(before.get("doneToday") + 1);
		assertThat(done).containsEntry("assignedToMe", 0).containsEntry("doneTodayByMe", 1).containsEntry("doneThisMonthByMe", 1);
	}

	private Map<String, Integer> supportWorkload(Account agent) throws Exception {
		return JsonPath.read(mvc.perform(get("/api/v1/admin/dashboard/workload").header(HttpHeaders.AUTHORIZATION, agent.bearer()))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.supportTickets");
	}

	@Test
	void theDashboardChartsShowOnlyWhatTheViewerMaySee() throws Exception {
		String all = mvc.perform(get("/api/v1/admin/dashboard/charts").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat((Object) JsonPath.read(all, "$.payouts")).isNotNull();
		assertThat((Object) JsonPath.read(all, "$.investors")).isNotNull();
		List<String> stages = JsonPath.read(all, "$.pipeline[*].stage");
		assertThat(stages).containsExactly("NEW", "CONTACTED", "QUALIFIED", "PROPOSAL", "WON", "LOST");

		String sales = mvc.perform(get("/api/v1/admin/dashboard/charts")
				.header(HttpHeaders.AUTHORIZATION, api.staff("SALES").bearer())).andReturn().getResponse().getContentAsString();
		assertThat((Object) JsonPath.read(sales, "$.payouts")).isNull();
		assertThat((Object) JsonPath.read(sales, "$.investors")).isNull();
		assertThat((Object) JsonPath.read(sales, "$.invested")).isNotNull();
		assertThat((Object) JsonPath.read(sales, "$.pipeline")).isNotNull();
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

	private ResultActions credit(Account investor, String currency, String amount, String balance) throws Exception {
		return mvc.perform(post("/api/v1/admin/ledger/adjustments").header(HttpHeaders.AUTHORIZATION, api.admin().bearer())
				.header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content("""
						{"userId":"%s","currency":"%s","direction":"CREDIT","amount":"%s","reason":"Report test","balance":"%s"}
						""".formatted(investor.id(), currency, amount, balance)))
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
