package com.sealease.backend.marketplace;

import com.jayway.jsonpath.JsonPath;
import com.sealease.backend.container.service.ContainerNumbers;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Containers, investment plans and their lifecycle, marketplace visibility, eligibility and projections. */
@IntegrationTest
class MarketplaceIntegrationTest {

	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1, 2, 3, 4 };

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	private TestApi api;
	private Account admin;
	private OfferingFixtures offerings;

	@BeforeEach
	void setUp() throws Exception {
		api = new TestApi(mvc, jdbc);
		admin = api.admin();
		offerings = new OfferingFixtures(mvc, admin);
	}

	// -------------------------------------------------------------------------- containers

	@Test
	void containerRegistrationValidatesIdentityAndPhysics() throws Exception {
		String number = randomContainerNumber();
		String wrongCheckDigit = number.substring(0, 10) + ((number.charAt(10) - '0' + 1) % 10);

		createContainer(wrongCheckDigit, 2300, 30480, admin).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(containsString("ISO 6346")));
		createContainer(number, 31000, 30480, admin).andExpect(status().isBadRequest());
		createContainer(number.toLowerCase(), 2300, 30480, admin).andExpect(status().isCreated())
			.andExpect(jsonPath("$.container.containerNumber").value(number))
			.andExpect(jsonPath("$.container.status").value("AVAILABLE"));
		createContainer(number, 2300, 30480, admin).andExpect(status().isConflict());
	}

	@Test
	void reservedAndLeasedContainersChangeStatusOnlyThroughOrders() throws Exception {
		UUID product = offerings.plan(1);
		Account investor = approvedInvestor();
		mvc.perform(put("/api/v1/cart/items/{id}", product).header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
			.andExpect(status().isOk());
		String orderId = JsonPath.read(mvc.perform(post("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"acceptedTerms":["%s"]}""".formatted(product)))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
		UUID reserved = jdbc.queryForObject("SELECT container_id FROM order_items WHERE order_id = ?::uuid", UUID.class, orderId);

		changeStatus(reserved, "RETIRED").andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.message").value(containsString("reserved for an order")));
		UUID spare = offerings.containers(OfferingFixtures.TYPE, 1).getFirst();
		changeStatus(spare, "ON_LEASE").andExpect(status().isUnprocessableContent());
		changeStatus(spare, "MAINTENANCE").andExpect(status().isOk())
			.andExpect(jsonPath("$.container.status").value("MAINTENANCE"));
	}

	// -------------------------------------------------------------------------- plan lifecycle

	@Test
	void planLifecycleDraftPublishCloseWithFrozenTerms() throws Exception {
		String created = mvc.perform(post("/api/v1/admin/investment-products").header(HttpHeaders.AUTHORIZATION, admin.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(planBody("DRY_20FT", "300000", "2.25", 16)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.code").value(matchesPattern("PLAN-\\d{5,}")))
			.andExpect(jsonPath("$.price.amount").value("300000.00"))
			// 16 months: 100/16 = 6.25% of the price back each month on top of 2.25% rent.
			.andExpect(jsonPath("$.tenureMonths").value(16))
			.andExpect(jsonPath("$.monthlyCapitalReturnPercent").value(6.25))
			.andExpect(jsonPath("$.monthlyPayoutPercent").value(8.5))
			.andExpect(jsonPath("$.monthlyRent.amount").value("6750.00"))
			.andExpect(jsonPath("$.monthlyCapitalReturn.amount").value("18750.00"))
			.andExpect(jsonPath("$.monthlyPayout.amount").value("25500.00"))
			.andExpect(jsonPath("$.totalPayout.amount").value("408000.00"))
			.andReturn().getResponse().getContentAsString();
		String productId = JsonPath.read(created, "$.id");

		// Invisible to investors while in draft; editable by staff.
		Account investor = approvedInvestor();
		mvc.perform(get("/api/v1/marketplace/{id}", productId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isNotFound());
		// The tenure is the plan's own: 12 months returns 100/12 = 8.3333% a month.
		update(productId, planBody("DRY_20FT", "300000", "2.5", 12)).andExpect(status().isOk())
			.andExpect(jsonPath("$.monthlyRentPercent").value(2.5))
			.andExpect(jsonPath("$.tenureMonths").value(12))
			.andExpect(jsonPath("$.monthlyCapitalReturnPercent").value(8.3333))
			.andExpect(jsonPath("$.monthlyCapitalReturn.amount").value("25000.00"));

		publish(productId).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OPEN"));
		publish(productId).andExpect(status().isConflict());
		update(productId, planBody("DRY_20FT", "300000", "9", 12)).andExpect(status().isUnprocessableContent());
		cancel(productId).andExpect(status().isUnprocessableContent());

		mvc.perform(post("/api/v1/admin/investment-products/{id}/close", productId).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CLOSED"))
			.andExpect(jsonPath("$.closedAt").isNotEmpty());
		mvc.perform(get("/api/v1/marketplace/{id}", productId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.eligibility.eligible").value(false))
			.andExpect(jsonPath("$.eligibility.reasons", hasItem("This plan is not accepting investments")));
		assertThat(api.auditCount("PRODUCT_CLOSED", productId)).isEqualTo(1);
	}

	@Test
	void draftPlansCanBeCancelledAndRentAndTenureAreBounded() throws Exception {
		mvc.perform(post("/api/v1/admin/investment-products").header(HttpHeaders.AUTHORIZATION, admin.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(planBody("DRY_20FT", "300000", "25", 16)))
			.andExpect(status().isBadRequest());
		mvc.perform(post("/api/v1/admin/investment-products").header(HttpHeaders.AUTHORIZATION, admin.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(planBody("DRY_20FT", "300000", "2", 0)))
			.andExpect(status().isBadRequest());
		String productId = offerings.draft("DRY_20FT", "300000", "2").toString();
		cancel(productId).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
		publish(productId).andExpect(status().isConflict());
	}

	@Test
	void publishingRequiresApprovalPermission() throws Exception {
		Account administration = api.staff("ADMINISTRATION");
		String productId = JsonPath.read(mvc.perform(post("/api/v1/admin/investment-products")
				.header(HttpHeaders.AUTHORIZATION, administration.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(planBody("DRY_20FT", "200000", "2", 16)))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
		mvc.perform(post("/api/v1/admin/investment-products/{id}/publish", productId)
				.header(HttpHeaders.AUTHORIZATION, administration.bearer()))
			.andExpect(status().isForbidden());

		Account investor = approvedInvestor();
		createContainer(randomContainerNumber(), 2300, 30480, investor).andExpect(status().isForbidden());
	}

	// --------------------------------------------------------------------------- marketplace

	@Test
	void marketplaceShowsOpenPlansWithPhotosOfTheirContainerType() throws Exception {
		// The only test using 20ft open tops, so their photos are known.
		UUID container = offerings.containers("OPEN_TOP_20FT", 2).getFirst();
		UUID photo = jdbc.queryForObject("SELECT document_id FROM container_documents WHERE container_id = ?", UUID.class, container);
		UUID hidden = uploadPhoto(container, false);
		UUID otherType = uploadPhoto(offerings.containers(OfferingFixtures.TYPE, 1).getFirst(), true);
		String productId = offerings.published("OPEN_TOP_20FT", "180000", "2").toString();
		Account investor = approvedInvestor();

		mvc.perform(get("/api/v1/marketplace").param("size", "100").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[*].id", hasItem(productId)))
			.andExpect(jsonPath("$.content[?(@.id == '%s')].availableContainers".formatted(productId)).value(2))
			.andExpect(jsonPath("$.content[?(@.id == '%s')].coverPhotoId".formatted(productId)).value(photo.toString()));
		mvc.perform(get("/api/v1/marketplace").param("containerType", "OPEN_TOP_20FT")
				.header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.content[*].containerType", not(hasItem(OfferingFixtures.TYPE))));

		String detail = mvc.perform(get("/api/v1/marketplace/{id}", productId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.photoIds", hasItem(photo.toString())))
			.andExpect(jsonPath("$.photoIds", not(hasItem(hidden.toString()))))
			.andExpect(jsonPath("$.eligibility.eligible").value(true))
			.andReturn().getResponse().getContentAsString();
		assertThat(detail).doesNotContain("acquisitionCost").doesNotContain("containerNumber");

		photo(productId, photo, investor).andExpect(status().isOk()).andExpect(content().bytes(PNG));
		photo(productId, hidden, investor).andExpect(status().isNotFound());
		photo(productId, otherType, investor).andExpect(status().isNotFound());
	}

	@Test
	void eligibilityExplainsWhyAnInvestorCannotInvest() throws Exception {
		UUID product = offerings.plan(1);
		Account unverified = api.register();
		mvc.perform(get("/api/v1/marketplace/{id}", product).header(HttpHeaders.AUTHORIZATION, unverified.bearer()))
			.andExpect(jsonPath("$.eligibility.eligible").value(false))
			.andExpect(jsonPath("$.eligibility.reasons", hasItem("Verify your identity before investing")));
		Account investor = approvedInvestor();
		mvc.perform(get("/api/v1/marketplace/{id}", product).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.eligibility.eligible").value(true));
	}

	@Test
	void projectionShowsWhatSeveralContainersCostAndPay() throws Exception {
		UUID product = offerings.plan(3);
		Account investor = approvedInvestor();

		// 3 × ₹50,000; each pays 2% rent + 6.25% capital a month for 16 months: the price comes back in full.
		mvc.perform(get("/api/v1/marketplace/{id}/projection", product).param("containers", "3")
				.header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.valid").value(true))
			.andExpect(jsonPath("$.amount.amount").value("150000.00"))
			.andExpect(jsonPath("$.monthlyRent.amount").value("3000.00"))
			.andExpect(jsonPath("$.monthlyCapitalReturn.amount").value("9375.00"))
			.andExpect(jsonPath("$.monthlyPayout.amount").value("12375.00"))
			.andExpect(jsonPath("$.payouts").value(16))
			.andExpect(jsonPath("$.totalRent.amount").value("48000.00"))
			.andExpect(jsonPath("$.totalCapitalReturned.amount").value("150000.00"))
			.andExpect(jsonPath("$.totalPayout.amount").value("198000.00"));

		mvc.perform(get("/api/v1/marketplace/{id}/projection", product).param("containers", "0")
				.header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.valid").value(false))
			.andExpect(jsonPath("$.problems[0]").value("Buy at least one container"));
	}

	@Test
	void sortsByMonthlyRent() throws Exception {
		String low = offerings.published(OfferingFixtures.TYPE, "50000", "1").toString();
		String high = offerings.published(OfferingFixtures.TYPE, "50000", "3").toString();
		Account investor = approvedInvestor();
		List<String> ids = JsonPath.read(mvc.perform(get("/api/v1/marketplace").param("sort", "HIGHEST_RETURN").param("size", "100")
				.header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andReturn().getResponse().getContentAsString(), "$.content[*].id");
		assertThat(ids.indexOf(high)).isLessThan(ids.indexOf(low));
	}

	// ------------------------------------------------------------------------------ helpers

	private Account approvedInvestor() throws Exception {
		Account investor = api.register();
		jdbc.update("UPDATE user_profiles SET kyc_status = 'APPROVED' WHERE user_id = ?", investor.id());
		return investor;
	}

	private ResultActions createContainer(String number, int tare, int maxGross, Account actor) throws Exception {
		return mvc.perform(post("/api/v1/admin/containers").header(HttpHeaders.AUTHORIZATION, actor.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("""
					{"containerNumber":"%s","containerType":"DRY_40FT","condition":"CARGO_WORTHY","capacityCbm":67.7,
					 "maxGrossKg":%d,"tareKg":%d,"manufactureYear":2022,"manufacturer":"CIMC",
					 "currentLocation":"Nhava Sheva, Mumbai","locationCountry":"IN",
					 "acquisitionCost":420000,"acquisitionCurrency":"INR"}
					""".formatted(number, maxGross, tare)));
	}

	private ResultActions changeStatus(UUID containerId, String status) throws Exception {
		return mvc.perform(post("/api/v1/admin/containers/{id}/status", containerId).header(HttpHeaders.AUTHORIZATION, admin.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + status + "\",\"reason\":\"Inspection\"}"));
	}

	private UUID uploadPhoto(UUID containerId, boolean visible) throws Exception {
		String body = mvc.perform(multipart("/api/v1/admin/containers/{id}/documents", containerId)
				.file(new MockMultipartFile("file", "photo.png", "image/png", PNG))
				.param("purpose", "CONTAINER_PHOTO").param("title", "Side view")
				.param("visibleToInvestors", String.valueOf(visible))
				.header(HttpHeaders.AUTHORIZATION, admin.bearer()))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(body, "$.documentId"));
	}

	private ResultActions photo(String productId, UUID documentId, Account viewer) throws Exception {
		return mvc.perform(get("/api/v1/marketplace/{id}/documents/{doc}", productId, documentId)
			.header(HttpHeaders.AUTHORIZATION, viewer.bearer()));
	}

	private ResultActions update(String productId, String body) throws Exception {
		return mvc.perform(put("/api/v1/admin/investment-products/{id}", productId).header(HttpHeaders.AUTHORIZATION, admin.bearer())
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions publish(String productId) throws Exception {
		return mvc.perform(post("/api/v1/admin/investment-products/{id}/publish", productId)
			.header(HttpHeaders.AUTHORIZATION, admin.bearer()));
	}

	private ResultActions cancel(String productId) throws Exception {
		return mvc.perform(post("/api/v1/admin/investment-products/{id}/cancel", productId)
			.header(HttpHeaders.AUTHORIZATION, admin.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Not going ahead\"}"));
	}

	private static String planBody(String type, String price, String rentPercent, int tenureMonths) {
		return """
				{"containerType":"%s","title":"20ft dry container plan","summary":"Own a container, earn monthly",
				 "description":"Buy a container and lease it out for 16 months.","currency":"INR","price":"%s",
				 "monthlyRentPercent":"%s","tenureMonths":%d,
				 "riskDisclosure":"Payouts depend on the platform.","termsAndConditions":"Standard terms."}
				""".formatted(type, price, rentPercent, tenureMonths);
	}

	private static String randomContainerNumber() {
		return ContainerNumbers.withCheckDigit("SLSU" + ThreadLocalRandom.current().nextInt(100000, 1000000));
	}

}
