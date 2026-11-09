package com.sealease.backend.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.sealease.backend.payment.gateway.RazorpayClient;
import com.sealease.backend.support.IntegrationTest;
import com.sealease.backend.support.InvestorFixtures;
import com.sealease.backend.support.OfferingFixtures;
import com.sealease.backend.support.TestApi;
import com.sealease.backend.support.TestApi.Account;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Razorpay is on and no company bank account is active: online investors are offered the checkout and their balance,
 * and bank transfer into an account for the order (withdrawn) is refused with a plain message.
 */
@IntegrationTest
@TestPropertySource(properties = {
		"app.payments.razorpay.key-id=rzp_test_key",
		"app.payments.razorpay.key-secret=test-key-secret",
		"app.payments.razorpay.webhook-secret=test-webhook-secret" })
class GatewayWithoutCompanyAccountsIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoBean
	private RazorpayClient razorpay;

	/** Company accounts other tests left active, switched off here and back on afterwards. */
	private List<UUID> switchedOff = List.of();

	@BeforeEach
	void noActiveCompanyAccount() {
		switchedOff = jdbc.queryForList("SELECT id FROM company_bank_accounts WHERE active", UUID.class);
		jdbc.update("UPDATE company_bank_accounts SET active = false WHERE active");
	}

	@AfterEach
	void restoreCompanyAccounts() {
		switchedOff.forEach(id -> jdbc.update("UPDATE company_bank_accounts SET active = true WHERE id = ?", id));
	}

	@Test
	void onlyTheCheckoutIsOffered() throws Exception {
		TestApi api = new TestApi(mvc, jdbc);
		Account investor = api.register();
		jdbc.update("UPDATE user_profiles SET kyc_status = 'APPROVED' WHERE user_id = ?", investor.id());
		InvestorFixtures investors = new InvestorFixtures(mvc, jdbc, api);
		String orderId = investors.placeOrder(investor, new OfferingFixtures(mvc, api.admin()).plan(), 1);

		mvc.perform(get("/api/v1/payments/methods").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.methods", contains("GATEWAY", "WALLET")));

		start(investor, orderId, "VIRTUAL_ACCOUNT").andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.message").value(containsString("no longer offered")));

		when(razorpay.createOrder(anyLong(), anyString(), anyString(), anyMap())).thenReturn("order_" + UUID.randomUUID());
		start(investor, orderId, "GATEWAY").andExpect(status().isCreated())
			.andExpect(jsonPath("$.method").value("GATEWAY"))
			.andExpect(jsonPath("$.checkout.orderId", containsString("order_")));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM payments WHERE order_id = ?::uuid", Integer.class, orderId))
			.isEqualTo(1);
	}

	@Test
	void anOrderOverTheCheckoutLimitWithNoOtherWayToPayIsExplained() throws Exception {
		TestApi api = new TestApi(mvc, jdbc);
		Account investor = api.register();
		jdbc.update("UPDATE user_profiles SET kyc_status = 'APPROVED' WHERE user_id = ?", investor.id());
		InvestorFixtures investors = new InvestorFixtures(mvc, jdbc, api);
		String orderId = investors.placeOrder(investor, new OfferingFixtures(mvc, api.admin()).plan(), 11); // ₹5,50,000

		start(investor, orderId, "GATEWAY").andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.message").value("Online payment is limited to ₹5,00,000.00 per payment, and this order "
					+ "is ₹5,50,000.00. Please pay from your available balance if it covers the order, or cancel it "
					+ "and place orders of up to ₹5,00,000.00 each."));
		verify(razorpay, never()).createOrder(anyLong(), anyString(), anyString(), anyMap());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM payments WHERE order_id = ?::uuid", Integer.class, orderId))
			.isZero();
	}

	private ResultActions start(Account investor, String orderId, String method) throws Exception {
		return mvc.perform(post("/api/v1/orders/{id}/payments", orderId).header(HttpHeaders.AUTHORIZATION, investor.bearer())
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"" + method + "\"}"));
	}

}
