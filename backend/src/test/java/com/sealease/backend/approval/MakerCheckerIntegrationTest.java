package com.sealease.backend.approval;

import com.jayway.jsonpath.JsonPath;
import com.sealease.backend.order.service.OrderService;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sensitive money operations need two people, and run exactly once. */
@IntegrationTest
@TestPropertySource(properties = "app.approvals.required=LEDGER_ADJUSTMENT,PAYMENT_REFUND")
class MakerCheckerIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private OrderService orders;

	private TestApi api;

	private Account maker;

	private Account checker;

	private Account investor;

	@BeforeEach
	void setUp() throws Exception {
		api = new TestApi(mvc, jdbc);
		maker = api.staff("ACCOUNTS_EXECUTIVE");
		checker = api.staff("FINANCE_APPROVER");
		investor = api.register();
	}

	private ResultActions send(Account who, MockHttpServletRequestBuilder request, String json) throws Exception {
		return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, who.bearer())
			.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private String adjustment() {
		return """
				{"userId":"%s","currency":"INR","direction":"CREDIT","amount":"500","reason":"Goodwill"}"""
			.formatted(investor.id());
	}

	private String request(Account who) throws Exception {
		String body = send(who, post("/api/v1/admin/approvals"), """
				{"type":"LEDGER_ADJUSTMENT","reason":"Goodwill credit","adjustment":%s}""".formatted(adjustment()))
			.andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"))
			.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	private int adjustments() {
		return jdbc.queryForObject("SELECT count(*) FROM ledger_transactions WHERE reference LIKE ?", Integer.class,
				investor.id() + ":%");
	}

	@Test
	void theDirectRouteIsClosed() throws Exception {
		mvc.perform(post("/api/v1/admin/ledger/adjustments").header(HttpHeaders.AUTHORIZATION, maker.bearer())
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON).content(adjustment()))
			.andExpect(status().isUnprocessableContent());
		assertThat(adjustments()).isZero();
	}

	@Test
	void aSecondPersonApprovesAndTheOperationRuns() throws Exception {
		String id = request(maker);
		assertThat(adjustments()).isZero();

		send(checker, post("/api/v1/admin/approvals/{id}/approve", id), "{}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("APPROVED"))
			.andExpect(jsonPath("$.decidedBy").value(checker.id().toString()))
			.andExpect(jsonPath("$.transactionReference").value(id));
		assertThat(adjustments()).isEqualTo(1);
		assertThat(api.auditCount("APPROVAL_REQUESTED", id)).isEqualTo(1);
		assertThat(api.auditCount("APPROVAL_APPROVED", id)).isEqualTo(1);
	}

	@Test
	void nobodyDecidesTheirOwnRequest() throws Exception {
		String id = request(checker);
		send(checker, post("/api/v1/admin/approvals/{id}/approve", id), "{}").andExpect(status().isForbidden());
		send(checker, post("/api/v1/admin/approvals/{id}/reject", id), """
				{"reason":"Mine"}""").andExpect(status().isForbidden());
		assertThat(adjustments()).isZero();
	}

	@Test
	void theDatabaseItselfRefusesAMakerDeciding() throws Exception {
		String id = request(checker);
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("""
				UPDATE approval_requests SET status = 'APPROVED', decided_by = created_by, decided_at = now()
				WHERE id = ?::uuid""", id)).hasMessageContaining("approval_requests_separation_chk");
	}

	@Test
	void aMakerWithoutTheDecidePermissionCannotApprove() throws Exception {
		String id = request(checker);
		send(maker, post("/api/v1/admin/approvals/{id}/approve", id), "{}").andExpect(status().isForbidden());
	}

	@Test
	void anAssignedRequestWaitsForThatPerson() throws Exception {
		Account other = api.staff("FINANCE_APPROVER");
		String body = send(maker, post("/api/v1/admin/approvals"), """
				{"type":"LEDGER_ADJUSTMENT","reason":"Goodwill","assignedTo":"%s","adjustment":%s}"""
			.formatted(other.id(), adjustment())).andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(body, "$.id");

		send(checker, post("/api/v1/admin/approvals/{id}/approve", id), "{}").andExpect(status().isForbidden());
		send(other, post("/api/v1/admin/approvals/{id}/approve", id), "{}").andExpect(status().isOk());
	}

	@Test
	void aRejectionKeepsItsReasonAndRunsNothing() throws Exception {
		String id = request(maker);
		send(checker, post("/api/v1/admin/approvals/{id}/reject", id), """
				{"reason":"No evidence"}""").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("REJECTED"))
			.andExpect(jsonPath("$.decisionReason").value("No evidence"));
		assertThat(adjustments()).isZero();
		send(checker, post("/api/v1/admin/approvals/{id}/approve", id), "{}").andExpect(status().isUnprocessableContent());
	}

	@Test
	void aFailedOperationLeavesTheRequestPending() throws Exception {
		String body = send(maker, post("/api/v1/admin/approvals"), """
				{"type":"LEDGER_ADJUSTMENT","reason":"Clawback","adjustment":{"userId":"%s","currency":"INR",
				 "direction":"DEBIT","amount":"500","reason":"Clawback"}}""".formatted(investor.id()))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(body, "$.id");

		send(checker, post("/api/v1/admin/approvals/{id}/approve", id), "{}").andExpect(status().isUnprocessableContent());
		assertThat(jdbc.queryForObject("SELECT status FROM approval_requests WHERE id = ?::uuid", String.class, id))
			.isEqualTo("PENDING");
	}

	/** A transfer that arrived after its order expired: recorded, and the money must go back. */
	private String paymentAwaitingRefund() throws Exception {
		Account admin = api.admin();
		UUID product = new OfferingFixtures(mvc, admin).plan();
		api.companyBankAccount();
		InvestorFixtures fixtures = new InvestorFixtures(mvc, jdbc, api);
		Account payer = fixtures.approvedInvestor();
		String orderId = fixtures.placeOrder(payer, product, 1);
		String payment = JsonPath.read(mvc.perform(post("/api/v1/orders/{id}/payments", orderId)
			.header(HttpHeaders.AUTHORIZATION, payer.bearer()).header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"BANK_TRANSFER\"}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
		jdbc.update("UPDATE orders SET expires_at = now() - interval '1 minute' WHERE id = ?::uuid", orderId);
		orders.expireDue();
		Account finance = api.staff("ADMIN", "FINANCE");
		api.takePayment(payment, finance);
		send(finance, post("/api/v1/admin/payments/{id}/confirm", payment), """
				{"externalReference":"BANK-REF-1","amountReceived":"50000"}""")
			.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REFUND_REQUIRED"));
		return payment;
	}

	private String paymentStatus(String payment) {
		return jdbc.queryForObject("SELECT status FROM payments WHERE id = ?::uuid", String.class, payment);
	}

	@Test
	void aRefundRunsOnlyOnceApproved() throws Exception {
		String payment = paymentAwaitingRefund();
		send(maker, post("/api/v1/admin/payments/{id}/refund", payment), """
				{"reference":"OUT-1","reason":"Order expired"}""").andExpect(status().isUnprocessableContent());

		String body = send(maker, post("/api/v1/admin/approvals"), """
				{"type":"PAYMENT_REFUND","reason":"Order expired","paymentId":"%s",
				 "refund":{"reference":"OUT-1","reason":"Order expired"}}""".formatted(payment))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(body, "$.id");
		assertThat(paymentStatus(payment)).isEqualTo("REFUND_REQUIRED");

		send(checker, post("/api/v1/admin/approvals/{id}/approve", id), "{}").andExpect(status().isOk());
		assertThat(paymentStatus(payment)).isEqualTo("REFUNDED");
	}

	@Test
	void theSummaryDescribesWhatWillRunNotWhatElseWasSent() throws Exception {
		String body = send(maker, post("/api/v1/admin/approvals"), """
				{"type":"PAYMENT_REFUND","reason":"Mixed","paymentId":"%s","adjustment":%s,
				 "refund":{"reference":"OUT-2","reason":"Mixed"}}""".formatted(UUID.randomUUID(), adjustment()))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		assertThat(JsonPath.<String>read(body, "$.summary")).startsWith("Refund of payment");
	}

	@Test
	void twoPeopleApprovingAtOnceRunItOnce() throws Exception {
		String id = request(maker);
		Account second = api.staff("FINANCE_APPROVER");
		List<Callable<Integer>> attempts = new ArrayList<>();
		for (Account who : List.of(checker, second, checker, second)) {
			attempts.add(() -> mvc.perform(post("/api/v1/admin/approvals/{id}/approve", id)
				.header(HttpHeaders.AUTHORIZATION, who.bearer())).andReturn().getResponse().getStatus());
		}
		try (ExecutorService pool = Executors.newFixedThreadPool(attempts.size())) {
			List<Integer> statuses = new ArrayList<>();
			for (Future<Integer> attempt : pool.invokeAll(attempts)) {
				statuses.add(attempt.get());
			}
			assertThat(statuses).containsOnlyOnce(200);
		}
		assertThat(adjustments()).isEqualTo(1);
		assertThat(jdbc.queryForObject("""
				SELECT coalesce(sum(CASE direction WHEN 'CREDIT' THEN amount ELSE -amount END), 0)
				FROM ledger_entries""", BigDecimal.class)).isEqualByComparingTo("0");
	}

}
