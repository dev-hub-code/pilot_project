package com.sealease.backend.agreement;

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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Admins write the lease agreement's wording; a paid investment is issued the agreement in that wording, and the
 * investor's acceptance starts the lease and the rent.
 */
@IntegrationTest
class AgreementIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	private TestApi api;
	private InvestorFixtures investors;
	private Account admin;

	@BeforeEach
	void setUp() throws Exception {
		api = new TestApi(mvc, jdbc);
		investors = new InvestorFixtures(mvc, jdbc, api);
		admin = api.admin();
	}

	@Test
	void aPaidInvestmentIsIssuedTheAgreementInTheWordingInForce() throws Exception {
		// Only those who may manage the wording see it.
		Account finance = api.staff("FINANCE");
		mvc.perform(get("/api/v1/admin/agreement-template").header(HttpHeaders.AUTHORIZATION, finance.bearer()))
			.andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/admin/agreement-template").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.current.version").isNumber())
			.andExpect(jsonPath("$.placeholders[?(@.key == 'planCode')]").exists());

		// A typo in a placeholder is refused, naming it; a preview fills in example values.
		save("Lease {{agreementNumber}}", "Hello {{investorNmae}}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(containsString("{{investorNmae}}")));
		mvc.perform(post("/api/v1/admin/agreement-template/preview").header(HttpHeaders.AUTHORIZATION, admin.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"Lease\",\"body\":\"Plan {{planCode}}\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body").value("Plan LST-10001"));

		String marker = "Clause-" + UUID.randomUUID();
		save("Lease {{agreementNumber}}", marker + " for {{investorEmail}}: plan {{planCode}}, "
				+ "{{tenureMonths}} months at {{monthlyReturns}}.\n\n# Signed\nBy SeaLease\n\n"
				+ "Wording v{{agreementVersion}}, {{currentYear}}. Acceptance: {{acceptanceStatus}} / {{acceptedAt}} / "
				+ "{{acceptanceReference}}.")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version").isNumber());
		int version = jdbc.queryForObject("SELECT max(version) FROM agreement_templates", Integer.class);
		mvc.perform(get("/api/v1/admin/agreement-template/sample").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_PDF));

		Account investor = investors.approvedInvestor();
		UUID plan = new OfferingFixtures(mvc, admin).plan();
		String orderId = investors.paidOrder(investor, plan, 1);
		String holdingId = jdbc.queryForObject("SELECT id::text FROM holdings WHERE order_id = ?::uuid", String.class, orderId);

		// Assigned, but nothing is earned until the investor accepts: no lease dates, no rent schedule.
		assertThat(jdbc.queryForMap("SELECT status, lease_starts_on FROM holdings WHERE id = ?::uuid", holdingId))
			.containsEntry("status", "AWAITING_ACCEPTANCE").containsEntry("lease_starts_on", null);
		assertThat(accruals(holdingId)).isZero();
		mvc.perform(get("/api/v1/agreements/{id}", holdingId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.agreement.acceptanceRequired").value(true))
			.andExpect(jsonPath("$.agreement.acceptedAt").doesNotExist())
			.andExpect(jsonPath("$.body").value(startsWith(marker)))
			.andExpect(jsonPath("$.body").value(containsString("Wording v" + version + ", " + java.time.Year.now().getValue()
					+ ". Acceptance: Awaiting acceptance / Not yet accepted / Pending.")));
		Account stranger = investors.approvedInvestor();
		mvc.perform(post("/api/v1/agreements/{id}/accept", holdingId).header(HttpHeaders.AUTHORIZATION, stranger.bearer()))
			.andExpect(status().isNotFound());

		// Accepting starts the lease and the rent; accepting again is refused.
		mvc.perform(post("/api/v1/agreements/{id}/accept", holdingId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.acceptedAt").isNotEmpty());
		assertThat(jdbc.queryForObject("SELECT status FROM holdings WHERE id = ?::uuid", String.class, holdingId))
			.isEqualTo("ACTIVE");
		assertThat(accruals(holdingId)).isOne();
		String number = jdbc.queryForObject("SELECT agreement_number FROM lease_agreements WHERE holding_id = ?::uuid",
				String.class, holdingId);
		mvc.perform(get("/api/v1/agreements/{id}", holdingId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.body").value(matchesPattern("(?s).*Acceptance: Accepted / \\d{1,2} \\w+ \\d{4}, \\d{2}:\\d{2} \\S+ / "
					+ number.replace("AGR-", "ACC-") + "\\..*")));
		mvc.perform(post("/api/v1/agreements/{id}/accept", holdingId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isConflict());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM lease_agreement_acceptances a JOIN lease_agreements l "
				+ "ON l.id = a.agreement_id WHERE l.holding_id = ?::uuid", Long.class, holdingId)).isOne();
		String planCode = jdbc.queryForObject("SELECT code FROM investment_products WHERE id = ?", String.class, plan);
		var stored = jdbc.queryForMap("SELECT agreement_number, title, body FROM lease_agreements WHERE holding_id = ?::uuid",
				holdingId);
		assertThat((String) stored.get("agreement_number")).matches("AGR-\\d{4}-\\d{6}");
		assertThat(stored.get("title")).isEqualTo("Lease " + stored.get("agreement_number"));
		assertThat((String) stored.get("body")).startsWith(marker + " for " + investor.email() + ": plan " + planCode
				+ ", 12 months at ₹1,000.00.");

		// The investor lists and downloads their own; others cannot.
		mvc.perform(get("/api/v1/agreements").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].holdingId").value(holdingId))
			.andExpect(jsonPath("$[0].agreementNumber").value(stored.get("agreement_number")));
		byte[] pdf = mvc.perform(get("/api/v1/agreements/{id}/pdf", holdingId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_PDF))
			.andReturn().getResponse().getContentAsByteArray();
		assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
		mvc.perform(get("/api/v1/agreements/{id}/pdf", holdingId).header(HttpHeaders.AUTHORIZATION, stranger.bearer()))
			.andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/admin/agreements").param("orderId", orderId).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].agreementNumber").value(startsWith("AGR-")));

		// Changing the wording later leaves the issued agreement as it was.
		save("Lease v-next", "New wording {{planCode}}").andExpect(status().isOk());
		assertThat(jdbc.queryForObject("SELECT body FROM lease_agreements WHERE holding_id = ?::uuid", String.class, holdingId))
			.isEqualTo(stored.get("body"));
	}

	@Test
	void anAgreementAwaitingAcceptanceFollowsTheWordingInForceAndTheAcceptedWordingStays() throws Exception {
		save("Lease {{agreementNumber}}", "Old wording for {{planCode}}.").andExpect(status().isOk());
		Account investor = investors.approvedInvestor();
		UUID plan = new OfferingFixtures(mvc, admin).plan();
		String orderId = investors.paidOrder(investor, plan, 1);
		String holdingId = jdbc.queryForObject("SELECT id::text FROM holdings WHERE order_id = ?::uuid", String.class, orderId);
		String number = jdbc.queryForObject("SELECT agreement_number FROM lease_agreements WHERE holding_id = ?::uuid",
				String.class, holdingId);
		String planCode = jdbc.queryForObject("SELECT code FROM investment_products WHERE id = ?", String.class, plan);

		// New wording reaches the agreement still waiting, under its own number.
		save("Agreement {{agreementNumber}}", "New wording v{{agreementVersion}} for {{planCode}}, from {{startDate}}.")
			.andExpect(status().isOk());
		int version = jdbc.queryForObject("SELECT max(version) FROM agreement_templates", Integer.class);
		mvc.perform(get("/api/v1/agreements/{id}", holdingId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.agreement.title").value("Agreement " + number))
			.andExpect(jsonPath("$.body").value("New wording v" + version + " for " + planCode
					+ ", from the day this Agreement is accepted."));

		// What is accepted is that wording, and later changes leave it as accepted.
		mvc.perform(post("/api/v1/agreements/{id}/accept", holdingId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk());
		save("Lease later", "Later wording {{planCode}}").andExpect(status().isOk());
		mvc.perform(get("/api/v1/agreements/{id}", holdingId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.agreement.title").value("Agreement " + number))
			.andExpect(jsonPath("$.body").value(startsWith("New wording v" + version + " for " + planCode + ", from ")))
			.andExpect(jsonPath("$.body").value(org.hamcrest.Matchers.not(containsString("accepted"))));
		mvc.perform(get("/api/v1/agreements/{id}/pdf", holdingId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk());
	}

	private long accruals(String holdingId) {
		return jdbc.queryForObject("SELECT count(*) FROM rent_accruals WHERE holding_id = ?::uuid", Long.class, holdingId);
	}

	private ResultActions save(String title, String body) throws Exception {
		return mvc.perform(put("/api/v1/admin/agreement-template").header(HttpHeaders.AUTHORIZATION, admin.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"title\":\"%s\",\"body\":\"%s\"}".formatted(title, body.replace("\n", "\\n"))));
	}

}
