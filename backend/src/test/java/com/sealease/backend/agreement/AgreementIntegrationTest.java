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
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Admins write the lease agreement's wording; assigning a container issues the agreement in that wording, and the
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
	void assigningAContainerIssuesTheAgreementInTheWordingInForce() throws Exception {
		// Only those who may manage the wording see it.
		Account finance = api.staff("FINANCE");
		mvc.perform(get("/api/v1/admin/agreement-template").header(HttpHeaders.AUTHORIZATION, finance.bearer()))
			.andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/admin/agreement-template").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.current.version").isNumber())
			.andExpect(jsonPath("$.placeholders[?(@.key == 'containerNumber')]").exists());

		// A typo in a placeholder is refused, naming it; a preview fills in example values.
		save("Lease {{agreementNumber}}", "Hello {{investorNmae}}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(containsString("{{investorNmae}}")));
		mvc.perform(post("/api/v1/admin/agreement-template/preview").header(HttpHeaders.AUTHORIZATION, admin.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"Lease\",\"body\":\"Container {{containerNumber}}\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body").value("Container SLSU1234565"));

		String marker = "Clause-" + UUID.randomUUID();
		save("Lease {{agreementNumber}}", marker + " for {{investorEmail}}: container {{containerNumber}}, "
				+ "{{tenureMonths}} months at {{monthlyRent}}.\n\n# Signed\nBy SeaLease")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version").isNumber());
		mvc.perform(get("/api/v1/admin/agreement-template/sample").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_PDF));

		Account investor = investors.approvedInvestor();
		UUID plan = new OfferingFixtures(mvc, admin).plan();
		String orderId = investors.paidOrder(investor, plan, 1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM lease_agreements WHERE order_id = ?::uuid", Long.class, orderId))
			.as("no agreement before a container is assigned").isZero();

		investors.assignContainersOnly(orderId);
		String holdingId = jdbc.queryForObject("SELECT id::text FROM holdings WHERE order_id = ?::uuid", String.class, orderId);

		// Assigned, but nothing is earned until the investor accepts: no lease dates, no rent schedule.
		assertThat(jdbc.queryForMap("SELECT status, lease_starts_on FROM holdings WHERE id = ?::uuid", holdingId))
			.containsEntry("status", "AWAITING_ACCEPTANCE").containsEntry("lease_starts_on", null);
		assertThat(accruals(holdingId)).isZero();
		mvc.perform(get("/api/v1/agreements/{id}", holdingId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.agreement.acceptanceRequired").value(true))
			.andExpect(jsonPath("$.agreement.acceptedAt").doesNotExist())
			.andExpect(jsonPath("$.body").value(startsWith(marker)));
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
		mvc.perform(post("/api/v1/agreements/{id}/accept", holdingId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isConflict());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM lease_agreement_acceptances a JOIN lease_agreements l "
				+ "ON l.id = a.agreement_id WHERE l.holding_id = ?::uuid", Long.class, holdingId)).isOne();
		String container = jdbc.queryForObject("""
				SELECT c.container_number FROM holdings h JOIN containers c ON c.id = h.container_id WHERE h.id = ?::uuid""",
				String.class, holdingId);
		var stored = jdbc.queryForMap("SELECT agreement_number, title, body FROM lease_agreements WHERE holding_id = ?::uuid",
				holdingId);
		assertThat((String) stored.get("agreement_number")).matches("AGR-\\d{4}-\\d{6}");
		assertThat(stored.get("title")).isEqualTo("Lease " + stored.get("agreement_number"));
		assertThat((String) stored.get("body")).startsWith(marker + " for " + investor.email() + ": container " + container
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

		// Changing the wording later leaves the issued agreement as it was; issuing again changes nothing.
		save("Lease v-next", "New wording {{containerNumber}}").andExpect(status().isOk());
		mvc.perform(post("/api/v1/admin/container-assignments/orders/{id}/agreements", orderId)
				.header(HttpHeaders.AUTHORIZATION, admin.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].agreementNumber").value(stored.get("agreement_number")));
		assertThat(jdbc.queryForObject("SELECT body FROM lease_agreements WHERE holding_id = ?::uuid", String.class, holdingId))
			.isEqualTo(stored.get("body"));
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
