package com.sealease.backend.reconciliation;

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
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Finding M-8: what needs finance's attention is listed, and the daily run keeps it once across instances. */
@IntegrationTest
class ReconciliationIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private OrderService orders;

	@Autowired
	private ReconciliationService reconciliation;

	private TestApi api;
	private InvestorFixtures investors;
	private OfferingFixtures offerings;

	@BeforeEach
	void setUp() throws Exception {
		api = new TestApi(mvc, jdbc);
		investors = new InvestorFixtures(mvc, jdbc, api);
		offerings = new OfferingFixtures(mvc, api.admin());
	}

	@Test
	void anOverdueRefundIsListedAndDepositsAreReconciledWithTheLedger() throws Exception {
		// Money that arrived for an order that had expired: it must go back, and nobody has refunded it for days.
		Account investor = investors.approvedInvestor();
		String orderId = investors.placeOrder(investor, offerings.plan(), 1);
		String paymentId = JsonPath.read(mvc.perform(post("/api/v1/orders/{id}/payments", orderId)
				.header(HttpHeaders.AUTHORIZATION, investor.bearer()).header("Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"BANK_TRANSFER\"}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
		String amount = jdbc.queryForObject("SELECT amount::text FROM payments WHERE id = ?::uuid", String.class, paymentId);
		jdbc.update("UPDATE orders SET expires_at = now() - interval '1 minute' WHERE id = ?::uuid", orderId);
		orders.expireDue();
		Account finance = api.staff("ADMIN", "FINANCE");
		api.takePayment(paymentId, finance);
		mvc.perform(post("/api/v1/admin/payments/{id}/confirm", paymentId).header(HttpHeaders.AUTHORIZATION, finance.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"externalReference\":\"LATE-" + paymentId + "\",\"amountReceived\":\"" + amount + "\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("REFUND_REQUIRED"));
		jdbc.update("UPDATE payments SET settled_at = now() - interval '5 days' WHERE id = ?::uuid", paymentId);
		String paid = jdbc.queryForObject("SELECT p.id::text FROM payments p JOIN orders o ON o.id = p.order_id "
				+ "WHERE o.id = ?::uuid AND p.status = 'SUCCEEDED'", String.class,
				investors.paidOrder(investors.approvedInvestor(), offerings.plan(), 1));

		mvc.perform(get("/api/v1/admin/reconciliation").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/admin/reconciliation").header(HttpHeaders.AUTHORIZATION, finance.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.issues").isNumber())
			.andExpect(jsonPath("$.pendingRefunds[*].paymentId").value(hasItem(paymentId)))
			// Both were posted to the ledger when the money arrived.
			.andExpect(jsonPath("$.unpostedDeposits[*].paymentId").value(not(hasItem(paymentId))))
			.andExpect(jsonPath("$.unpostedDeposits[*].paymentId").value(not(hasItem(paid))))
			.andExpect(jsonPath("$.trialBalance[0].balanced").value(true))
			.andExpect(jsonPath("$.inTransit").isArray());
	}

	@Test
	void theDailyRunIsKeptOnce() throws Exception {
		reconciliation.run(Duration.ofDays(1));
		// Another instance (or the same one again) within the day finds it done.
		assertThat(reconciliation.run(Duration.ofDays(1))).isEmpty();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM reconciliation_runs WHERE run_at > now() - interval '1 day'",
				Integer.class)).isEqualTo(1);
		mvc.perform(get("/api/v1/admin/reconciliation/runs").header(HttpHeaders.AUTHORIZATION, api.staff("ADMIN", "FINANCE").bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].issues").isNumber());
	}

}
