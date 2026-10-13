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
						{"accountHolderName":"Test Investor","bankName":"Test Bank","country":"IN","currency":"INR",
						 "accountNumber":"%s","routingCode":"HDFC0001234"}""".formatted(number))
				.header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
		mvc.perform(post("/api/v1/admin/bank-accounts/{id}/verify", accountId).header(HttpHeaders.AUTHORIZATION, api.admin().bearer()))
			.andExpect(status().isOk());
	}

	/** Puts {@code containers} containers of the plan in the cart and checks out; returns the order id (awaiting payment). */
	public String placeOrder(Account investor, UUID product, int containers) throws Exception {
		ensureVerifiedBankAccount(investor);
		mvc.perform(put("/api/v1/cart/items/{id}", product).header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":" + containers + "}"))
			.andExpect(status().isOk());
		return JsonPath.read(mvc.perform(post("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"acceptedTerms":["%s"]}"""
					.formatted(product)))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
	}

	/**
	 * Buys containers under the plan, paid by bank and confirmed by the admin, who then assigns an available container
	 * of the plan's type to each (starting the leases and payouts); returns the confirmed order id.
	 */
	public String invest(Account investor, UUID product, int containers) throws Exception {
		String orderId = placeOrder(investor, product, containers);
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
		mvc.perform(post("/api/v1/admin/payments/{id}/confirm", paymentId).header(HttpHeaders.AUTHORIZATION, api.admin().bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"externalReference\":\"STATEMENT-1\",\"amountReceived\":\"" + amount + "\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SUCCEEDED"));
		assignContainers(orderId);
		return orderId;
	}

	/** Finance assigns a new container, by number, to each of the order's paid investments. */
	public void assignContainers(String orderId) throws Exception {
		List<UUID> awaiting = jdbc.queryForList(
				"SELECT id FROM holdings WHERE order_id = ?::uuid AND status = 'AWAITING_CONTAINER' ORDER BY created_at",
				UUID.class, orderId);
		for (UUID holding : awaiting) {
			mvc.perform(post("/api/v1/admin/container-assignments/{id}", holding)
					.header(HttpHeaders.AUTHORIZATION, api.admin().bearer())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"containerNumber\":\"" + OfferingFixtures.containerNumber() + "\"}"))
				.andExpect(status().isNoContent());
		}
	}

}
