package com.sealease.backend.payment;

import com.jayway.jsonpath.JsonPath;
import com.sealease.backend.support.IntegrationTest;
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

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Company bank accounts, and investors telling finance how they paid into one. */
@IntegrationTest
class BankPaymentIntegrationTest {

	private static final String ACCOUNTS = "/api/v1/admin/company-bank-accounts";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	private TestApi api;
	private OfferingFixtures offerings;

	@BeforeEach
	void setUp() throws Exception {
		api = new TestApi(mvc, jdbc);
		offerings = new OfferingFixtures(mvc, api.admin());
	}

	// ----------------------------------------------------------------- company accounts

	@Test
	void financeManagesTheCompanyBankAccounts() throws Exception {
		Account finance = api.staff("FINANCE");
		Account investor = approvedInvestor();
		String number = uniqueAccountNumber();

		saveAccount(investor, post(ACCOUNTS), number, "ICIC0000104").andExpect(status().isForbidden());
		saveAccount(finance, post(ACCOUNTS), number, "ICIC104").andExpect(status().isBadRequest());
		saveAccount(finance, post(ACCOUNTS), "12AB", "ICIC0000104").andExpect(status().isBadRequest());

		// Stored normalised: IFSC upper case, account number without spaces.
		String spaced = number.substring(0, 4) + " " + number.substring(4);
		String id = JsonPath.read(saveAccount(finance, post(ACCOUNTS), spaced, "icic0000104")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.ifscCode").value("ICIC0000104"))
			.andExpect(jsonPath("$.accountNumber").value(number))
			.andExpect(jsonPath("$.active").value(true))
			.andReturn().getResponse().getContentAsString(), "$.id");
		saveAccount(finance, post(ACCOUNTS), number, "ICIC0000104").andExpect(status().isConflict());
		assertThat(api.auditCount("COMPANY_BANK_ACCOUNT_CREATED", id)).isEqualTo(1);

		saveAccount(finance, put(ACCOUNTS + "/{id}", id), number, "ICIC0000999").andExpect(status().isOk())
			.andExpect(jsonPath("$.ifscCode").value("ICIC0000999"));
		assertThat(api.auditCount("COMPANY_BANK_ACCOUNT_UPDATED", id)).isEqualTo(1);

		// Investors are offered active accounts only.
		String orderId = placeOrder(investor, offerings.plan(1));
		startBankPayment(investor, orderId).andExpect(jsonPath("$.bankTransfer.accounts[*].id", hasItem(id)));
		mvc.perform(post(ACCOUNTS + "/{id}/deactivate", id).header(HttpHeaders.AUTHORIZATION, finance.bearer()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
		mvc.perform(get("/api/v1/orders/{id}/payments", orderId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$[0].bankTransfer.accounts[*].id", not(hasItem(id))));
		mvc.perform(get(ACCOUNTS).header(HttpHeaders.AUTHORIZATION, finance.bearer()))
			.andExpect(jsonPath("$[*].id", hasItem(id)));
		assertThat(api.auditCount("COMPANY_BANK_ACCOUNT_DEACTIVATED", id)).isEqualTo(1);
	}

	// ------------------------------------------------------------------ payment details

	@Test
	void investorsSayHowTheyPaidAndFinanceConfirms() throws Exception {
		UUID account = api.companyBankAccount();
		Account investor = approvedInvestor();
		Account finance = api.staff("FINANCE");
		String orderId = placeOrder(investor, offerings.plan(1));
		String payment = paymentId(startBankPayment(investor, orderId));

		Account stranger = approvedInvestor();
		submit(stranger, payment, account, "ONLINE", "UTR123456789").andExpect(status().isNotFound());
		submit(investor, payment, account, "CHEQUE", "").andExpect(status().isBadRequest());
		submit(investor, payment, UUID.randomUUID(), "CHEQUE", "004512").andExpect(status().isUnprocessableContent());

		submit(investor, payment, account, "CHEQUE", "004512").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PENDING"))
			.andExpect(jsonPath("$.deposit.mode").value("CHEQUE"))
			.andExpect(jsonPath("$.deposit.reference").value("004512"))
			.andExpect(jsonPath("$.deposit.companyBankAccountId").value(account.toString()))
			.andExpect(jsonPath("$.deposit.bankName").value("Test Bank"));
		// The order now waits for the cheque to clear instead of lapsing after the checkout window.
		Instant expiresAt = jdbc.queryForObject("SELECT expires_at FROM orders WHERE id = ?::uuid", OffsetDateTime.class,
				orderId).toInstant();
		assertThat(expiresAt).isAfter(Instant.now().plus(Duration.ofDays(6)));

		// A mistake can be corrected until finance has decided.
		submit(investor, payment, account, "ONLINE", "utr998877665544").andExpect(status().isOk())
			.andExpect(jsonPath("$.deposit.mode").value("ONLINE"))
			.andExpect(jsonPath("$.deposit.reference").value("UTR998877665544"));
		assertThat(api.auditCount("PAYMENT_DETAILS_SUBMITTED", payment)).isEqualTo(2);

		mvc.perform(get("/api/v1/admin/payments").param("status", "PENDING").param("method", "BANK_TRANSFER")
				.param("size", "200").header(HttpHeaders.AUTHORIZATION, finance.bearer()))
			.andExpect(jsonPath("$.content[?(@.id == '%s')].deposit.reference".formatted(payment)).value("UTR998877665544"));

		confirm(finance, payment, "UTR998877665544", "50000").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SUCCEEDED"))
			.andExpect(jsonPath("$.deposit.reference").value("UTR998877665544"));
		mvc.perform(get("/api/v1/orders/{id}", orderId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.status").value("CONFIRMED"));
		submit(investor, payment, account, "ONLINE", "UTR000").andExpect(status().isConflict());
	}

	@Test
	void financeRejectsMoneyThatNeverArrivedAndTheInvestorPaysAgain() throws Exception {
		UUID account = api.companyBankAccount();
		Account investor = approvedInvestor();
		Account finance = api.staff("FINANCE");
		String orderId = placeOrder(investor, offerings.plan(1));
		String payment = paymentId(startBankPayment(investor, orderId));
		submit(investor, payment, account, "CASH_DEPOSIT", "RCPT-7781").andExpect(status().isOk());

		reject(investor, payment).andExpect(status().isForbidden());
		reject(finance, payment).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("FAILED"))
			.andExpect(jsonPath("$.failureReason").value("No deposit with this receipt number"));
		reject(finance, payment).andExpect(status().isConflict());
		assertThat(api.auditCount("PAYMENT_REJECTED", payment)).isEqualTo(1);
		submit(investor, payment, account, "CASH_DEPOSIT", "RCPT-7782").andExpect(status().isConflict());

		// The order is still awaiting payment: the investor tries again.
		String second = paymentId(startBankPayment(investor, orderId).andExpect(status().isCreated()));
		assertThat(second).isNotEqualTo(payment);
		submit(investor, second, account, "CASH_DEPOSIT", "RCPT-7782").andExpect(status().isOk());

	}

	// ------------------------------------------------------------------------------ helpers

	private Account approvedInvestor() throws Exception {
		Account investor = api.register();
		jdbc.update("UPDATE user_profiles SET kyc_status = 'APPROVED' WHERE user_id = ?", investor.id());
		return investor;
	}

	private static String uniqueAccountNumber() {
		return String.valueOf(1_000_000_000L + (long) (Math.random() * 8_999_999_999L));
	}

	private ResultActions saveAccount(Account actor,
			org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String number, String ifsc)
			throws Exception {
		return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, actor.bearer()).contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"accountName":"SeaLease Collections","bankName":"ICICI Bank","accountNumber":"%s","ifscCode":"%s"}"""
				.formatted(number, ifsc)));
	}

	private String placeOrder(Account investor, UUID product) throws Exception {
		mvc.perform(put("/api/v1/cart/items/{id}", product).header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
			.andExpect(status().isOk());
		return JsonPath.read(mvc.perform(post("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"acceptedTerms":["%s"]}""".formatted(product)))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
	}

	private ResultActions startBankPayment(Account investor, String orderId) throws Exception {
		return mvc.perform(post("/api/v1/orders/{id}/payments", orderId).header(HttpHeaders.AUTHORIZATION, investor.bearer())
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"BANK_TRANSFER\"}"));
	}

	private static String paymentId(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
	}

	private ResultActions submit(Account investor, String paymentId, UUID account, String mode, String reference)
			throws Exception {
		return mvc.perform(post("/api/v1/payments/{id}/deposit", paymentId).header(HttpHeaders.AUTHORIZATION, investor.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"companyBankAccountId":"%s","mode":"%s","reference":"%s"}""".formatted(account, mode, reference)));
	}

	private ResultActions confirm(Account actor, String paymentId, String reference, String amount) throws Exception {
		return mvc.perform(post("/api/v1/admin/payments/{id}/confirm", paymentId).header(HttpHeaders.AUTHORIZATION, actor.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"externalReference\":\"" + reference + "\",\"amountReceived\":\"" + amount + "\"}"));
	}

	private ResultActions reject(Account actor, String paymentId) throws Exception {
		return mvc.perform(post("/api/v1/admin/payments/{id}/reject", paymentId).header(HttpHeaders.AUTHORIZATION, actor.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"No deposit with this receipt number\"}"));
	}

}
