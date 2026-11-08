package com.sealease.backend.helpdesk;

import com.jayway.jsonpath.JsonPath;
import com.sealease.backend.helpdesk.sla.SlaMonitor;
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
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Service management on top of the ticket basics: lifecycle transitions, references, SLA policies and the monitor,
 * escalation, automation, assignment, the knowledge base, customer feedback, the dashboard, and who may do what.
 */
@IntegrationTest
class SupportManagementIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private SlaMonitor monitor;

	private TestApi api;
	private InvestorFixtures investors;
	private Account agent;
	private Account admin;

	@BeforeEach
	void setUp() throws Exception {
		api = new TestApi(mvc, jdbc);
		investors = new InvestorFixtures(mvc, jdbc, api);
		agent = api.staff("SUPPORT");
		admin = api.admin();
	}

	// ------------------------------------------------------------------------------------------------ lifecycle

	@Test
	void ticketsMoveOnlyAlongTheAllowedLifecycle() throws Exception {
		Account investor = api.register();
		String id = open(investor, "Cannot see my returns", "EARNINGS", null);
		staffDetail(id).andExpect(jsonPath("$.ticket.status").value("NEW"))
			.andExpect(jsonPath("$.ticket.reference").value(org.hamcrest.Matchers.matchesPattern("SUP-\\d{4}-\\d{6}")))
			.andExpect(jsonPath("$.ticket.channel").value("PORTAL"));

		setStatus(id, "NEW").andExpect(status().isBadRequest());
		setStatus(id, "ESCALATED").andExpect(status().isBadRequest());
		setStatus(id, "IN_PROGRESS").andExpect(status().isOk()).andExpect(jsonPath("$.ticket.status").value("IN_PROGRESS"));
		setStatus(id, "WAITING_ON_INTERNAL").andExpect(status().isOk());
		escalate(agent, id, "{\"reason\":\"Needs finance\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.ticket.status").value("ESCALATED"))
			.andExpect(jsonPath("$.ticket.escalationLevel").value(1));
		escalate(agent, id, "{\"level\":1,\"reason\":\"Again\"}").andExpect(status().isConflict());
		setStatus(id, "RESOLVED").andExpect(status().isOk());
		setStatus(id, "IN_PROGRESS").andExpect(status().isConflict());
		setStatus(id, "CLOSED").andExpect(status().isOk());
		setStatus(id, "RESOLVED").andExpect(status().isUnprocessableContent());
		escalate(agent, id, "{\"reason\":\"Late\"}").andExpect(status().isUnprocessableContent());

		// The customer follows status changes, but never escalation mechanics, SLA events or internal notes.
		customerDetail(investor, id)
			.andExpect(jsonPath("$.events[*].type", not(hasItem("ESCALATED"))))
			.andExpect(jsonPath("$.ticket.escalationLevel").doesNotExist())
			.andExpect(jsonPath("$.ticket.slaState").doesNotExist());
		staffDetail(id).andExpect(jsonPath("$.events[*].type", hasItem("ESCALATED")));
		assertThat(api.auditCount("TICKET_ESCALATED", id)).isEqualTo(1);
	}

	@Test
	void referencesStayUniqueWhenTicketsAreOpenedAtOnce() throws Exception {
		List<Account> customers = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			customers.add(api.register());
		}
		ExecutorService pool = Executors.newFixedThreadPool(6);
		try {
			List<Future<String>> results = new ArrayList<>();
			for (Account c : customers) {
				results.add(pool.submit((Callable<String>) () -> open(c, "Concurrent", "TECHNICAL", null)));
			}
			Set<String> references = new HashSet<>();
			for (Future<String> f : results) {
				String id = f.get();
				references.add(jdbc.queryForObject("SELECT reference FROM support_tickets WHERE id = ?::uuid", String.class, id));
			}
			assertThat(references).hasSize(6).allMatch(r -> r.matches("SUP-\\d{4}-\\d{6}"));
		}
		finally {
			pool.shutdownNow();
		}
	}

	// --------------------------------------------------------------------------------------------------- SLA

	@Test
	void slaTargetsFollowThePolicyAndPauseWhileWaitingOnTheCustomer() throws Exception {
		Account investor = api.register();
		UUID failed = jdbc.queryForObject("SELECT id FROM support_categories WHERE code = 'PAYMENT_FAILED'", UUID.class);
		String id = open(investor, "Payment failed", "PAYMENT", failed);
		staffDetail(id)
			.andExpect(jsonPath("$.ticket.priority").value("HIGH"))
			.andExpect(jsonPath("$.ticket.subcategoryName").value("Payment failed"))
			.andExpect(jsonPath("$.ticket.resolutionDueAt").isNotEmpty());
		context(agent, id).andExpect(jsonPath("$.sla.policyName").value("High"))
			.andExpect(jsonPath("$.sla.businessHours").value(true))
			.andExpect(jsonPath("$.sla.firstResponse.dueAt").isNotEmpty());

		// A public reply hands the ticket to the customer: the resolution clock stops.
		staffReply(id, "Could you share the bank reference?").andExpect(status().isOk());
		context(agent, id).andExpect(jsonPath("$.sla.paused").value(true))
			.andExpect(jsonPath("$.sla.firstResponse.state").value("MET"));
		jdbc.update("UPDATE support_tickets SET sla_paused_at = sla_paused_at - interval '2 days' WHERE id = ?::uuid", id);
		String dueBefore = jdbc.queryForObject("SELECT resolution_due_at::text FROM support_tickets WHERE id = ?::uuid", String.class, id);

		// The customer answers: the clock restarts with the target moved out, and a next response is owed.
		customerReply(investor, id, "Here it is: UTR123").andExpect(status().isOk())
			.andExpect(jsonPath("$.ticket.status").value("OPEN"));
		context(agent, id).andExpect(jsonPath("$.sla.paused").value(false))
			.andExpect(jsonPath("$.sla.nextResponse.dueAt").isNotEmpty());
		String dueAfter = jdbc.queryForObject("SELECT resolution_due_at::text FROM support_tickets WHERE id = ?::uuid", String.class, id);
		Integer paused = jdbc.queryForObject("SELECT sla_paused_minutes FROM support_tickets WHERE id = ?::uuid", Integer.class, id);
		assertThat(paused).isPositive();
		assertThat(dueAfter).isNotEqualTo(dueBefore);
		staffDetail(id).andExpect(jsonPath("$.events[*].type", hasItem("SLA_PAUSED")))
			.andExpect(jsonPath("$.events[*].type", hasItem("SLA_RESUMED")));
		customerDetail(investor, id).andExpect(jsonPath("$.events[*].type", not(hasItem("SLA_PAUSED"))));
	}

	@Test
	void theMonitorRecordsBreachesOnceAndEscalatesThroughTheAutomationRule() throws Exception {
		Account investor = api.register();
		String id = open(investor, "No answer yet", "ACCOUNT", null);
		jdbc.update("UPDATE support_tickets SET first_response_due_at = now() - interval '1 minute' WHERE id = ?::uuid", id);

		monitor.run();
		staffDetail(id)
			.andExpect(jsonPath("$.ticket.status").value("ESCALATED"))
			.andExpect(jsonPath("$.ticket.escalationLevel").value(1))
			.andExpect(jsonPath("$.ticket.slaState").value("BREACHED"))
			.andExpect(jsonPath("$.events[?(@.type == 'SLA_BREACHED')].toValue").value(hasItem("FIRST_RESPONSE")));
		assertThat(api.auditCount("TICKET_SLA_BREACHED", id)).isEqualTo(1);
		int runs = runsFor(id);
		assertThat(runs).isPositive();

		// Running again changes nothing: one breach, one escalation, no new automation runs.
		monitor.run();
		assertThat(api.auditCount("TICKET_SLA_BREACHED", id)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM support_escalations WHERE ticket_id = ?::uuid", Integer.class, id)).isEqualTo(1);
		assertThat(runsFor(id)).isEqualTo(runs);
		mvc.perform(get("/api/v1/admin/support/tickets").param("view", "breached").param("size", "200")
				.header(HttpHeaders.AUTHORIZATION, agent.bearer()))
			.andExpect(jsonPath("$.content[*].id", hasItem(id)));
	}

	@Test
	void criticalTicketsNobodyActsOnAreEscalatedAutomatically() throws Exception {
		Account investor = api.register();
		String id = open(investor, "Account locked", "ACCOUNT", null);
		priority(id, "URGENT").andExpect(status().isOk());
		jdbc.update("UPDATE support_tickets SET created_at = now() - interval '20 minutes', acknowledged_at = NULL WHERE id = ?::uuid", id);

		monitor.run();
		assertThat(jdbc.queryForList("SELECT trigger FROM support_escalations WHERE ticket_id = ?::uuid", String.class, id))
			.contains("CRITICAL_UNACKNOWLEDGED");
		mvc.perform(get("/api/v1/admin/support/escalations").header(HttpHeaders.AUTHORIZATION, agent.bearer()))
			.andExpect(jsonPath("$[*].ticketId", hasItem(id)));
	}

	// ------------------------------------------------------------------------------------- automation & assignment

	@Test
	void automationRulesTakeOnlyStructuredConditionsAndRunOncePerTicket() throws Exception {
		configRule(admin, """
				{"name":"Bad","trigger":"TICKET_CREATED","conditions":{"sql":"DROP TABLE users"},"action":"NOTIFY_TEAM"}""")
			.andExpect(status().isBadRequest());
		configRule(admin, """
				{"name":"Bad","trigger":"TICKET_CREATED","conditions":{"priorities":["URGENT; DELETE"]},"action":"NOTIFY_TEAM"}""")
			.andExpect(status().isBadRequest());
		configRule(admin, """
				{"name":"Bad","trigger":"SLA_PERCENT","conditions":{},"action":"NOTIFY_ASSIGNEE"}""")
			.andExpect(status().isBadRequest());
		configRule(agent, """
				{"name":"Nope","trigger":"TICKET_CREATED","conditions":{},"action":"NOTIFY_TEAM"}""")
			.andExpect(status().isForbidden());

		// The seeded "unassigned for 30 minutes" rule assigns the least-loaded agent, once.
		Account investor = api.register();
		String id = open(investor, "Nobody picked this up", "REFERRAL", null);
		jdbc.update("UPDATE support_tickets SET created_at = now() - interval '45 minutes' WHERE id = ?::uuid", id);
		monitor.run();
		monitor.run();
		staffDetail(id).andExpect(jsonPath("$.ticket.assigneeId").isNotEmpty());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket_events WHERE ticket_id = ?::uuid AND type = 'ASSIGNED'",
				Integer.class, id)).isEqualTo(1);
		mvc.perform(get("/api/v1/admin/support/config/automation-runs").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
			.andExpect(jsonPath("$[?(@.ticketId == '" + id + "')].status").value(hasItem("SUCCEEDED")));
	}

	@Test
	void agentsAssignInBulkAndOpenTicketsForCustomers() throws Exception {
		Account investor = api.register();
		String first = open(investor, "One", "TECHNICAL", null);
		String second = open(investor, "Two", "TECHNICAL", null);
		mvc.perform(post("/api/v1/admin/support/tickets/bulk-assign").header(HttpHeaders.AUTHORIZATION, agent.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"ticketIds\":[\"" + first + "\",\"" + second + "\"],\"assigneeId\":\"" + agent.id() + "\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].assigned").value(true))
			.andExpect(jsonPath("$[1].assigneeId").value(agent.id().toString()));
		mvc.perform(post("/api/v1/admin/support/tickets/bulk-assign").header(HttpHeaders.AUTHORIZATION, agent.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"ticketIds\":[\"" + first + "\"],\"assigneeId\":\"" + investor.id() + "\"}"))
			.andExpect(jsonPath("$[0].assigned").value(false));
		mvc.perform(get("/api/v1/admin/support/workload").header(HttpHeaders.AUTHORIZATION, agent.bearer()))
			.andExpect(jsonPath("$[?(@.agentId == '" + agent.id() + "')].open").value(hasItem(org.hamcrest.Matchers.greaterThanOrEqualTo(2))));

		// A ticket opened for a customer after a phone call: public, OPEN, from the STAFF channel; the customer sees it.
		String created = JsonPath.read(mvc.perform(post("/api/v1/admin/support/tickets").header(HttpHeaders.AUTHORIZATION, agent.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"customerId":"%s","subject":"Called about KYC","category":"KYC","message":"As discussed on the phone."}"""
						.formatted(investor.id())))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.ticket.channel").value("STAFF"))
			.andExpect(jsonPath("$.ticket.status").value("OPEN"))
			.andReturn().getResponse().getContentAsString(), "$.ticket.id");
		mvc.perform(get("/api/v1/support/tickets").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.content[*].id", hasItem(created)));

		// Moving it to another category is kept in the staff history only.
		mvc.perform(post("/api/v1/admin/support/tickets/{id}/category", created).header(HttpHeaders.AUTHORIZATION, agent.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"ACCOUNT\"}"))
			.andExpect(jsonPath("$.ticket.category").value("ACCOUNT"));
		customerDetail(investor, created).andExpect(jsonPath("$.events[*].type", not(hasItem("CATEGORY_CHANGED"))));
		assertThat(api.auditCount("TICKET_CATEGORY_CHANGED", created)).isEqualTo(1);
	}

	// --------------------------------------------------------------------------------------- knowledge base

	@Test
	void knowledgeBaseArticlesReachOnlyTheirAudience() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String publicId = article("Resetting your password " + suffix, "PUBLIC", "account", "Use the forgotten password link zebrafish" + suffix);
		String customerId = article("Tracking your withdrawal " + suffix, "CUSTOMER", "withdrawals", "Open Withdrawals to follow it.");
		String internalId = article("Matching unmatched transfers " + suffix, "INTERNAL", "payments", "Check the bank statement first.");
		String draftId = article("Draft only " + suffix, "PUBLIC", "account", "Not ready.");
		for (String id : List.of(publicId, customerId, internalId)) {
			mvc.perform(post("/api/v1/admin/knowledge-base/articles/{id}/publish", id).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.summary.status").value("PUBLISHED"));
		}
		String publicSlug = slug(publicId);
		String customerSlug = slug(customerId);
		String internalSlug = slug(internalId);

		// Anyone: public articles only, found by full-text search; drafts never.
		mvc.perform(get("/api/v1/open/knowledge-base/articles").param("q", "zebrafish" + suffix))
			.andExpect(status().isOk()).andExpect(jsonPath("$.content[*].id", hasItem(publicId)));
		mvc.perform(get("/api/v1/open/knowledge-base/articles").param("q", suffix).param("size", "50"))
			.andExpect(jsonPath("$.content[*].id", not(hasItem(customerId))))
			.andExpect(jsonPath("$.content[*].id", not(hasItem(internalId))))
			.andExpect(jsonPath("$.content[*].id", not(hasItem(draftId))));
		mvc.perform(get("/api/v1/open/knowledge-base/articles/{slug}", customerSlug)).andExpect(status().isNotFound());

		// Customers: public and customer articles, never internal ones.
		Account investor = api.register();
		mvc.perform(get("/api/v1/support/knowledge-base/articles/{slug}", customerSlug).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk());
		mvc.perform(get("/api/v1/support/knowledge-base/articles/{slug}", internalSlug).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isNotFound());
		// Staff read internal articles; only editors change them.
		mvc.perform(get("/api/v1/admin/knowledge-base/read/{slug}", internalSlug).header(HttpHeaders.AUTHORIZATION, agent.bearer()))
			.andExpect(status().isOk());
		mvc.perform(post("/api/v1/admin/knowledge-base/articles/{id}/archive", internalId).header(HttpHeaders.AUTHORIZATION, agent.bearer()))
			.andExpect(status().isForbidden());

		// Edits are versioned; a stale edit is refused.
		String body = articleJson("Resetting your password " + suffix, "PUBLIC", "account", "Updated steps zebrafish" + suffix);
		mvc.perform(put("/api/v1/admin/knowledge-base/articles/{id}", publicId).param("expectedVersion", "1")
				.header(HttpHeaders.AUTHORIZATION, admin.bearer()).contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk()).andExpect(jsonPath("$.currentVersion").value(2));
		mvc.perform(put("/api/v1/admin/knowledge-base/articles/{id}", publicId).param("expectedVersion", "1")
				.header(HttpHeaders.AUTHORIZATION, admin.bearer()).contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isConflict());
		mvc.perform(get("/api/v1/admin/knowledge-base/articles/{id}/versions", publicId).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
			.andExpect(jsonPath("$.length()").value(2));

		// Readers say once whether it helped.
		String vote = "{\"helpful\":true}";
		mvc.perform(post("/api/v1/support/knowledge-base/articles/{slug}/feedback", publicSlug).header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(vote)).andExpect(status().isNoContent());
		mvc.perform(post("/api/v1/support/knowledge-base/articles/{slug}/feedback", publicSlug).header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(vote)).andExpect(status().isConflict());
		mvc.perform(get("/api/v1/open/knowledge-base/articles/{slug}", publicSlug))
			.andExpect(jsonPath("$.summary.helpful").value(1))
			.andExpect(jsonPath("$.body").value("Updated steps zebrafish" + suffix));
		assertThat(api.auditCount("KB_ARTICLE_PUBLISHED", publicId)).isEqualTo(1);
	}

	// --------------------------------------------------------------------------------- feedback & dashboard

	@Test
	void customersRateResolvedTicketsOnceAndTheDashboardCountsIt() throws Exception {
		Account investor = api.register();
		String id = open(investor, "Statement question", "EARNINGS", null);
		rate(investor, id, 5).andExpect(status().isUnprocessableContent());
		staffReply(id, "Here is your statement.").andExpect(status().isOk());
		setStatus(id, "RESOLVED").andExpect(status().isOk());
		customerDetail(investor, id).andExpect(jsonPath("$.ticket.feedbackRequested").value(true));

		rate(api.register(), id, 1).andExpect(status().isNotFound());
		rate(investor, id, 5).andExpect(status().isCreated());
		rate(investor, id, 4).andExpect(status().isConflict());
		mvc.perform(get("/api/v1/support/tickets/{id}/feedback", id).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.rating").value(5));
		assertThat(api.auditCount("TICKET_FEEDBACK_GIVEN", id)).isEqualTo(1);

		mvc.perform(get("/api/v1/admin/support/dashboard").header(HttpHeaders.AUTHORIZATION, agent.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.kpis[?(@.key == 'created')].value").value(hasItem(org.hamcrest.Matchers.greaterThanOrEqualTo(1.0))))
			.andExpect(jsonPath("$.kpis[?(@.key == 'resolved')].value").value(hasItem(org.hamcrest.Matchers.greaterThanOrEqualTo(1.0))))
			.andExpect(jsonPath("$.kpis[?(@.key == 'csat')].value").isNotEmpty())
			.andExpect(jsonPath("$.trend.length()").value(30));
		mvc.perform(get("/api/v1/admin/support/dashboard").param("from", "2026-05-01").param("to", "2026-04-01")
				.header(HttpHeaders.AUTHORIZATION, agent.bearer())).andExpect(status().isBadRequest());
		mvc.perform(get("/api/v1/admin/support/feedback").header(HttpHeaders.AUTHORIZATION, agent.bearer()))
			.andExpect(jsonPath("$[*].ticketId", hasItem(id)));
	}

	// ---------------------------------------------------------------------------------------- access control

	@Test
	void accessFollowsExistingPermissionsAndNewPermissionsStayUnassigned() throws Exception {
		Account investor = api.register();
		String id = open(investor, "Mine", "ACCOUNT", null);
		Account other = api.register();
		mvc.perform(get("/api/v1/support/tickets/{id}", id).header(HttpHeaders.AUTHORIZATION, other.bearer()))
			.andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/support/tickets/{id}/feedback", id).header(HttpHeaders.AUTHORIZATION, other.bearer()))
			.andExpect(status().isNotFound());
		for (String path : List.of("/api/v1/admin/support/dashboard", "/api/v1/admin/support/workload",
				"/api/v1/admin/support/config/sla-policies", "/api/v1/admin/knowledge-base/articles",
				"/api/v1/admin/support/tickets/" + id + "/context")) {
			mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, investor.bearer())).andExpect(status().isForbidden());
		}
		// Support agents read the configuration; only settings managers change it.
		mvc.perform(get("/api/v1/admin/support/config/sla-policies").header(HttpHeaders.AUTHORIZATION, agent.bearer()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(4)));
		String calendar = "{\"hours\":[{\"dayOfWeek\":1,\"opensAt\":\"09:00\",\"closesAt\":\"18:00\"}],\"holidays\":[]}";
		mvc.perform(put("/api/v1/admin/support/config/calendar").header(HttpHeaders.AUTHORIZATION, agent.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(calendar)).andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/admin/knowledge-base/articles").header(HttpHeaders.AUTHORIZATION, agent.bearer()))
			.andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/admin/support/config/calendar").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.hours.length()").value(5));

		// Only the support and BI job roles hold the support management permissions.
		assertThat(jdbc.queryForList("""
				SELECT DISTINCT r.name FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
				JOIN roles r ON r.id = rp.role_id
				WHERE p.code IN ('SUPPORT_CONFIG_MANAGE', 'KNOWLEDGE_BASE_MANAGE', 'SUPPORT_ANALYTICS_VIEW')""", String.class))
			.containsExactlyInAnyOrder("SUPPORT_MANAGER", "SENIOR_SUPPORT_EXECUTIVE", "BI_MANAGER");
	}

	// ------------------------------------------------------------------------------------------------ helpers

	private String open(Account investor, String subject, String category, UUID subcategoryId) throws Exception {
		var request = multipart("/api/v1/support/tickets").param("subject", subject).param("category", category)
			.param("message", "Please help.");
		if (subcategoryId != null) {
			request.param("subcategoryId", subcategoryId.toString());
		}
		return JsonPath.read(mvc.perform(request.header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.ticket.id");
	}

	private ResultActions staffDetail(String id) throws Exception {
		return mvc.perform(get("/api/v1/admin/support/tickets/{id}", id).header(HttpHeaders.AUTHORIZATION, agent.bearer()));
	}

	private ResultActions customerDetail(Account investor, String id) throws Exception {
		return mvc.perform(get("/api/v1/support/tickets/{id}", id).header(HttpHeaders.AUTHORIZATION, investor.bearer()));
	}

	private ResultActions context(Account staff, String id) throws Exception {
		return mvc.perform(get("/api/v1/admin/support/tickets/{id}/context", id).header(HttpHeaders.AUTHORIZATION, staff.bearer()));
	}

	private ResultActions setStatus(String id, String status) throws Exception {
		return mvc.perform(post("/api/v1/admin/support/tickets/{id}/status", id).header(HttpHeaders.AUTHORIZATION, agent.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + status + "\"}"));
	}

	private ResultActions priority(String id, String priority) throws Exception {
		return mvc.perform(post("/api/v1/admin/support/tickets/{id}/priority", id).header(HttpHeaders.AUTHORIZATION, agent.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"priority\":\"" + priority + "\"}"));
	}

	private ResultActions escalate(Account who, String id, String body) throws Exception {
		return mvc.perform(post("/api/v1/admin/support/tickets/{id}/escalate", id).header(HttpHeaders.AUTHORIZATION, who.bearer())
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions staffReply(String id, String body) throws Exception {
		return mvc.perform(multipart("/api/v1/admin/support/tickets/{id}/messages", id).param("body", body)
			.header(HttpHeaders.AUTHORIZATION, agent.bearer()));
	}

	private ResultActions customerReply(Account investor, String id, String body) throws Exception {
		return mvc.perform(multipart("/api/v1/support/tickets/{id}/messages", id).param("body", body)
			.header(HttpHeaders.AUTHORIZATION, investor.bearer()));
	}

	private ResultActions rate(Account investor, String id, int rating) throws Exception {
		return mvc.perform(post("/api/v1/support/tickets/{id}/feedback", id).header(HttpHeaders.AUTHORIZATION, investor.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"rating\":" + rating + ",\"comment\":\"Thanks\"}"));
	}

	private ResultActions configRule(Account who, String body) throws Exception {
		return mvc.perform(post("/api/v1/admin/support/config/automation-rules").header(HttpHeaders.AUTHORIZATION, who.bearer())
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private int runsFor(String ticketId) {
		return jdbc.queryForObject("SELECT count(*) FROM support_automation_runs WHERE ticket_id = ?::uuid", Integer.class, ticketId);
	}

	private static String articleJson(String title, String visibility, String category, String body) {
		return """
				{"title":"%s","summary":"%s","body":"%s","categoryCode":"%s","visibility":"%s","tags":["help"]}"""
			.formatted(title, title, body, category, visibility);
	}

	private String article(String title, String visibility, String category, String body) throws Exception {
		return JsonPath.read(mvc.perform(post("/api/v1/admin/knowledge-base/articles").header(HttpHeaders.AUTHORIZATION, admin.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(articleJson(title, visibility, category, body)))
			.andExpect(status().isCreated()).andExpect(jsonPath("$.summary.status").value("DRAFT"))
			.andReturn().getResponse().getContentAsString(), "$.summary.id");
	}

	private String slug(String id) {
		return jdbc.queryForObject("SELECT slug FROM kb_articles WHERE id = ?::uuid", String.class, id);
	}

}
