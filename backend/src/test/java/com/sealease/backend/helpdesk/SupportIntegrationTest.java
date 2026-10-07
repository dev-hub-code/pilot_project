package com.sealease.backend.helpdesk;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase 10: support tickets, internal notes, attachments, response targets and notifications. */
@IntegrationTest
class SupportIntegrationTest {

	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 9, 9, 9, 9 };

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	private TestApi api;
	private InvestorFixtures investors;
	private Account agent;

	@BeforeEach
	void setUp() throws Exception {
		api = new TestApi(mvc, jdbc);
		investors = new InvestorFixtures(mvc, jdbc, api);
		agent = api.staff("SUPPORT");
	}

	// --------------------------------------------------------------------------- lifecycle

	@Test
	void aTicketGoesBackAndForthUntilItIsClosed() throws Exception {
		Account investor = investors.approvedInvestor();
		UUID product = new OfferingFixtures(mvc, api.admin()).plan(1);
		String orderId = investors.placeOrder(investor, product, 1);

		String ticketId = JsonPath.read(open(investor, "Payment not showing", "ORDER", orderId, png("receipt.png"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.ticket.reference").value(matchesPattern("TK-\\d{6}")))
			.andExpect(jsonPath("$.ticket.status").value("OPEN"))
			.andExpect(jsonPath("$.ticket.priority").value("NORMAL"))
			.andExpect(jsonPath("$.ticket.relatedLabel").value(matchesPattern("ORD-\\d+")))
			.andExpect(jsonPath("$.ticket.assigneeId").doesNotExist())
			.andExpect(jsonPath("$.messages[0].authorName").value("You"))
			.andExpect(jsonPath("$.messages[0].attachments[0].filename").value("receipt.png"))
			.andExpect(jsonPath("$.messages[0].attachments[0].contentType").value("image/png"))
			.andReturn().getResponse().getContentAsString(), "$.ticket.id");

		// The agent answers publicly (customer notified) and leaves an internal note (customer never sees it).
		staffReply(ticketId, "Your transfer is being matched today.", false).andExpect(status().isOk())
			.andExpect(jsonPath("$.ticket.status").value("WAITING_ON_CUSTOMER"))
			.andExpect(jsonPath("$.ticket.firstRespondedAt").isNotEmpty());
		staffReply(ticketId, "Bank reference looks wrong, check with finance.", true)
			.andExpect(jsonPath("$.messages.length()").value(3))
			.andExpect(jsonPath("$.messages[2].internal").value(true));
		detail(investor, ticketId)
			.andExpect(jsonPath("$.messages.length()").value(2))
			.andExpect(jsonPath("$.messages[1].authorName").value("Test (SeaLease support)"))
			.andExpect(jsonPath("$.ticket.firstResponseDueAt").doesNotExist());
		unread(investor).andExpect(jsonPath("$.unread").value(1));

		mvc.perform(multipart("/api/v1/support/tickets/{id}/messages", ticketId).param("body", "Thanks, received.")
				.header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.ticket.status").value("OPEN"));
		setStatus(ticketId, "RESOLVED").andExpect(jsonPath("$.ticket.status").value("RESOLVED"));
		unread(investor).andExpect(jsonPath("$.unread").value(2));
		setStatus(ticketId, "OPEN").andExpect(status().isBadRequest());

		// Replying reopens a resolved ticket; closing is final.
		mvc.perform(multipart("/api/v1/support/tickets/{id}/messages", ticketId).param("body", "One more question")
				.header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.ticket.status").value("OPEN"))
			.andExpect(jsonPath("$.ticket.resolvedAt").doesNotExist());
		mvc.perform(post("/api/v1/support/tickets/{id}/close", ticketId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.ticket.status").value("CLOSED"));
		mvc.perform(multipart("/api/v1/support/tickets/{id}/messages", ticketId).param("body", "Hello?")
				.header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isUnprocessableContent());
		assertThat(api.auditCount("TICKET_OPENED", ticketId)).isEqualTo(1);
	}

	// -------------------------------------------------------------------------- visibility

	@Test
	void investorsSeeOnlyTheirOwnTicketsAndRecords() throws Exception {
		Account investor = investors.approvedInvestor();
		Account other = investors.approvedInvestor();
		UUID product = new OfferingFixtures(mvc, api.admin()).plan(1);
		String othersOrder = investors.placeOrder(other, product, 1);

		open(investor, "Not mine", "ORDER", othersOrder).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(containsString("not found among yours")));
		String ticketId = id(open(investor, "Login trouble", null, null).andExpect(status().isCreated()));

		detail(other, ticketId).andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/admin/support/tickets/{id}", ticketId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/support/tickets").header(HttpHeaders.AUTHORIZATION, other.bearer()))
			.andExpect(jsonPath("$.totalElements").value(0));

		assign(ticketId, investor.id()).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(containsString("cannot handle tickets")));
		Account colleague = api.staff("SUPPORT");
		assign(ticketId, colleague.id()).andExpect(jsonPath("$.ticket.assigneeName").value("Test Investor"));
		unread(colleague).andExpect(jsonPath("$.unread").value(1));
		String queue = mvc.perform(get("/api/v1/admin/support/tickets").param("assignee", "me")
				.header(HttpHeaders.AUTHORIZATION, colleague.bearer())).andReturn().getResponse().getContentAsString();
		List<String> mine = JsonPath.read(queue, "$.content[*].id");
		assertThat(mine).containsExactly(ticketId);
	}

	// ------------------------------------------------------------------------- attachments

	@Test
	void attachmentsAreTypeCheckedLimitedAndInternalOnesStayInternal() throws Exception {
		Account investor = investors.approvedInvestor();
		open(investor, "Statement", null, null, new MockMultipartFile("files", "notes.txt", "image/png", "plain text".getBytes()))
			.andExpect(status().isBadRequest());
		open(investor, "Too many", null, null, png("1.png"), png("2.png"), png("3.png"), png("4.png"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(containsString("at most 3")));

		String body = open(investor, "Statement", null, null, png("../../etc/statement.png"))
			.andReturn().getResponse().getContentAsString();
		String ticketId = JsonPath.read(body, "$.ticket.id");
		String own = JsonPath.read(body, "$.messages[0].attachments[0].id");
		assertThat((String) JsonPath.read(body, "$.messages[0].attachments[0].filename")).isEqualTo("statement.png");
		mvc.perform(get("/api/v1/support/tickets/{t}/attachments/{a}", ticketId, own).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk()).andExpect(content().bytes(PNG));

		String noteBody = mvc.perform(multipart("/api/v1/admin/support/tickets/{id}/messages", ticketId).file(png("internal.png"))
				.param("body", "Screenshot of the ledger").param("internal", "true")
				.header(HttpHeaders.AUTHORIZATION, agent.bearer()))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		String internal = JsonPath.read(noteBody, "$.messages[1].attachments[0].id");
		mvc.perform(get("/api/v1/support/tickets/{t}/attachments/{a}", ticketId, internal).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/admin/support/tickets/{t}/attachments/{a}", ticketId, own).header(HttpHeaders.AUTHORIZATION, agent.bearer()))
			.andExpect(status().isOk());
		assertThat(api.auditCount("TICKET_ATTACHMENT_VIEWED", ticketId)).isEqualTo(1);
	}

	// ------------------------------------------------------------------- response targets

	@Test
	void urgentTicketsWithoutAReplyBecomeOverdue() throws Exception {
		Account investor = investors.approvedInvestor();
		String ticketId = id(open(investor, "Cannot withdraw", null, null));
		jdbc.update("UPDATE support_tickets SET created_at = now() - interval '3 hours' WHERE id = ?::uuid", ticketId);

		mvc.perform(post("/api/v1/admin/support/tickets/{id}/priority", ticketId).header(HttpHeaders.AUTHORIZATION, agent.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"priority\":\"URGENT\"}"))
			.andExpect(jsonPath("$.ticket.priority").value("URGENT"))
			.andExpect(jsonPath("$.ticket.overdue").value(true));
		List<String> overdue = JsonPath.read(mvc.perform(get("/api/v1/admin/support/tickets").param("overdue", "true")
				.header(HttpHeaders.AUTHORIZATION, agent.bearer())).andReturn().getResponse().getContentAsString(), "$.content[*].id");
		assertThat(overdue).contains(ticketId);

		staffReply(ticketId, "Looking into it now.", false).andExpect(jsonPath("$.ticket.overdue").value(false));
	}

	// ----------------------------------------------------------------------- notifications

	@Test
	void notificationsCanBeReadOneByOneOrAllAtOnce() throws Exception {
		Account investor = investors.approvedInvestor();
		String ticketId = id(open(investor, "Question", null, null));
		staffReply(ticketId, "Answer one", false);
		staffReply(ticketId, "Answer two", false);
		String page = mvc.perform(get("/api/v1/notifications").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.content[0].link").value("/support/" + ticketId))
			.andExpect(jsonPath("$.content[0].read").value(false))
			.andReturn().getResponse().getContentAsString();
		String first = JsonPath.read(page, "$.content[0].id");

		Account stranger = investors.approvedInvestor();
		mvc.perform(post("/api/v1/notifications/{id}/read", first).header(HttpHeaders.AUTHORIZATION, stranger.bearer()))
			.andExpect(status().isNotFound());
		mvc.perform(post("/api/v1/notifications/{id}/read", first).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.read").value(true));
		unread(investor).andExpect(jsonPath("$.unread").value(1));
		mvc.perform(post("/api/v1/notifications/read-all").header(HttpHeaders.AUTHORIZATION, investor.bearer()));
		unread(investor).andExpect(jsonPath("$.unread").value(0));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE topic = 'notification.created' AND aggregate_id = ?",
				Integer.class, investor.id().toString())).isEqualTo(2);
	}

	// ----------------------------------------------------------------------------- helpers

	private static MockMultipartFile png(String name) {
		return new MockMultipartFile("files", name, "image/png", PNG);
	}

	private ResultActions open(Account investor, String subject, String relatedType, String relatedId,
			MockMultipartFile... files) throws Exception {
		MockMultipartHttpServletRequestBuilder request = multipart("/api/v1/support/tickets");
		for (MockMultipartFile file : files) {
			request.file(file);
		}
		request.param("subject", subject).param("category", "PAYMENT").param("message", "Please help with this.");
		if (relatedType != null) {
			request.param("relatedType", relatedType).param("relatedId", relatedId);
		}
		return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, investor.bearer()));
	}

	private ResultActions detail(Account investor, String ticketId) throws Exception {
		return mvc.perform(get("/api/v1/support/tickets/{id}", ticketId).header(HttpHeaders.AUTHORIZATION, investor.bearer()));
	}

	private ResultActions staffReply(String ticketId, String body, boolean internal) throws Exception {
		return mvc.perform(multipart("/api/v1/admin/support/tickets/{id}/messages", ticketId).param("body", body)
			.param("internal", String.valueOf(internal)).header(HttpHeaders.AUTHORIZATION, agent.bearer()));
	}

	private ResultActions setStatus(String ticketId, String status) throws Exception {
		return mvc.perform(post("/api/v1/admin/support/tickets/{id}/status", ticketId).header(HttpHeaders.AUTHORIZATION, agent.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + status + "\"}"));
	}

	private ResultActions assign(String ticketId, UUID assigneeId) throws Exception {
		return mvc.perform(post("/api/v1/admin/support/tickets/{id}/assign", ticketId).header(HttpHeaders.AUTHORIZATION, agent.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"assigneeId\":\"" + assigneeId + "\"}"));
	}

	private ResultActions unread(Account account) throws Exception {
		return mvc.perform(get("/api/v1/notifications/unread-count").header(HttpHeaders.AUTHORIZATION, account.bearer()));
	}

	private static String id(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.ticket.id");
	}

}
