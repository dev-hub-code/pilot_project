package com.sealease.backend.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * Admins set how online payments run (Platform config → Payment gateway): the switches, the checkout limit and the
 * payment windows apply to payments started from then on. The API keys are never shown or settable.
 */
@IntegrationTest
@TestPropertySource(properties = {
		"app.payments.razorpay.key-id=rzp_test_key",
		"app.payments.razorpay.key-secret=test-key-secret",
		"app.payments.razorpay.webhook-secret=test-webhook-secret" })
class PaymentGatewaySettingsIntegrationTest {

	private static final String PATH = "/api/v1/admin/settings/payment-gateway";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoBean
	private RazorpayClient razorpay;

	private TestApi api;

	@BeforeEach
	void setUp() {
		api = new TestApi(mvc, jdbc);
	}

	@AfterEach
	void backToDefaults() {
		jdbc.update("""
				UPDATE payment_gateway_settings SET online_payments_enabled = NULL, virtual_accounts_enabled = NULL,
				       max_amount = NULL, checkout_window_minutes = NULL, virtual_account_window_hours = NULL,
				       updated_at = NULL, updated_by = NULL WHERE id = 1""");
	}

	@Test
	void theServerDefaultsApplyUntilAnAdminSavesAndTheKeysAreNeverShown() throws Exception {
		String body = mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, api.admin().bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.keysConfigured").value(true))
			.andExpect(jsonPath("$.mode").value("test"))
			.andExpect(jsonPath("$.webhookConfigured").value(true))
			.andExpect(jsonPath("$.onlinePayments").value(true))
			.andExpect(jsonPath("$.virtualAccounts").value(true))
			.andExpect(jsonPath("$.maxAmount.amount").value("500000.00"))
			.andExpect(jsonPath("$.checkoutWindowMinutes").value(30))
			.andExpect(jsonPath("$.virtualAccountWindowHours").value(72))
			.andExpect(jsonPath("$.updatedAt").doesNotExist())
			.andReturn().getResponse().getContentAsString();
		assertThat(body).doesNotContain("test-key-secret").doesNotContain("test-webhook-secret");
	}

	@Test
	void onlySystemSettingsManagersChangeThemAndFinanceMayLook() throws Exception {
		Account investor = api.register();
		Account financeManager = api.staff("FINANCE_MANAGER");
		mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, investor.bearer())).andExpect(status().isForbidden());
		mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, financeManager.bearer())).andExpect(status().isOk());
		save(financeManager, true, true, "500000", 30, 72).andExpect(status().isForbidden());
		save(api.admin(), true, true, "-1", 30, 72).andExpect(status().isBadRequest());
		save(api.admin(), true, true, "500000", 2, 72).andExpect(status().isBadRequest());
	}

	@Test
	void aSavedChangeAppliesToTheNextPaymentAndIsAudited() throws Exception {
		Account admin = api.admin();
		Account investor = approvedInvestor();
		String orderId = new InvestorFixtures(mvc, jdbc, api).placeOrder(investor, new OfferingFixtures(mvc, admin).plan(), 1);

		// Bank transfer off, and checkout limited below this ₹50,000 order.
		save(admin, true, false, "40000", 15, 24).andExpect(status().isOk())
			.andExpect(jsonPath("$.virtualAccounts").value(false))
			.andExpect(jsonPath("$.maxAmount.amount").value("40000.00"))
			.andExpect(jsonPath("$.updatedAt").exists());
		methods(investor).andExpect(jsonPath("$.methods", contains("GATEWAY", "WALLET")))
			.andExpect(jsonPath("$.checkoutLimit.amount").value("40000.00"));
		start(investor, orderId, "GATEWAY").andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.message", containsString("limited to ₹40,000.00")));
		start(investor, orderId, "VIRTUAL_ACCOUNT").andExpect(status().isUnprocessableContent());
		verify(razorpay, never()).createOrder(anyLong(), anyString(), anyString(), anyMap());

		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM audit_logs WHERE action = 'PAYMENT_GATEWAY_SETTINGS_CHANGED' AND actor_user_id = ?""",
				Integer.class, admin.id())).isPositive();

		// Online payments off: online investors pay by bank to a company account, or from their balance.
		save(admin, false, false, "0", 15, 24).andExpect(status().isOk());
		methods(investor).andExpect(jsonPath("$.methods", contains("BANK_TRANSFER", "WALLET")))
			.andExpect(jsonPath("$.checkoutLimit").doesNotExist());
		start(investor, orderId, "GATEWAY").andExpect(status().isUnprocessableContent());
	}

	private ResultActions save(Account actor, boolean online, boolean virtualAccounts, String maxAmount, int checkoutMinutes,
			int vaHours) throws Exception {
		return mvc.perform(put(PATH).header(HttpHeaders.AUTHORIZATION, actor.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("""
					{"onlinePayments":%s,"virtualAccounts":%s,"maxAmount":"%s","checkoutWindowMinutes":%d,
					 "virtualAccountWindowHours":%d}""".formatted(online, virtualAccounts, maxAmount, checkoutMinutes, vaHours)));
	}

	private ResultActions methods(Account investor) throws Exception {
		return mvc.perform(get("/api/v1/payments/methods").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk());
	}

	private ResultActions start(Account investor, String orderId, String method) throws Exception {
		return mvc.perform(post("/api/v1/orders/{id}/payments", orderId).header(HttpHeaders.AUTHORIZATION, investor.bearer())
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"" + method + "\"}"));
	}

	private Account approvedInvestor() throws Exception {
		Account investor = api.register();
		jdbc.update("UPDATE user_profiles SET kyc_status = 'APPROVED' WHERE user_id = ?", investor.id());
		return investor;
	}

}
