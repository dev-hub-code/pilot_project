package com.sealease.backend.reporting;

import com.jayway.jsonpath.JsonPath;
import com.sealease.backend.reporting.catalog.ColumnType;
import com.sealease.backend.reporting.catalog.ReportCatalog;
import com.sealease.backend.reporting.catalog.ReportColumn;
import com.sealease.backend.reporting.catalog.ReportDefinition;
import com.sealease.backend.reporting.catalog.ReportQuery;
import com.sealease.backend.reporting.catalog.ReportQueryEngine;
import com.sealease.backend.reporting.catalog.ReportResult;
import com.sealease.backend.support.IntegrationTest;
import com.sealease.backend.support.InvestorFixtures;
import com.sealease.backend.support.TestApi;
import com.sealease.backend.support.TestApi.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Reports &amp; analytics: every catalogue query runs, figures reconcile, access follows the existing permissions. */
@IntegrationTest
class AnalyticsIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ReportCatalog catalog;

	@Autowired
	private ReportQueryEngine engine;

	private TestApi api;
	private InvestorFixtures investors;

	@BeforeEach
	void setUp() {
		api = new TestApi(mvc, jdbc);
		investors = new InvestorFixtures(mvc, jdbc, api);
	}

	private static final LocalDate FROM = LocalDate.now().minusYears(1);
	private static final LocalDate TO = LocalDate.now().plusDays(1);

	@Test
	void everyReportRunsWithEverySortAndFilterAgainstTheDatabase() throws Exception {
		Account investor = investors.approvedInvestor();
		credit(investor, "1250.50");
		assertThat(catalog.all()).hasSizeGreaterThanOrEqualTo(40);
		for (ReportDefinition report : catalog.all()) {
			// No period, a period, and the previous period.
			ReportQuery all = new ReportQuery(null, null, Map.of(), null, null, null, null, null, 0, 20);
			ReportQuery period = new ReportQuery(FROM, TO, Map.of(), null, null, null, null, null, 0, 20);
			for (ReportQuery query : List.of(all, period, period.previousPeriod())) {
				ReportResult result = engine.page(report, query, p -> true);
				assertThat(result.columns()).as(report.key()).isNotEmpty();
				assertThat(result.rows()).as(report.key()).allSatisfy(row -> assertThat(row).hasSize(result.columns().size()));
			}
			for (ReportColumn column : report.columns()) {
				if (column.sortable() && !column.hidden()) {
					engine.page(report, new ReportQuery(FROM, TO, Map.of(), null, null, null, column.key(), false, 0, 5), p -> true);
				}
			}
			Map<String, List<String>> choices = new java.util.LinkedHashMap<>();
			report.choices().forEach(c -> choices.put(c.key(), List.of(c.values().getFirst())));
			engine.page(report, new ReportQuery(FROM, TO, choices, report.searchSql().isEmpty() ? null : "x%_'\\",
					report.amountSql() == null ? null : BigDecimal.ONE, report.amountSql() == null ? null : BigDecimal.TEN,
					null, null, 1, 5), p -> true);
			engine.all(report, period, p -> true);
		}
	}

	@Test
	void theTrialBalanceBalancesAndTheLedgerReconciles() throws Exception {
		Account investor = investors.approvedInvestor();
		credit(investor, "777.77");
		ReportDefinition trial = catalog.find("trial-balance").orElseThrow();
		ReportResult result = engine.all(trial, new ReportQuery(FROM, TO, Map.of(), null, null, null, null, null, 0, 1), p -> true);
		assertThat(result.totals()).isNotEmpty();
		for (ReportResult.Totals totals : result.totals()) {
			Map<String, Object> t = totals.values();
			assertThat(money(t.get("openingDebit"))).isEqualByComparingTo(money(t.get("openingCredit")));
			assertThat(money(t.get("periodDebit"))).isEqualByComparingTo(money(t.get("periodCredit")));
			assertThat(money(t.get("closingDebit"))).isEqualByComparingTo(money(t.get("closingCredit")));
		}
		// The general ledger lists the adjustment, and its debits equal its credits.
		ReportResult gl = engine.all(catalog.find("general-ledger").orElseThrow(),
				new ReportQuery(FROM, TO, Map.of("transaction", List.of("ADJUSTMENT")), null, null, null, null, null, 0, 1), p -> true);
		assertThat(gl.rows()).isNotEmpty();
		gl.totals().forEach(t -> assertThat(money(t.values().get("debit"))).isEqualByComparingTo(money(t.values().get("credit"))));
		// The investor's statement closes at what was credited.
		ReportResult statements = engine.all(catalog.find("investor-statements").orElseThrow(),
				new ReportQuery(FROM, TO, Map.of(), investorCode(investor), null, null, null, null, 0, 1), p -> true);
		assertThat(statements.rows()).hasSize(1);
		int closing = column(statements, "closing");
		int adjustments = column(statements, "adjustments");
		assertThat(money(statements.rows().getFirst().get(closing))).isEqualByComparingTo("777.77");
		assertThat(money(statements.rows().getFirst().get(adjustments))).isEqualByComparingTo("777.77");
		// Unbalanced or missing ledger records would be reconciliation exceptions; there are none of those kinds.
		ReportResult exceptions = engine.all(catalog.find("reconciliation").orElseThrow(),
				new ReportQuery(null, null, Map.of("kind", List.of("UNBALANCED_TRANSACTION", "MISSING_LEDGER_ENTRY",
						"EARNINGS_DISCREPANCY")), null, null, null, null, null, 0, 1), p -> true);
		assertThat(exceptions.rows()).isEmpty();
	}

	@Test
	void contactDetailsAreMaskedWithoutTheContactPermission() throws Exception {
		Account investor = investors.approvedInvestor();
		ReportDefinition master = catalog.find("investor-master").orElseThrow();
		ReportQuery query = new ReportQuery(null, null, Map.of(), investorCode(investor), null, null, null, null, 0, 5);
		ReportResult masked = engine.page(master, query, p -> p.equals("REPORT_VIEW") || p.equals("INVESTOR_ANALYTICS_VIEW"));
		int email = column(masked, "email");
		assertThat(masked.columns().get(email).masked()).isTrue();
		assertThat(masked.rows()).allSatisfy(row -> assertThat(row.get(email)).isEqualTo("Restricted"));
		ReportResult open = engine.page(master, query, p -> true);
		assertThat(open.rows().getFirst().get(email)).isEqualTo(investor.email());
		// A masked column cannot be sorted on (its order would reveal it).
		assertThat(engineRejects(master, new ReportQuery(null, null, Map.of(), null, null, null, "email", false, 0, 5),
				p -> p.equals("REPORT_VIEW") || p.equals("INVESTOR_ANALYTICS_VIEW"))).isTrue();
	}

	@Test
	void filtersAndSortsOutsideTheDefinitionAreRefused() throws Exception {
		Account admin = api.admin();
		get(admin, "/api/v1/admin/analytics/reports/payment-register?f.status=DROP%20TABLE").andExpect(status().isBadRequest());
		get(admin, "/api/v1/admin/analytics/reports/payment-register?sort=p.amount;--").andExpect(status().isBadRequest());
		get(admin, "/api/v1/admin/analytics/reports/payment-register?f.nope=1").andExpect(status().isBadRequest());
		get(admin, "/api/v1/admin/analytics/reports/payment-register?size=5000").andExpect(status().isBadRequest());
		get(admin, "/api/v1/admin/analytics/reports/payment-register?from=2026-01-10&to=2026-01-01").andExpect(status().isBadRequest());
		get(admin, "/api/v1/admin/analytics/reports/no-such-report").andExpect(status().isNotFound());
		get(admin, "/api/v1/admin/analytics/reports/no-such-report/export?format=exe").andExpect(status().isBadRequest());
	}

	@Test
	void accessFollowsTheExistingPermissions() throws Exception {
		// An investor has no reporting permission.
		Account investor = investors.approvedInvestor();
		get(investor, "/api/v1/admin/analytics/catalog").andExpect(status().isForbidden());
		get(investor, "/api/v1/admin/analytics/overview").andExpect(status().isForbidden());

		// A business analyst (REPORT_VIEW alone) sees aggregates but not registers, and cannot download.
		Account analyst = api.staff("BUSINESS_ANALYST");
		String body = get(analyst, "/api/v1/admin/analytics/catalog").andExpect(status().isOk()).andReturn().getResponse()
			.getContentAsString();
		List<String> keys = JsonPath.read(body, "$[*].reports[*].key");
		assertThat(keys).contains("trial-balance", "plan-performance")
			.doesNotContain("general-ledger", "investor-master", "audit-trail", "payment-register");
		get(analyst, "/api/v1/admin/analytics/reports/trial-balance").andExpect(status().isOk());
		get(analyst, "/api/v1/admin/analytics/reports/general-ledger").andExpect(status().isForbidden());
		get(analyst, "/api/v1/admin/analytics/reports/trial-balance/export?format=csv").andExpect(status().isForbidden());
		get(analyst, "/api/v1/admin/analytics/overview").andExpect(status().isOk());

		// A finance manager (REPORT_VIEW + FINANCE_VIEW + LEDGER_VIEW …) opens the general ledger.
		Account finance = api.staff("FINANCE_MANAGER");
		get(finance, "/api/v1/admin/analytics/reports/general-ledger").andExpect(status().isOk());
	}

	@Test
	void exportsAreRealFilesAuditedAndKeptInTheHistory() throws Exception {
		Account investor = investors.approvedInvestor();
		credit(investor, "1500");
		Account admin = api.admin();
		String query = "?from=" + FROM + "&to=" + TO + "&search=" + investorCode(investor);

		MvcResult csv = get(admin, "/api/v1/admin/analytics/reports/investor-statements/export" + query + "&format=csv")
			.andExpect(status().isOk()).andReturn();
		assertThat(csv.getResponse().getContentType()).startsWith("text/csv");
		assertThat(csv.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).contains("investor-statements-");
		String text = new String(csv.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
		assertThat(text).startsWith("﻿\"Investor ID\"").contains("\"1500.00\"").contains("\"INR\"");

		MvcResult xlsx = get(admin, "/api/v1/admin/analytics/reports/investor-statements/export" + query + "&format=xlsx")
			.andExpect(status().isOk()).andReturn();
		List<String> entries = new ArrayList<>();
		String data = null;
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(xlsx.getResponse().getContentAsByteArray()))) {
			for (ZipEntry e; (e = zip.getNextEntry()) != null;) {
				entries.add(e.getName());
				if (e.getName().equals("xl/worksheets/sheet2.xml")) {
					data = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
				}
			}
		}
		assertThat(entries).contains("[Content_Types].xml", "xl/workbook.xml", "xl/styles.xml", "xl/worksheets/sheet1.xml");
		assertThat(data).contains("<v>1500.00</v>").contains("SUBTOTAL(9,").contains("state=\"frozen\"").contains("<autoFilter");

		MvcResult pdf = get(admin, "/api/v1/admin/analytics/reports/general-ledger/export?format=pdf&from=" + FROM + "&to=" + TO)
			.andExpect(status().isOk()).andReturn();
		byte[] bytes = pdf.getResponse().getContentAsByteArray();
		assertThat(new String(bytes, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

		Integer executions = jdbc.queryForObject("""
				SELECT count(*) FROM report_executions WHERE executed_by = ?::uuid AND status = 'COMPLETED'
				  AND report_key IN ('investor-statements', 'general-ledger')""", Integer.class, admin.id());
		assertThat(executions).isGreaterThanOrEqualTo(3);
		Integer audited = jdbc.queryForObject("""
				SELECT count(*) FROM audit_logs WHERE action = 'REPORT_GENERATED' AND actor_user_id = ?::uuid
				  AND entity_id = 'investor-statements'""", Integer.class, admin.id());
		assertThat(audited).isGreaterThanOrEqualTo(2);
		get(admin, "/api/v1/admin/analytics/history?size=5").andExpect(status().isOk())
			.andExpect(jsonPath("$.executions[0].reportKey").exists());
	}

	@Test
	void theOverviewComparesWithThePreviousPeriod() throws Exception {
		Account admin = api.admin();
		get(admin, "/api/v1/admin/analytics/overview?from=2026-10-01&to=2026-10-31").andExpect(status().isOk())
			.andExpect(jsonPath("$.previousFrom").value("2026-08-31"))
			.andExpect(jsonPath("$.previousTo").value("2026-09-30"))
			.andExpect(jsonPath("$.period[0].key").value("investedCapital"))
			.andExpect(jsonPath("$.now[0].previous").doesNotExist())
			.andExpect(jsonPath("$.trends.length()").value(12));
	}

	// ------------------------------------------------------------------------- helpers

	private boolean engineRejects(ReportDefinition report, ReportQuery query, java.util.function.Predicate<String> has) {
		try {
			engine.page(report, query, has);
			return false;
		}
		catch (RuntimeException expected) {
			return true;
		}
	}

	private static int column(ReportResult result, String key) {
		for (int i = 0; i < result.columns().size(); i++) {
			if (result.columns().get(i).key().equals(key)) {
				return i;
			}
		}
		throw new AssertionError("No column " + key);
	}

	private static BigDecimal money(Object value) {
		return new BigDecimal(String.valueOf(value));
	}

	private String investorCode(Account investor) {
		Long number = jdbc.queryForObject("SELECT user_number FROM users WHERE id = ?::uuid", Long.class, investor.id());
		return "SL" + String.format("%05d", number);
	}

	private org.springframework.test.web.servlet.ResultActions get(Account who, String url) throws Exception {
		return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url)
			.header(HttpHeaders.AUTHORIZATION, who.bearer()));
	}

	private void credit(Account investor, String amount) throws Exception {
		mvc.perform(post("/api/v1/admin/ledger/adjustments").header(HttpHeaders.AUTHORIZATION, api.admin().bearer())
				.header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content("""
						{"userId":"%s","currency":"INR","direction":"CREDIT","amount":"%s","reason":"Analytics test","balance":"RENT_BANK"}
						""".formatted(investor.id(), amount)))
			.andExpect(status().isCreated());
	}

}
