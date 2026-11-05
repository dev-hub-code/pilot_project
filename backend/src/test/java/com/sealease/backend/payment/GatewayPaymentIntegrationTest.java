package com.sealease.backend.payment;

import com.jayway.jsonpath.JsonPath;
import com.sealease.backend.payment.gateway.RazorpayClient;
import com.sealease.backend.payment.gateway.RazorpayClient.GatewayPayment;
import com.sealease.backend.payment.gateway.RazorpayClient.VirtualAccount;
import com.sealease.backend.payment.gateway.RazorpaySignatures;
import com.sealease.backend.payment.provider.PaymentProvider.VirtualAccountDetails;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Online investors pay through Razorpay (checkout or virtual account) and their orders confirm without finance; network
 * investors keep paying by bank or at the office. Razorpay's API is mocked; signatures are real.
 */
@IntegrationTest
@TestPropertySource(properties = {
		"app.payments.razorpay.key-id=rzp_test_key",
		"app.payments.razorpay.key-secret=test-key-secret",
		"app.payments.razorpay.webhook-secret=test-webhook-secret" })
class GatewayPaymentIntegrationTest {

	private static final String KEY_SECRET = "test-key-secret";
	private static final String WEBHOOK_SECRET = "test-webhook-secret";
	/** 100 units of the fixture plan. */
	private static final long ORDER_PAISE = 5_000_000;

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoBean
	private RazorpayClient razorpay;

	private TestApi api;
	private OfferingFixtures offerings;

	@BeforeEach
	void setUp() throws Exception {
		api = new TestApi(mvc, jdbc);
		offerings = new OfferingFixtures(mvc, api.admin());
		api.companyBankAccount();
	}

	@Test
	void onlineInvestorsPayThroughTheGatewayAndNetworkInvestorsByBankOrAtTheOffice() throws Exception {
		Account online = approvedInvestor();
		Account network = network(approvedInvestor());

		methods(online).andExpect(jsonPath("$.methods", contains("GATEWAY", "VIRTUAL_ACCOUNT")));
		methods(network).andExpect(jsonPath("$.methods", contains("BANK_TRANSFER", "OFFICE_CASH")));

		String onlineOrder = placeOrder(online, offerings.plan());
		startPayment(online, onlineOrder, "BANK_TRANSFER").andExpect(status().isUnprocessableContent());
		startPayment(online, onlineOrder, "OFFICE_CASH").andExpect(status().isUnprocessableContent());

		String networkOrder = placeOrder(network, offerings.networkPlan());
		startPayment(network, networkOrder, "GATEWAY").andExpect(status().isUnprocessableContent());
		startPayment(network, networkOrder, "VIRTUAL_ACCOUNT").andExpect(status().isUnprocessableContent());
		startPayment(network, networkOrder, "BANK_TRANSFER").andExpect(status().isCreated())
			.andExpect(jsonPath("$.bankTransfer.reference").isNotEmpty());
	}

	@Test
	void aCheckoutPaymentConfirmsTheOrderOnceRazorpayHasTakenTheMoney() throws Exception {
		Account investor = approvedInvestor();
		String orderId = placeOrder(investor, offerings.plan());
		String gatewayOrder = "order_" + suffix();
		when(razorpay.createOrder(eq(ORDER_PAISE), eq("INR"), anyString(), anyMap())).thenReturn(gatewayOrder);

		String payment = paymentId(startPayment(investor, orderId, "GATEWAY").andExpect(status().isCreated())
			.andExpect(jsonPath("$.checkout.keyId").value("rzp_test_key"))
			.andExpect(jsonPath("$.checkout.orderId").value(gatewayOrder))
			.andExpect(jsonPath("$.checkout.amountPaise").value(ORDER_PAISE))
			.andExpect(jsonPath("$.checkout.currency").value("INR")));

		// A forged result is refused without asking Razorpay.
		String gatewayPayment = "pay_" + suffix();
		checkout(investor, payment, gatewayOrder, gatewayPayment, "deadbeef").andExpect(status().isBadRequest());
		verify(razorpay, never()).fetchPayment(gatewayPayment);
		// Someone else's payment is not theirs to complete.
		checkout(approvedInvestor(), payment, gatewayOrder, gatewayPayment, checkoutSignature(gatewayOrder, gatewayPayment))
			.andExpect(status().isNotFound());

		when(razorpay.fetchPayment(gatewayPayment))
			.thenReturn(new GatewayPayment(gatewayPayment, gatewayOrder, ORDER_PAISE, "INR", "captured"));
		checkout(investor, payment, gatewayOrder, gatewayPayment, checkoutSignature(gatewayOrder, gatewayPayment))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SUCCEEDED"))
			.andExpect(jsonPath("$.externalReference").value(gatewayPayment))
			.andExpect(jsonPath("$.checkout").doesNotExist());
		orderStatus(investor, orderId).andExpect(jsonPath("$.status").value("CONFIRMED"));

		// Razorpay's webhook for the same payment arrives too: nothing changes.
		webhook(capturedEvent(gatewayPayment, gatewayOrder, ORDER_PAISE)).andExpect(status().isOk());
		assertThat(paymentsOf(orderId)).isEqualTo(1);
		assertThat(api.auditCount("PAYMENT_SUCCEEDED", payment)).isEqualTo(1);
	}

	@Test
	void theWebhookAloneConfirmsACheckoutPaymentCapturingOneThatIsOnlyAuthorized() throws Exception {
		Account investor = approvedInvestor();
		String orderId = placeOrder(investor, offerings.plan());
		String gatewayOrder = "order_" + suffix();
		when(razorpay.createOrder(anyLong(), anyString(), anyString(), anyMap())).thenReturn(gatewayOrder);
		String payment = paymentId(startPayment(investor, orderId, "GATEWAY").andExpect(status().isCreated()));

		String gatewayPayment = "pay_" + suffix();
		when(razorpay.fetchPayment(gatewayPayment))
			.thenReturn(new GatewayPayment(gatewayPayment, gatewayOrder, ORDER_PAISE, "INR", "authorized"));
		when(razorpay.capture(gatewayPayment, ORDER_PAISE, "INR"))
			.thenReturn(new GatewayPayment(gatewayPayment, gatewayOrder, ORDER_PAISE, "INR", "captured"));

		webhook(event("payment.authorized", """
				"payment":{"entity":{"id":"%s","order_id":"%s","amount":%d,"currency":"INR","status":"authorized"}}"""
			.formatted(gatewayPayment, gatewayOrder, ORDER_PAISE))).andExpect(status().isOk());

		verify(razorpay).capture(gatewayPayment, ORDER_PAISE, "INR");
		assertThat(statusOf(payment)).isEqualTo("SUCCEEDED");
		orderStatus(investor, orderId).andExpect(jsonPath("$.status").value("CONFIRMED"));
	}

	@Test
	void anOrderOverTheCheckoutLimitIsPaidByBankTransferInstead() throws Exception {
		Account investor = approvedInvestor();
		methods(investor).andExpect(jsonPath("$.checkoutLimit.amount").value("500000.00"));
		String orderId = placeOrder(investor, offerings.plan(), 1100); // ₹5,50,000: over Razorpay's ₹5,00,000

		startPayment(investor, orderId, "GATEWAY").andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.message").value("Online payment is limited to ₹5,00,000.00 per payment, and this order "
					+ "is ₹5,50,000.00. Please pay by bank transfer instead."));
		verify(razorpay, never()).createOrder(anyLong(), anyString(), anyString(), anyMap());

		when(razorpay.createVirtualAccount(eq(55_000_000L), anyString(), any(Instant.class), anyMap()))
			.thenReturn(new VirtualAccount("va_" + suffix(),
					new VirtualAccountDetails("2223330012345679", "RATN0VAAPIS", "SeaLease", "RBL Bank", null)));
		startPayment(investor, orderId, "VIRTUAL_ACCOUNT").andExpect(status().isCreated())
			.andExpect(jsonPath("$.virtualAccount.amount.amount").value("550000.00"));
	}

	@Test
	void aVirtualAccountTakesABankTransferAndOnlyTheRightAmountPaysTheOrder() throws Exception {
		Account investor = approvedInvestor();
		String orderId = placeOrder(investor, offerings.plan());
		String account = "va_" + suffix();
		when(razorpay.createVirtualAccount(eq(ORDER_PAISE), anyString(), any(Instant.class), anyMap()))
			.thenReturn(new VirtualAccount(account,
					new VirtualAccountDetails("2223330012345678", "RATN0VAAPIS", "SeaLease", "RBL Bank", "rpy.sl123@icici")));

		String payment = paymentId(startPayment(investor, orderId, "VIRTUAL_ACCOUNT").andExpect(status().isCreated())
			.andExpect(jsonPath("$.virtualAccount.accountNumber").value("2223330012345678"))
			.andExpect(jsonPath("$.virtualAccount.ifsc").value("RATN0VAAPIS"))
			.andExpect(jsonPath("$.virtualAccount.upiId").value("rpy.sl123@icici"))
			.andExpect(jsonPath("$.virtualAccount.amount.amount").value("50000.00")));
		// The order stays pending while the transfer is on its way.
		assertThat(expiresAt(orderId)).isAfter(Instant.now().plus(Duration.ofDays(2)));

		// Too little: kept aside for refund, and the order still waits.
		String short1 = "pay_" + suffix();
		webhook(creditedEvent(short1, account, 4_000_000)).andExpect(status().isOk());
		assertThat(statusOf(payment)).isEqualTo("REFUND_REQUIRED");
		orderStatus(investor, orderId).andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));

		// The right amount, into the same account: it pays the order, and the account is closed.
		String exact = "pay_" + suffix();
		webhook(creditedEvent(exact, account, ORDER_PAISE)).andExpect(status().isOk());
		webhook(creditedEvent(exact, account, ORDER_PAISE)).andExpect(status().isOk());
		assertThat(statusOfGatewayPayment(exact)).isEqualTo("SUCCEEDED");
		orderStatus(investor, orderId).andExpect(jsonPath("$.status").value("CONFIRMED"));
		verify(razorpay, timeout(2000)).closeVirtualAccount(account);

		// Anything more is refunded, never kept.
		String extra = "pay_" + suffix();
		webhook(creditedEvent(extra, account, ORDER_PAISE)).andExpect(status().isOk());
		assertThat(statusOfGatewayPayment(extra)).isEqualTo("REFUND_REQUIRED");
		assertThat(paymentsOf(orderId)).isEqualTo(3);
	}

	@Test
	void choosingTheCheckoutInsteadClosesTheVirtualAccount() throws Exception {
		Account investor = approvedInvestor();
		String orderId = placeOrder(investor, offerings.plan());
		String account = "va_" + suffix();
		when(razorpay.createVirtualAccount(anyLong(), anyString(), any(Instant.class), anyMap()))
			.thenReturn(new VirtualAccount(account, new VirtualAccountDetails("2223330087654321", "RATN0VAAPIS", null, null, null)));
		when(razorpay.createOrder(anyLong(), anyString(), anyString(), anyMap())).thenReturn("order_" + suffix());

		String transfer = paymentId(startPayment(investor, orderId, "VIRTUAL_ACCOUNT").andExpect(status().isCreated()));
		startPayment(investor, orderId, "GATEWAY").andExpect(status().isCreated());

		assertThat(statusOf(transfer)).isEqualTo("CANCELLED");
		verify(razorpay, timeout(2000)).closeVirtualAccount(account);
	}

	@Test
	void webhooksMustBeSignedAndOnesForOtherPaymentsAreAcknowledged() throws Exception {
		String body = creditedEvent("pay_" + suffix(), "va_unknown", ORDER_PAISE);
		mvc.perform(post("/api/v1/webhooks/razorpay").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isUnauthorized());
		mvc.perform(post("/api/v1/webhooks/razorpay").contentType(MediaType.APPLICATION_JSON).content(body)
				.header("X-Razorpay-Signature", RazorpaySignatures.sign("wrong-secret", body)))
			.andExpect(status().isUnauthorized());
		webhook(body).andExpect(status().isOk());
		webhook(event("refund.processed", "\"refund\":{\"entity\":{\"id\":\"rfnd_1\"}}")).andExpect(status().isOk());
	}

	@Test
	void moneyForAnUnknownReferenceIsRecordedOnceForFinanceToResolve() throws Exception {
		String gatewayPayment = "pay_" + suffix();
		String account = "va_" + suffix();
		String body = creditedEvent(gatewayPayment, account, ORDER_PAISE);
		webhook(body).andExpect(status().isOk());
		webhook(body).andExpect(status().isOk()); // Razorpay delivers again: still one record.

		String id = jdbc.queryForObject("""
				SELECT id::text FROM unmatched_gateway_receipts WHERE provider = 'RAZORPAY' AND gateway_payment_id = ?""",
				String.class, gatewayPayment);
		assertThat(api.auditCount("PAYMENT_UNMATCHED_RECEIVED", id)).isEqualTo(1);

		Account finance = api.staff("ADMIN", "FINANCE");
		mvc.perform(get("/api/v1/admin/payments/unmatched").param("open", "true")
				.header(HttpHeaders.AUTHORIZATION, approvedInvestor().bearer()))
			.andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/admin/payments/unmatched").param("open", "true")
				.header(HttpHeaders.AUTHORIZATION, finance.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.id == '%s')].providerReference".formatted(id)).value(account))
			.andExpect(jsonPath("$[?(@.id == '%s')].amount.amount".formatted(id)).value("50000.00"));

		resolve(finance, id, " ").andExpect(status().isBadRequest());
		resolve(finance, id, "Refunded to the sender, Razorpay refund rfnd_123").andExpect(status().isOk())
			.andExpect(jsonPath("$.resolvedBy").value(finance.id().toString()))
			.andExpect(jsonPath("$.resolution").value("Refunded to the sender, Razorpay refund rfnd_123"));
		resolve(finance, id, "Again").andExpect(status().isUnprocessableContent());
		resolve(finance, UUID.randomUUID().toString(), "Unknown").andExpect(status().isNotFound());
		assertThat(api.auditCount("PAYMENT_UNMATCHED_RESOLVED", id)).isEqualTo(1);
	}

	private ResultActions resolve(Account actor, String receiptId, String note) throws Exception {
		return mvc.perform(post("/api/v1/admin/payments/unmatched/{id}/resolve", receiptId)
			.header(HttpHeaders.AUTHORIZATION, actor.bearer()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\":\"" + note + "\"}"));
	}

	// ------------------------------------------------------------------------------ helpers

	/** Identity verified and a verified bank account: able to buy. */
	private Account approvedInvestor() throws Exception {
		Account investor = api.register();
		jdbc.update("UPDATE user_profiles SET kyc_status = 'APPROVED' WHERE user_id = ?", investor.id());
		new InvestorFixtures(mvc, jdbc, api).ensureVerifiedBankAccount(investor);
		return investor;
	}

	private Account network(Account investor) {
		jdbc.update("INSERT INTO network_investors (user_id, signups_join_network, created_at) VALUES (?, false, now())",
				investor.id());
		return investor;
	}

	private static String suffix() {
		return UUID.randomUUID().toString().replace("-", "").substring(0, 14);
	}

	private ResultActions methods(Account investor) throws Exception {
		return mvc.perform(get("/api/v1/payments/methods").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk());
	}

	private String placeOrder(Account investor, UUID product) throws Exception {
		return placeOrder(investor, product, 100);
	}

	private String placeOrder(Account investor, UUID product, int units) throws Exception {
		return JsonPath.read(mvc.perform(post("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"productId":"%s","units":%d,"acceptTerms":true}""".formatted(product, units)))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
	}

	private ResultActions startPayment(Account investor, String orderId, String method) throws Exception {
		return mvc.perform(post("/api/v1/orders/{id}/payments", orderId).header(HttpHeaders.AUTHORIZATION, investor.bearer())
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"" + method + "\"}"));
	}

	private ResultActions checkout(Account investor, String paymentId, String gatewayOrder, String gatewayPayment,
			String signature) throws Exception {
		return mvc.perform(post("/api/v1/payments/{id}/checkout", paymentId)
			.header(HttpHeaders.AUTHORIZATION, investor.bearer()).contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"gatewayOrderId":"%s","gatewayPaymentId":"%s","signature":"%s"}"""
				.formatted(gatewayOrder, gatewayPayment, signature)));
	}

	private static String checkoutSignature(String gatewayOrder, String gatewayPayment) {
		return RazorpaySignatures.sign(KEY_SECRET, gatewayOrder + "|" + gatewayPayment);
	}

	private ResultActions webhook(String body) throws Exception {
		return mvc.perform(post("/api/v1/webhooks/razorpay").contentType(MediaType.APPLICATION_JSON).content(body)
			.header("X-Razorpay-Signature", RazorpaySignatures.sign(WEBHOOK_SECRET, body)));
	}

	private static String event(String type, String payload) {
		return """
				{"entity":"event","event":"%s","payload":{%s}}""".formatted(type, payload);
	}

	private static String capturedEvent(String gatewayPayment, String gatewayOrder, long paise) {
		return event("payment.captured", """
				"payment":{"entity":{"id":"%s","order_id":"%s","amount":%d,"currency":"INR","status":"captured"}}"""
			.formatted(gatewayPayment, gatewayOrder, paise));
	}

	private static String creditedEvent(String gatewayPayment, String virtualAccount, long paise) {
		return event("virtual_account.credited", """
				"payment":{"entity":{"id":"%s","order_id":null,"amount":%d,"currency":"INR","status":"captured","method":"bank_transfer"}},
				"virtual_account":{"entity":{"id":"%s"}}""".formatted(gatewayPayment, paise, virtualAccount));
	}

	private ResultActions orderStatus(Account investor, String orderId) throws Exception {
		return mvc.perform(get("/api/v1/orders/{id}", orderId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk());
	}

	private static String paymentId(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
	}

	private String statusOf(String paymentId) {
		return jdbc.queryForObject("SELECT status FROM payments WHERE id = ?::uuid", String.class, paymentId);
	}

	private String statusOfGatewayPayment(String gatewayPayment) {
		return jdbc.queryForObject("SELECT status FROM payments WHERE provider = 'RAZORPAY' AND external_reference = ?",
				String.class, gatewayPayment);
	}

	private int paymentsOf(String orderId) {
		return jdbc.queryForObject("SELECT count(*) FROM payments WHERE order_id = ?::uuid", Integer.class, orderId);
	}

	private Instant expiresAt(String orderId) {
		return jdbc.queryForObject("SELECT expires_at FROM orders WHERE id = ?::uuid", OffsetDateTime.class, orderId)
			.toInstant();
	}

}
