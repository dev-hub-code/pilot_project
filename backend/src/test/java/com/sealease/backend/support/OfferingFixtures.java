package com.sealease.backend.support;

import com.jayway.jsonpath.JsonPath;
import com.sealease.backend.container.service.ContainerNumbers;
import com.sealease.backend.support.TestApi.Account;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Publishes offerings through the admin API, for tests that need something to invest in. */
public final class OfferingFixtures {

	public static final String TERMS_VERSION = "2026.1";
	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1, 2, 3, 4 };

	private final MockMvc mvc;
	private final Account admin;

	public OfferingFixtures(MockMvc mvc, Account admin) {
		this.mvc = mvc;
		this.admin = admin;
	}

	/** A published offering; returns its id. Rental is per month, over 36 months, without a fee. */
	public UUID published(String type, String total, String minimum, String increment, String rental) throws Exception {
		return published(type, total, minimum, increment, rental, 36, "0");
	}

	/** A published offering with monthly rental over {@code durationMonths} and a management fee. */
	public UUID published(String type, String total, String minimum, String increment, String rental,
			int durationMonths, String feePercent) throws Exception {
		UUID containerId = container();
		mvc.perform(multipart("/api/v1/admin/containers/{id}/documents", containerId)
				.file(new MockMultipartFile("file", "photo.png", "image/png", PNG))
				.param("purpose", "CONTAINER_PHOTO").param("title", "Side view").param("visibleToInvestors", "true")
				.header(HttpHeaders.AUTHORIZATION, admin.bearer()))
			.andExpect(status().isCreated());
		String product = mvc.perform(post("/api/v1/admin/investment-products").header(HttpHeaders.AUTHORIZATION, admin.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"containerId":"%s","investmentType":"%s","title":"40ft dry container on lease","summary":"Leased",
						 "description":"A cargo-worthy 40ft container.","currency":"USD","totalAmount":"%s",
						 "minimumInvestment":"%s","investmentIncrement":"%s","expectedRentalAmount":"%s",
						 "rentalFrequency":"MONTHLY","durationMonths":%d,"managementFeePercent":"%s","riskLevel":"MEDIUM",
						 "riskDisclosure":"Rental income depends on the lessee.","termsAndConditions":"Standard terms.",
						 "termsVersion":"%s","offerClosesAt":"%s"}
						""".formatted(containerId, type, total, minimum, increment, rental, durationMonths, feePercent,
						TERMS_VERSION,
						Instant.now().plus(60, ChronoUnit.DAYS))))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		UUID productId = UUID.fromString(JsonPath.read(product, "$.id"));
		mvc.perform(post("/api/v1/admin/investment-products/{id}/publish", productId)
				.header(HttpHeaders.AUTHORIZATION, admin.bearer()))
			.andExpect(status().isOk());
		return productId;
	}

	public UUID retail(String total, String minimum, String increment) throws Exception {
		return published("RETAIL", total, minimum, increment, "1500");
	}

	private UUID container() throws Exception {
		String number = ContainerNumbers.withCheckDigit("SLSU" + ThreadLocalRandom.current().nextInt(100000, 1000000));
		String body = mvc.perform(post("/api/v1/admin/containers").header(HttpHeaders.AUTHORIZATION, admin.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"containerNumber":"%s","containerType":"DRY_40FT","condition":"CARGO_WORTHY","capacityCbm":67.7,
						 "maxGrossKg":30480,"tareKg":3750,"manufactureYear":2022,"currentLocation":"Port of Rotterdam",
						 "locationCountry":"NL"}
						""".formatted(number)))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(body, "$.container.id"));
	}

}
