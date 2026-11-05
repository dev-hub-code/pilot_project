package com.sealease.backend.role;

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

import java.util.Map;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The finance team: users (view only), payments to confirm, finance tasks to pay, bank accounts to verify, company bank
 * accounts (view only) and the tickets support hands to it. Each is a task a member assigns to themselves first.
 */
@IntegrationTest
class FinanceScopeIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	private TestApi api;
	private Account finance;
	private Account colleague;

	@BeforeEach
	void setUp() throws Exception {
		api = new TestApi(mvc, jdbc);
		finance = api.staff("FINANCE");
		colleague = api.staff("FINANCE");
	}

	@Test
	void financeSeesUsersAndCompanyAccountsButNotMoneyReportsOrPlans() throws Exception {
		api.companyBankAccount();
		fetch(finance, "/api/v1/admin/users").andExpect(status().isOk());
		fetch(finance, "/api/v1/admin/company-bank-accounts").andExpect(status().isOk());
		mvc.perform(post("/api/v1/admin/company-bank-accounts").header(HttpHeaders.AUTHORIZATION, finance.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"accountName":"SeaLease Collections","bankName":"Test Bank","branch":"Fort, Mumbai",
						 "accountNumber":"1234567890","ifscCode":"HDFC0001234"}"""))
			.andExpect(status().isForbidden());
		for (String path : new String[] { "/api/v1/admin/withdrawals", "/api/v1/admin/withdrawal-batches", "/api/v1/admin/withdrawal-runs",
				"/api/v1/admin/ledger/accounts", "/api/v1/admin/payouts", "/api/v1/admin/orders",
				"/api/v1/admin/investment-products", "/api/v1/admin/reports/offerings", "/api/v1/admin/referral-earnings" }) {
			fetch(finance, path).andExpect(status().isForbidden());
		}
	}

	@Test
	void financeHandlesOnlyTheTicketsSupportHandsItAfterTakingThem() throws Exception {
		Account support = api.staff("SUPPORT");
		Account investor = api.register();
		String handed = openTicket(investor, "Refund not received");
		String kept = openTicket(investor, "How do I change my email?");

		// Finance sees nothing until support hands a ticket over.
		tickets(finance).andExpect(jsonPath("$.content[*].id", not(hasItem(handed))));
		fetch(finance, "/api/v1/admin/support/tickets/" + handed).andExpect(status().isNotFound());
		team(support, handed, "FINANCE").andExpect(status().isOk()).andExpect(jsonPath("$.ticket.team").value("FINANCE"));
		tickets(finance)
			.andExpect(jsonPath("$.content[*].id", hasItem(handed)))
			.andExpect(jsonPath("$.content[*].id", not(hasItem(kept))));
		fetch(finance, "/api/v1/admin/support/tickets/" + kept).andExpect(status().isNotFound());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ? AND type = 'TICKET_HANDED_TO_FINANCE'",
				Integer.class, finance.id())).isEqualTo(1);

		// A finance member takes it before working on it; nobody else can then.
		reply(finance, handed).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.message").value(containsString("Assign this ticket to yourself")));
		assign(colleague, handed, finance.id()).andExpect(status().isForbidden());
		assign(finance, handed, finance.id()).andExpect(status().isOk());
		assign(colleague, handed, colleague.id()).andExpect(status().isConflict());
		reply(colleague, handed).andExpect(status().isUnprocessableContent());
		reply(finance, handed).andExpect(status().isOk());

		// Finance cannot hand tickets to itself, but hands this one back to support when done.
		team(finance, kept, "FINANCE").andExpect(status().isNotFound());
		team(finance, handed, "SUPPORT").andExpect(status().isOk())
			.andExpect(jsonPath("$.ticket.team").value("SUPPORT"))
			.andExpect(jsonPath("$.ticket.assigneeId").doesNotExist());
		fetch(finance, "/api/v1/admin/support/tickets/" + handed).andExpect(status().isNotFound());
		assertThat(api.auditCount("TICKET_TEAM_CHANGED", handed)).isEqualTo(2);
	}

	@Test
	void financeConfirmsOnlyThePaymentsItAssignedToItselfAndSeesOnlyThoseWaiting() throws Exception {
		Account investor = new InvestorFixtures(mvc, jdbc, api).approvedInvestor();
		UUID product = new OfferingFixtures(mvc, api.admin()).plan();
		String orderId = new InvestorFixtures(mvc, jdbc, api).placeOrder(investor, product, 1);
		UUID companyAccount = api.companyBankAccount();
		String payment = mvc.perform(post("/api/v1/orders/{id}/payments", orderId).header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.header("Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"BANK_TRANSFER\"}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		String paymentId = JsonPath.read(payment, "$.id");
		String amount = JsonPath.read(payment, "$.amount.amount");
		mvc.perform(post("/api/v1/payments/{id}/deposit", paymentId).header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"companyBankAccountId\":\"" + companyAccount + "\",\"mode\":\"ONLINE\",\"reference\":\"UTR123456789\"}"))
			.andExpect(status().isOk());

		// Finance's list is the payments waiting for confirmation, whatever it asks for.
		mvc.perform(get("/api/v1/admin/payments").param("status", "SUCCEEDED").param("size", "200")
				.header(HttpHeaders.AUTHORIZATION, finance.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[*].id", hasItem(paymentId)))
			.andExpect(jsonPath("$.content[?(@.status != 'PENDING')]").isEmpty());

		confirm(finance, paymentId, amount).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.message").value(containsString("Assign this payment to yourself")));
		payment(finance, paymentId, "take").andExpect(status().isOk())
			.andExpect(jsonPath("$.assignee.id").value(finance.id().toString()));
		payment(colleague, paymentId, "take").andExpect(status().isConflict());
		confirm(colleague, paymentId, amount).andExpect(status().isUnprocessableContent());
		confirm(finance, paymentId, amount).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCEEDED"));
		assertThat(api.auditCount("PAYMENT_TASK_ASSIGNED", paymentId)).isEqualTo(1);
	}

	@Test
	void financePaysOnlyTheFinanceTasksItAssignedToItselfButCannotRunWithdrawals() throws Exception {
		InvestorFixtures investors = new InvestorFixtures(mvc, jdbc, api);
		Account investor = investors.approvedInvestor();
		investors.ensureVerifiedBankAccount(investor);
		investors.enrolInNetwork(investor);
		mvc.perform(post("/api/v1/admin/ledger/adjustments").header(HttpHeaders.AUTHORIZATION, api.admin().bearer())
				.header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content("""
						{"userId":"%s","currency":"INR","direction":"CREDIT","amount":"700","reason":"Test funding","balance":"RENT_BANK"}
						""".formatted(investor.id())))
			.andExpect(status().isCreated());
		mvc.perform(post("/api/v1/admin/withdrawal-runs").param("kind", "RENT").header(HttpHeaders.AUTHORIZATION, finance.bearer()))
			.andExpect(status().isForbidden());
		mvc.perform(post("/api/v1/admin/withdrawal-runs").param("kind", "RENT").header(HttpHeaders.AUTHORIZATION, api.admin().bearer()))
			.andExpect(status().isCreated());
		String task = jdbc.queryForObject("SELECT id::text FROM withdrawals WHERE user_id = ?::uuid", String.class, investor.id());

		mvc.perform(get("/api/v1/admin/finance-tasks").param("size", "200")
				.header(HttpHeaders.AUTHORIZATION, finance.bearer()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.content[*].id", hasItem(task)));
		markPaid(finance, task).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.message").value(containsString("Assign this task to yourself")));
		financeTask(finance, task, "take").andExpect(status().isOk())
			.andExpect(jsonPath("$.assignee.id").value(finance.id().toString()));
		financeTask(colleague, task, "take").andExpect(status().isConflict());
		markPaid(colleague, task).andExpect(status().isUnprocessableContent());
		markPaid(finance, task).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAID"));
		financeTask(finance, task, "release").andExpect(status().isConflict());
		assertThat(api.auditCount("FINANCE_TASK_ASSIGNED", task)).isEqualTo(1);
	}

	private ResultActions financeTask(Account who, String withdrawalId, String action) throws Exception {
		return mvc.perform(post("/api/v1/admin/finance-tasks/{id}/" + action, withdrawalId)
			.header(HttpHeaders.AUTHORIZATION, who.bearer()));
	}

	private ResultActions markPaid(Account who, String withdrawalId) throws Exception {
		return mvc.perform(post("/api/v1/admin/finance-tasks/{id}", withdrawalId).header(HttpHeaders.AUTHORIZATION, who.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"PAID\",\"reference\":\"UTR-1\"}"));
	}

	// -------------------------------------------------------------------------- helpers

	private ResultActions payment(Account who, String paymentId, String action) throws Exception {
		return mvc.perform(post("/api/v1/admin/payments/{id}/" + action, paymentId).header(HttpHeaders.AUTHORIZATION, who.bearer()));
	}

	private ResultActions confirm(Account who, String paymentId, String amount) throws Exception {
		return mvc.perform(post("/api/v1/admin/payments/{id}/confirm", paymentId).header(HttpHeaders.AUTHORIZATION, who.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"externalReference\":\"STATEMENT-1\",\"amountReceived\":\"" + amount + "\"}"));
	}

	private ResultActions fetch(Account who, String path) throws Exception {
		return mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, who.bearer()));
	}

	private String openTicket(Account investor, String subject) throws Exception {
		return JsonPath.read(mvc.perform(multipart("/api/v1/support/tickets").param("subject", subject)
				.param("category", "PAYMENT").param("message", "Please help with this.")
				.header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.ticket.id");
	}

	private ResultActions tickets(Account who) throws Exception {
		return mvc.perform(get("/api/v1/admin/support/tickets").param("size", "200").header(HttpHeaders.AUTHORIZATION, who.bearer()));
	}

	private ResultActions team(Account who, String ticketId, String team) throws Exception {
		return mvc.perform(post("/api/v1/admin/support/tickets/{id}/team", ticketId).header(HttpHeaders.AUTHORIZATION, who.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"team\":\"" + team + "\"}"));
	}

	private ResultActions assign(Account who, String ticketId, UUID assigneeId) throws Exception {
		return mvc.perform(post("/api/v1/admin/support/tickets/{id}/assign", ticketId).header(HttpHeaders.AUTHORIZATION, who.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"assigneeId\":\"" + assigneeId + "\"}"));
	}

	private ResultActions reply(Account who, String ticketId) throws Exception {
		return mvc.perform(multipart("/api/v1/admin/support/tickets/{id}/messages", ticketId).param("body", "We are on it.")
			.header(HttpHeaders.AUTHORIZATION, who.bearer()));
	}

}
