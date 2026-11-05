package com.sealease.backend.support;

import com.jayway.jsonpath.JsonPath;
import com.sealease.backend.support.TestApi.Account;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Investors and confirmed investments, made through the investor API, for tests that need holdings. */
public final class InvestorFixtures {

	private final MockMvc mvc;
	private final JdbcTemplate jdbc;
	private final TestApi api;
	private UUID companyAccount;

	public InvestorFixtures(MockMvc mvc, JdbcTemplate jdbc, TestApi api) {
		this.mvc = mvc;
		this.jdbc = jdbc;
		this.api = api;
	}

	/** A registered investor whose identity verification is approved. */
	public Account approvedInvestor() throws Exception {
		return approve(api.register());
	}

	/** Marks the account's identity verification as approved. */
	public Account approve(Account investor) {
		jdbc.update("UPDATE user_profiles SET kyc_status = 'APPROVED' WHERE user_id = ?", investor.id());
		return investor;
	}

	/**
	 * Gives the investor a verified bank account, which buying requires, unless they already have one. The admin
	 * verifies it, so the investor's identity must already be approved.
	 */
	public void ensureVerifiedBankAccount(Account investor) throws Exception {
		Integer verified = jdbc.queryForObject("SELECT count(*) FROM bank_accounts WHERE user_id = ? AND status = 'VERIFIED'",
				Integer.class, investor.id());
		if (verified != null && verified > 0) {
			return;
		}
		String number = String.valueOf(ThreadLocalRandom.current().nextLong(100_000_000_000L, 999_999_999_999L));
		String accountId = JsonPath.read(mvc.perform(BankAccountUploads.addAccount("""
						{"accountHolderName":"Test Investor","country":"IN","currency":"INR",
						 "accountNumber":"%s","routingCode":"HDFC0001234"}""".formatted(number))
				.header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
		api.assignReview("bank-accounts", accountId, api.admin());
		mvc.perform(post("/api/v1/admin/bank-accounts/{id}/verify", accountId).header(HttpHeaders.AUTHORIZATION, api.admin().bearer()))
			.andExpect(status().isOk());
	}

	/** Buys {@code lots} lots (₹50,000 each) of the plan; returns the order id (awaiting payment). */
	public String placeOrder(Account investor, UUID product, int lots) throws Exception {
		ensureVerifiedBankAccount(investor);
		return JsonPath.read(mvc.perform(post("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"productId":"%s","units":%d,"acceptTerms":true}"""
					.formatted(product, lots * OfferingFixtures.LOT)))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
	}

	/**
	 * Buys units of the plan, paid by bank and confirmed by the admin; the investor then accepts each agreement
	 * (starting the leases and payouts). Returns the confirmed order id.
	 */
	public String invest(Account investor, UUID product, int units) throws Exception {
		String orderId = paidOrder(investor, product, units);
		acceptAgreements(investor, orderId);
		return orderId;
	}

	/** Buys units of the plan, paid by bank and confirmed by the admin; they await the investor's acceptance. */
	public String paidOrder(Account investor, UUID product, int units) throws Exception {
		String orderId = placeOrder(investor, product, units);
		// Bank payments are only offered while the company has an active account to pay into.
		if (companyAccount == null) {
			companyAccount = api.companyBankAccount();
		}
		String payment = mvc.perform(post("/api/v1/orders/{id}/payments", orderId)
				.header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.header("Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"BANK_TRANSFER\"}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		String paymentId = JsonPath.read(payment, "$.id");
		String amount = JsonPath.read(payment, "$.amount.amount");
		mvc.perform(post("/api/v1/payments/{id}/deposit", paymentId).header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"companyBankAccountId":"%s","mode":"ONLINE","reference":"UTR%s"}"""
					.formatted(companyAccount, paymentId.replace("-", "").substring(0, 12))))
			.andExpect(status().isOk());
		Account admin = api.admin();
		api.takePayment(paymentId, admin);
		mvc.perform(post("/api/v1/admin/payments/{id}/confirm", paymentId).header(HttpHeaders.AUTHORIZATION, admin.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"externalReference\":\"STATEMENT-" + paymentId + "\",\"amountReceived\":\"" + amount + "\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SUCCEEDED"));
		return orderId;
	}

	/** The investor accepts the agreement of each of the order's paid investments, which starts them. */
	public void acceptAgreements(Account investor, String orderId) throws Exception {
		for (UUID holding : awaitingAcceptance(orderId)) {
			acceptAgreement(investor, holding);
		}
	}

	/** Makes the investor a network investor. */
	public void enrolInNetwork(Account investor) {
		jdbc.update("INSERT INTO network_investors (user_id, signups_join_network, created_at) VALUES (?, false, now())",
				investor.id());
	}

	/** The investor accepts the investment agreement, which starts the term and returns. */
	public void acceptAgreement(Account investor, UUID holdingId) throws Exception {
		mvc.perform(post("/api/v1/agreements/{id}/accept", holdingId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk());
	}

	/** The order's paid investments awaiting the investor's acceptance. */
	public List<UUID> awaitingAcceptance(String orderId) {
		return jdbc.queryForList("SELECT id FROM holdings WHERE order_id = ?::uuid AND status = 'AWAITING_ACCEPTANCE' ORDER BY created_at",
				UUID.class, orderId);
	}

}
