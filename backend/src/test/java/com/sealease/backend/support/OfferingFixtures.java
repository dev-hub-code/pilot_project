package com.sealease.backend.support;

import com.jayway.jsonpath.JsonPath;
import com.sealease.backend.container.service.ContainerNumbers;
import com.sealease.backend.support.TestApi.Account;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Publishes plans through the admin API, for tests that need something to invest in, and makes up container numbers
 * for finance to assign. Tests share one database, so use a fresh number rather than one another test may hold.
 */
public final class OfferingFixtures {

	public static final String TYPE = "DRY_40FT";

	private final MockMvc mvc;
	private final Account admin;

	public OfferingFixtures(MockMvc mvc, Account admin) {
		this.mvc = mvc;
		this.admin = admin;
	}

	/** A published 40ft dry plan at ₹50,000 a container, 2% rent a month, leased for 1 to 5 years (1 by default). */
	public UUID plan() throws Exception {
		return published(TYPE, "50000", "2");
	}

	/** A new, valid ISO 6346 container number (owner code SLSU, random serial, correct check digit). */
	public static String containerNumber() {
		return ContainerNumbers.withCheckDigit("SLSU" + ThreadLocalRandom.current().nextInt(100000, 1000000));
	}

	/** A published plan; returns its id. */
	public UUID published(String type, String price, String monthlyRentPercent) throws Exception {
		UUID productId = draft(type, price, monthlyRentPercent);
		mvc.perform(post("/api/v1/admin/investment-products/{id}/publish", productId)
				.header(HttpHeaders.AUTHORIZATION, admin.bearer()))
			.andExpect(status().isOk());
		return productId;
	}

	public UUID draft(String type, String price, String monthlyRentPercent) throws Exception {
		return draft(type, price, monthlyRentPercent, 1, 5);
	}

	public UUID published(String type, String price, String monthlyRentPercent, int minTenureYears, int maxTenureYears)
			throws Exception {
		UUID productId = draft(type, price, monthlyRentPercent, minTenureYears, maxTenureYears);
		mvc.perform(post("/api/v1/admin/investment-products/{id}/publish", productId)
				.header(HttpHeaders.AUTHORIZATION, admin.bearer()))
			.andExpect(status().isOk());
		return productId;
	}

	public UUID draft(String type, String price, String monthlyRentPercent, int minTenureYears, int maxTenureYears)
			throws Exception {
		String product = mvc.perform(post("/api/v1/admin/investment-products").header(HttpHeaders.AUTHORIZATION, admin.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"containerType":"%s","title":"40ft dry container plan","summary":"Own a container, earn rent",
						 "description":"Buy a cargo-worthy container and lease it out.","currency":"INR","price":"%s",
						 "monthlyRentPercent":"%s","minTenureYears":%d,"maxTenureYears":%d,
						 "riskDisclosure":"Payouts depend on the platform.","termsAndConditions":"Standard terms."}
						""".formatted(type, price, monthlyRentPercent, minTenureYears, maxTenureYears)))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(product, "$.id"));
	}


}
