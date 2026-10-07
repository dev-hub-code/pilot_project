package com.sealease.backend.support;

import com.jayway.jsonpath.JsonPath;
import com.sealease.backend.container.service.ContainerNumbers;
import com.sealease.backend.support.TestApi.Account;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Registers containers and publishes plans through the admin API, for tests that need something to
 * invest in. Tests share one database, so containers of a type registered by one test may be
 * allocated in another; assert on the containers of your own orders, not on inventory counts.
 */
public final class OfferingFixtures {

	public static final String TYPE = "DRY_40FT";
	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1, 2, 3, 4 };

	private final MockMvc mvc;
	private final Account admin;

	public OfferingFixtures(MockMvc mvc, Account admin) {
		this.mvc = mvc;
		this.admin = admin;
	}

	/** A published 40ft dry plan at ₹50,000 a container, 2% rent a month over 16 months, with {@code containers} in stock. */
	public UUID plan(int containers) throws Exception {
		containers(TYPE, containers);
		return published(TYPE, "50000", "2");
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
		return draft(type, price, monthlyRentPercent, 16);
	}

	public UUID published(String type, String price, String monthlyRentPercent, int tenureMonths) throws Exception {
		UUID productId = draft(type, price, monthlyRentPercent, tenureMonths);
		mvc.perform(post("/api/v1/admin/investment-products/{id}/publish", productId)
				.header(HttpHeaders.AUTHORIZATION, admin.bearer()))
			.andExpect(status().isOk());
		return productId;
	}

	public UUID draft(String type, String price, String monthlyRentPercent, int tenureMonths) throws Exception {
		String product = mvc.perform(post("/api/v1/admin/investment-products").header(HttpHeaders.AUTHORIZATION, admin.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"containerType":"%s","title":"40ft dry container plan","summary":"Own a container, earn rent",
						 "description":"Buy a cargo-worthy container and lease it out.","currency":"INR","price":"%s",
						 "monthlyRentPercent":"%s","tenureMonths":%d,
						 "riskDisclosure":"Payouts depend on the platform.","termsAndConditions":"Standard terms."}
						""".formatted(type, price, monthlyRentPercent, tenureMonths)))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(product, "$.id"));
	}

	/** Registers available containers of the type; returns their ids. The first gets an investor-visible photo. */
	public List<UUID> containers(String type, int count) throws Exception {
		List<UUID> ids = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			ids.add(container(type));
		}
		if (!ids.isEmpty()) {
			mvc.perform(multipart("/api/v1/admin/containers/{id}/documents", ids.getFirst())
					.file(new MockMultipartFile("file", "photo.png", "image/png", PNG))
					.param("purpose", "CONTAINER_PHOTO").param("title", "Side view").param("visibleToInvestors", "true")
					.header(HttpHeaders.AUTHORIZATION, admin.bearer()))
				.andExpect(status().isCreated());
		}
		return ids;
	}

	private UUID container(String type) throws Exception {
		String number = ContainerNumbers.withCheckDigit("SLSU" + ThreadLocalRandom.current().nextInt(100000, 1000000));
		String body = mvc.perform(post("/api/v1/admin/containers").header(HttpHeaders.AUTHORIZATION, admin.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"containerNumber":"%s","containerType":"%s","condition":"CARGO_WORTHY","capacityCbm":67.7,
						 "maxGrossKg":30480,"tareKg":3750,"manufactureYear":2022,"currentLocation":"Nhava Sheva, Mumbai",
						 "locationCountry":"IN"}
						""".formatted(number, type)))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(body, "$.container.id"));
	}

}
