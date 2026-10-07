package com.sealease.backend.crm;

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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase 9: leads from the website and staff, the pipeline, visibility and conversion to investors. */
@IntegrationTest
class LeadIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	private TestApi api;
	private Account manager;

	@BeforeEach
	void setUp() throws Exception {
		api = new TestApi(mvc, jdbc);
		manager = api.admin();
	}

	// ------------------------------------------------------------------------ website form

	@Test
	void theWebsiteFormCreatesOneOpenLeadPerEmailAndRevealsNothing() throws Exception {
		String email = email();
		submit(email, "I'd like a 40ft container", null).andExpect(status().isAccepted());
		submit(email.toUpperCase(), "Following up", null).andExpect(status().isAccepted());
		submit(email(), "Bot text", "http://spam.example").andExpect(status().isAccepted());
		mvc.perform(post("/api/v1/public/leads").contentType(MediaType.APPLICATION_JSON).content("""
				{"firstName":"Ada","email":"%s","interest":"RETAIL","consent":false}
				""".formatted(email()))).andExpect(status().isBadRequest());

		assertThat(jdbc.queryForObject("SELECT count(*) FROM leads WHERE email = ?", Integer.class, email)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM leads WHERE message = 'Bot text'", Integer.class)).isZero();
		String leadId = leadIdFor(email);
		lead(manager, leadId)
			.andExpect(jsonPath("$.lead.reference").value(matchesPattern("LD-\\d{6}")))
			.andExpect(jsonPath("$.lead.source").value("WEBSITE"))
			.andExpect(jsonPath("$.lead.stage").value("NEW"))
			.andExpect(jsonPath("$.lead.ownerId").doesNotExist())
			.andExpect(jsonPath("$.lead.message").value("I'd like a 40ft container"))
			.andExpect(jsonPath("$.activities[0].body").value(containsString("again: Following up")));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE topic = 'lead.created' AND aggregate_id = ?",
				Integer.class, leadId)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT payload::text FROM outbox_events WHERE topic = 'lead.created' AND aggregate_id = ?",
				String.class, leadId)).doesNotContain(email);
	}

	// -------------------------------------------------------------------------- visibility

	@Test
	void repsWorkTheirOwnAndUnassignedLeadsManagersAssign() throws Exception {
		Account rep = api.staff("SALES");
		Account otherRep = api.staff("SALES");
		String email = email();
		submit(email, null, null).andExpect(status().isAccepted());
		String leadId = leadIdFor(email);

		lead(rep, leadId).andExpect(status().isOk()).andExpect(jsonPath("$.editable").value(false));
		stage(rep, leadId, "CONTACTED", null).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value(containsString("Claim this lead")));
		action(rep, leadId, "claim").andExpect(status().isOk())
			.andExpect(jsonPath("$.lead.ownerId").value(rep.id().toString()))
			.andExpect(jsonPath("$.editable").value(true));
		lead(otherRep, leadId).andExpect(status().isNotFound());
		action(otherRep, leadId, "claim").andExpect(status().isNotFound());
		mvc.perform(post("/api/v1/admin/leads/{id}/assign", leadId).header(HttpHeaders.AUTHORIZATION, rep.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"ownerId\":\"" + otherRep.id() + "\"}"))
			.andExpect(status().isForbidden());

		assign(leadId, otherRep.id()).andExpect(jsonPath("$.lead.ownerId").value(otherRep.id().toString()));
		lead(rep, leadId).andExpect(status().isNotFound());
		lead(otherRep, leadId).andExpect(jsonPath("$.editable").value(true));
		assign(leadId, investorAccount().id()).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(containsString("cannot work leads")));
		assertThat(api.auditCount("LEAD_ASSIGNED", leadId)).isEqualTo(1);

		String mine = mvc.perform(get("/api/v1/admin/leads").param("owner", "me")
				.header(HttpHeaders.AUTHORIZATION, otherRep.bearer()))
			.andReturn().getResponse().getContentAsString();
		List<String> ids = JsonPath.read(mine, "$.content[*].id");
		assertThat(ids).contains(leadId);
		String assignees = mvc.perform(get("/api/v1/admin/leads/assignees").header(HttpHeaders.AUTHORIZATION, manager.bearer()))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		List<String> assigneeIds = JsonPath.read(assignees, "$[*].userId");
		assertThat(assigneeIds).contains(rep.id().toString(), otherRep.id().toString());
	}

	@Test
	void staffLeadsBelongToTheirCreatorAndEmailsAreNotDuplicated() throws Exception {
		Account rep = api.staff("SALES");
		String email = email();
		String leadId = JsonPath.read(createLead(rep, email).andExpect(status().isCreated())
			.andExpect(jsonPath("$.lead.source").value("STAFF"))
			.andExpect(jsonPath("$.lead.ownerId").value(rep.id().toString()))
			.andExpect(jsonPath("$.lead.estimate.amount").value("25000.00"))
			.andReturn().getResponse().getContentAsString(), "$.lead.id");
		createLead(rep, email).andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value(containsString("already exists")));
		mvc.perform(post("/api/v1/admin/leads").header(HttpHeaders.AUTHORIZATION, rep.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"firstName\":\"No\",\"interest\":\"RETAIL\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(containsString("email address or a phone number")));

		// The pipeline: lost needs a reason, a lost lead can be reopened, won is final.
		stage(rep, leadId, "CONTACTED", null).andExpect(jsonPath("$.lead.stage").value("CONTACTED"));
		stage(rep, leadId, "LOST", null).andExpect(status().isBadRequest());
		stage(rep, leadId, "LOST", "Chose a competitor").andExpect(jsonPath("$.lead.lostReason").value("Chose a competitor"))
			.andExpect(jsonPath("$.lead.closedAt").isNotEmpty());
		stage(rep, leadId, "QUALIFIED", null).andExpect(jsonPath("$.lead.lostReason").doesNotExist());
		mvc.perform(post("/api/v1/admin/leads/{id}/activities", leadId).header(HttpHeaders.AUTHORIZATION, rep.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"CALL\",\"body\":\"Discussed HNI options\"}"))
			.andExpect(jsonPath("$.activities[0].type").value("CALL"))
			.andExpect(jsonPath("$.activities[0].actorName").value("Test Investor"));
		mvc.perform(post("/api/v1/admin/leads/{id}/activities", leadId).header(HttpHeaders.AUTHORIZATION, rep.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"SYSTEM\",\"body\":\"Spoof\"}"))
			.andExpect(status().isBadRequest());
		stage(rep, leadId, "WON", null).andExpect(status().isOk());
		stage(rep, leadId, "PROPOSAL", null).andExpect(status().isUnprocessableContent());

		String pipeline = mvc.perform(get("/api/v1/admin/leads/pipeline").param("owner", "me")
				.header(HttpHeaders.AUTHORIZATION, rep.bearer())).andReturn().getResponse().getContentAsString();
		List<Integer> won = JsonPath.read(pipeline, "$[?(@.stage == 'WON')].leads");
		assertThat(won).containsExactly(1);
	}

	// -------------------------------------------------------------------------- conversion

	@Test
	void aLeadIsLinkedWhenItsEmailRegistersAndWonByTheFirstInvestment() throws Exception {
		String email = email();
		submit(email, "Interested", null).andExpect(status().isAccepted());
		String leadId = leadIdFor(email);

		MvcResult registered = api.registerResult(email, null);
		assertThat(registered.getResponse().getStatus()).isEqualTo(201);
		Account investor = new Account(api.userIdByEmail(email), email,
				JsonPath.read(registered.getResponse().getContentAsString(), "$.accessToken"));
		lead(manager, leadId).andExpect(jsonPath("$.lead.userId").value(investor.id().toString()))
			.andExpect(jsonPath("$.activities[0].body").value("Registered an investor account"));

		InvestorFixtures investors = new InvestorFixtures(mvc, jdbc, api);
		UUID product = new OfferingFixtures(mvc, manager).retail("50000", "1000", "500");
		investors.invest(investors.approve(investor), product, "2500");
		lead(manager, leadId).andExpect(jsonPath("$.lead.stage").value("WON"))
			.andExpect(jsonPath("$.lead.won.amount").value("2500.00"))
			.andExpect(jsonPath("$.activities[0].body").value(containsString("first investment confirmed")));
		assertThat(api.auditCount("LEAD_WON", leadId)).isEqualTo(1);

		// A lead entered for someone who already has an account is linked at once.
		Account existing = investorAccount();
		mvc.perform(post("/api/v1/admin/leads").header(HttpHeaders.AUTHORIZATION, manager.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"firstName":"Known","email":"%s","interest":"HNI"}
						""".formatted(existing.email())))
			.andExpect(jsonPath("$.lead.userId").value(existing.id().toString()));
	}

	// ----------------------------------------------------------------------------- helpers

	private static String email() {
		return "lead-" + UUID.randomUUID() + "@example.com";
	}

	private Account investorAccount() throws Exception {
		return api.register();
	}

	private ResultActions submit(String email, String message, String honeypot) throws Exception {
		return mvc.perform(post("/api/v1/public/leads").contentType(MediaType.APPLICATION_JSON).content("""
				{"firstName":"Ada","lastName":"Lovelace","email":"%s","interest":"HNI","consent":true%s%s}
				""".formatted(email, message == null ? "" : ",\"message\":\"" + message + "\"",
				honeypot == null ? "" : ",\"website\":\"" + honeypot + "\"")));
	}

	private String leadIdFor(String email) {
		return jdbc.queryForObject("SELECT id::text FROM leads WHERE email = ?", String.class, email.toLowerCase());
	}

	private ResultActions lead(Account viewer, String leadId) throws Exception {
		return mvc.perform(get("/api/v1/admin/leads/{id}", leadId).header(HttpHeaders.AUTHORIZATION, viewer.bearer()));
	}

	private ResultActions createLead(Account actor, String email) throws Exception {
		return mvc.perform(post("/api/v1/admin/leads").header(HttpHeaders.AUTHORIZATION, actor.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("""
					{"firstName":"Grace","lastName":"Hopper","email":"%s","phone":"+44 20 7946 0000","country":"GB",
					 "interest":"HNI","estimatedAmount":"25000","estimatedCurrency":"USD"}
					""".formatted(email)));
	}

	private ResultActions stage(Account actor, String leadId, String stage, String reason) throws Exception {
		return mvc.perform(post("/api/v1/admin/leads/{id}/stage", leadId).header(HttpHeaders.AUTHORIZATION, actor.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"" + stage + "\""
					+ (reason == null ? "" : ",\"reason\":\"" + reason + "\"") + "}"));
	}

	private ResultActions action(Account actor, String leadId, String action) throws Exception {
		return mvc.perform(post("/api/v1/admin/leads/{id}/" + action, leadId).header(HttpHeaders.AUTHORIZATION, actor.bearer()));
	}

	private ResultActions assign(String leadId, UUID ownerId) throws Exception {
		return mvc.perform(post("/api/v1/admin/leads/{id}/assign", leadId).header(HttpHeaders.AUTHORIZATION, manager.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"ownerId\":\"" + ownerId + "\"}"));
	}

}
