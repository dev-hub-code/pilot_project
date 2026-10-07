package com.sealease.backend.support;

import com.jayway.jsonpath.JsonPath;
import com.sealease.backend.support.TestApi.Account;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Investors and confirmed investments, made through the investor API, for tests that need holdings. */
public final class InvestorFixtures {

	private final MockMvc mvc;
	private final JdbcTemplate jdbc;
	private final TestApi api;

	public InvestorFixtures(MockMvc mvc, JdbcTemplate jdbc, TestApi api) {
		this.mvc = mvc;
		this.jdbc = jdbc;
		this.api = api;
	}

	/** A registered investor whose identity verification is approved. */
	public Account approvedInvestor() throws Exception {
		Account investor = api.register();
		jdbc.update("UPDATE user_profiles SET kyc_status = 'APPROVED' WHERE user_id = ?", investor.id());
		return investor;
	}

	/** An approved investor classified as high-net-worth, who may buy standalone (HNI) containers. */
	public Account hniInvestor() throws Exception {
		Account investor = approvedInvestor();
		jdbc.update("UPDATE user_profiles SET investor_type = 'HNI' WHERE user_id = ?", investor.id());
		return investor;
	}

	/** Puts the amount in the cart and checks out; returns the order id (awaiting payment). */
	public String placeOrder(Account investor, UUID product, String amount) throws Exception {
		mvc.perform(put("/api/v1/cart/items/{id}", product).header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"" + amount + "\"}"))
			.andExpect(status().isOk());
		return JsonPath.read(mvc.perform(post("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"acceptedTerms":[{"productId":"%s","termsVersion":"%s"}]}"""
					.formatted(product, OfferingFixtures.TERMS_VERSION)))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
	}

	/** Places an order and pays it by (simulated) card; returns the confirmed order id. */
	public String invest(Account investor, UUID product, String amount) throws Exception {
		String orderId = placeOrder(investor, product, amount);
		String paymentId = JsonPath.read(mvc.perform(post("/api/v1/orders/{id}/payments", orderId)
				.header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.header("Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"CARD\"}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
		mvc.perform(post("/api/v1/payments/{id}/simulate", paymentId).header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"SUCCEEDED\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SUCCEEDED"));
		return orderId;
	}

}
