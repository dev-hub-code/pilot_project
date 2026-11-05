package com.sealease.backend.support;

import com.jayway.jsonpath.JsonPath;
import com.sealease.backend.support.TestApi.Account;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Publishes plans through the admin API, for tests that need something to invest in.
 */
public final class OfferingFixtures {

	/** An online plan unless a test asks for a network one. */
	public static final String TYPE = "ONLINE";

	/** What tests invest per "lot": rupees. */
	public static final int UNIT = 50_000;

	/** The ₹500 units in a lot. */
	public static final int LOT = 100;

	private final MockMvc mvc;
	private final Account admin;

	public OfferingFixtures(MockMvc mvc, Account admin) {
		this.mvc = mvc;
		this.admin = admin;
	}

	/** A published one-year online plan paying 2% a month. */
	public UUID plan() throws Exception {
		return published(TYPE, "2");
	}

	/** A published plan for network investors. */
	public UUID networkPlan() throws Exception {
		return published("NETWORK", "2");
	}

	/** A published one-year plan; returns its id. */
	public UUID published(String type, String monthlyReturnPercent) throws Exception {
		return published(type, monthlyReturnPercent, 1);
	}

	public UUID published(String type, String monthlyReturnPercent, int tenureYears) throws Exception {
		return published(type, monthlyReturnPercent, tenureYears, 1);
	}

	public UUID published(String type, String monthlyReturnPercent, int tenureYears, int minUnits) throws Exception {
		UUID productId = draft(type, monthlyReturnPercent, tenureYears, minUnits);
		mvc.perform(post("/api/v1/admin/investment-products/{id}/publish", productId)
				.header(HttpHeaders.AUTHORIZATION, admin.bearer()))
			.andExpect(status().isOk());
		return productId;
	}

	public UUID draft(String type, String monthlyReturnPercent) throws Exception {
		return draft(type, monthlyReturnPercent, 1, 1);
	}

	public UUID draft(String type, String monthlyReturnPercent, int tenureYears, int minUnits) throws Exception {
		String product = mvc.perform(post("/api/v1/admin/investment-products").header(HttpHeaders.AUTHORIZATION, admin.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(planBody(type, monthlyReturnPercent, tenureYears, minUnits)))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(product, "$.id"));
	}

	/** The JSON an admin sends for a plan; tests think in monthly rates, so the annual return is twelve of them. */
	public static String planBody(String audience, String monthlyReturnPercent, int tenureYears, int minUnits) {
		return """
				{"audience":"%s","title":"Growth plan","summary":"Invest and earn monthly returns","currency":"INR",
				 "tenureYears":%d,"annualReturnPercent":"%s","minUnits":%d}
				""".formatted(audience, tenureYears, new java.math.BigDecimal(monthlyReturnPercent).multiply(java.math.BigDecimal.valueOf(12)),
						minUnits);
	}

}
