package com.sealease.backend.marketplace;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

	// -------------------------------------------------------------------------- plan lifecycle

	@Test
	void planLifecycleDraftPublishCloseWithFrozenTerms() throws Exception {
		String created = mvc.perform(post("/api/v1/admin/investment-products").header(HttpHeaders.AUTHORIZATION, admin.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(planBody("DRY_20FT", "300000", "2.25", 2, 5)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.code").value(matchesPattern("LST-\\d{5,}")))
			.andExpect(jsonPath("$.price.amount").value("300000.00"))
			// A lease of 2 to 5 years. No rent: that is set by each order's total value.
			.andExpect(jsonPath("$.minTenureYears").value(2))
			.andExpect(jsonPath("$.maxTenureYears").value(5))
			.andExpect(jsonPath("$.monthlyRent").doesNotExist())
			.andExpect(jsonPath("$.monthlyRentPercent").doesNotExist())
			.andReturn().getResponse().getContentAsString();
		String productId = JsonPath.read(created, "$.id");

		// Invisible to investors while in draft; editable by staff.
		Account investor = approvedInvestor();
		mvc.perform(get("/api/v1/marketplace/{id}", productId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isNotFound());
		// The lease limits are the plan's own; whatever they are, the plan pays rent only.
		update(productId, planBody("DRY_20FT", "300000", "2.5", 1, 3)).andExpect(status().isOk())
			.andExpect(jsonPath("$.minTenureYears").value(1))
			.andExpect(jsonPath("$.maxTenureYears").value(3));

		publish(productId).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OPEN"));
		publish(productId).andExpect(status().isConflict());
		update(productId, planBody("DRY_20FT", "300000", "9", 1, 3)).andExpect(status().isUnprocessableContent());
		cancel(productId).andExpect(status().isUnprocessableContent());

		mvc.perform(post("/api/v1/admin/investment-products/{id}/close", productId).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CLOSED"))
			.andExpect(jsonPath("$.closedAt").isNotEmpty());
		mvc.perform(get("/api/v1/marketplace/{id}", productId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.eligibility.eligible").value(false))
			.andExpect(jsonPath("$.eligibility.reasons", hasItem("This container is not accepting investments")));
		assertThat(api.auditCount("PRODUCT_CLOSED", productId)).isEqualTo(1);
	}

	@Test
	void draftPlansCanBeCancelledAndTenureIsBounded() throws Exception {
		mvc.perform(post("/api/v1/admin/investment-products").header(HttpHeaders.AUTHORIZATION, admin.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(planBody("DRY_20FT", "300000", "2", 0, 3)))
			.andExpect(status().isBadRequest());
		mvc.perform(post("/api/v1/admin/investment-products").header(HttpHeaders.AUTHORIZATION, admin.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(planBody("DRY_20FT", "300000", "2", 1, 31)))
			.andExpect(status().isBadRequest());
		// The longest lease cannot be shorter than the shortest.
		mvc.perform(post("/api/v1/admin/investment-products").header(HttpHeaders.AUTHORIZATION, admin.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(planBody("DRY_20FT", "300000", "2", 4, 2)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("longest lease")));
		String productId = offerings.draft("DRY_20FT", "300000", "2").toString();
		cancel(productId).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
		publish(productId).andExpect(status().isConflict());
	}

	@Test
	void publishingRequiresApprovalPermission() throws Exception {
		Account administration = api.staff("ADMINISTRATION");
		String productId = JsonPath.read(mvc.perform(post("/api/v1/admin/investment-products")
				.header(HttpHeaders.AUTHORIZATION, administration.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(planBody("DRY_20FT", "200000", "2", 1, 3)))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
		mvc.perform(post("/api/v1/admin/investment-products/{id}/publish", productId)
				.header(HttpHeaders.AUTHORIZATION, administration.bearer()))
			.andExpect(status().isForbidden());

		Account investor = approvedInvestor();
		mvc.perform(multipart("/api/v1/admin/investment-products/{id}/photos", productId)
				.file(new MockMultipartFile("file", "photo.png", "image/png", PNG)).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isForbidden());
	}

	// --------------------------------------------------------------------------- marketplace

	@Test
	void marketplaceShowsOpenPlansWithTheirOwnPhotos() throws Exception {
		String productId = offerings.published("OPEN_TOP_20FT", "180000", "2").toString();
		String sibling = offerings.published("OPEN_TOP_20FT", "190000", "2").toString();
		UUID cover = uploadPhoto(productId);
		UUID second = uploadPhoto(productId);
		UUID siblingPhoto = uploadPhoto(sibling);
		mvc.perform(multipart("/api/v1/admin/investment-products/{id}/photos", productId)
				.file(new MockMultipartFile("file", "photo.png", "image/png", "not an image".getBytes()))
				.header(HttpHeaders.AUTHORIZATION, admin.bearer()))
			.andExpect(status().isBadRequest());
		Account investor = approvedInvestor();

		mvc.perform(get("/api/v1/marketplace").param("size", "100").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[*].id", hasItem(productId)))
			.andExpect(jsonPath("$.content[?(@.id == '%s')].coverPhotoId".formatted(productId)).value(cover.toString()));
		mvc.perform(get("/api/v1/marketplace").param("containerType", "OPEN_TOP_20FT")
				.header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.content[*].containerType", not(hasItem(OfferingFixtures.TYPE))));

		String detail = mvc.perform(get("/api/v1/marketplace/{id}", productId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.photoIds", contains(cover.toString(), second.toString())))
			.andExpect(jsonPath("$.eligibility.eligible").value(true))
			.andReturn().getResponse().getContentAsString();
		assertThat(detail).doesNotContain("containerNumber");

		photo(productId, cover, investor).andExpect(status().isOk()).andExpect(content().bytes(PNG));
		photo(productId, siblingPhoto, investor).andExpect(status().isNotFound());

		// Removing the cover makes the next photo the cover.
		String photoId = JsonPath.read(mvc.perform(get("/api/v1/admin/investment-products/{id}/photos", productId)
				.header(HttpHeaders.AUTHORIZATION, admin.bearer()))
			.andExpect(jsonPath("$.length()").value(2))
			.andReturn().getResponse().getContentAsString(), "$[0].id");
		mvc.perform(delete("/api/v1/admin/investment-products/{id}/photos/{photo}", productId, photoId)
				.header(HttpHeaders.AUTHORIZATION, admin.bearer()))
			.andExpect(status().isNoContent());
		mvc.perform(get("/api/v1/marketplace/{id}", productId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.photoIds", contains(second.toString())))
			.andExpect(jsonPath("$.listing.coverPhotoId").value(second.toString()));
		photo(productId, cover, investor).andExpect(status().isNotFound());
		assertThat(api.auditCount("PLAN_PHOTO_ADDED", productId)).isEqualTo(2);
	}

	@Test
	void eligibilityExplainsWhyAnInvestorCannotInvest() throws Exception {
		UUID product = offerings.plan();
		Account unverified = api.register();
		mvc.perform(get("/api/v1/marketplace/{id}", product).header(HttpHeaders.AUTHORIZATION, unverified.bearer()))
			.andExpect(jsonPath("$.eligibility.eligible").value(false))
			.andExpect(jsonPath("$.eligibility.reasons", hasItem("Verify your identity before investing")));

		// Verified identity alone is not enough: a verified bank account is needed too, also to add to the cart.
		Account noBankAccount = api.register();
		jdbc.update("UPDATE user_profiles SET kyc_status = 'APPROVED' WHERE user_id = ?", noBankAccount.id());
		mvc.perform(get("/api/v1/marketplace/{id}", product).header(HttpHeaders.AUTHORIZATION, noBankAccount.bearer()))
			.andExpect(jsonPath("$.eligibility.eligible").value(false))
			.andExpect(jsonPath("$.eligibility.reasons", contains("Add a bank account and have it verified before investing")));
		mvc.perform(put("/api/v1/cart/items/{id}", product).header(HttpHeaders.AUTHORIZATION, noBankAccount.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
			.andExpect(status().isUnprocessableContent());

		Account investor = approvedInvestor();
		mvc.perform(get("/api/v1/marketplace/{id}", product).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.eligibility.eligible").value(true));
	}

	@Test
	void projectionShowsWhatSeveralContainersCostAndPay() throws Exception {
		UUID product = offerings.plan();
		Account investor = approvedInvestor();

		// 3 × ₹50,000 = ₹1,50,000, an order that earns 5% rent a month on each container. Without a lease chosen, the plan's shortest: 1 year.
		mvc.perform(get("/api/v1/marketplace/{id}/projection", product).param("containers", "3")
				.header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.valid").value(true))
			.andExpect(jsonPath("$.tenureYears").value(1))
			.andExpect(jsonPath("$.minTenureYears").value(1))
			.andExpect(jsonPath("$.maxTenureYears").value(5))
			.andExpect(jsonPath("$.amount.amount").value("150000.00"))
			.andExpect(jsonPath("$.monthlyRentPercent").value(5))
			.andExpect(jsonPath("$.monthlyRent.amount").value("7500.00"))
			.andExpect(jsonPath("$.payouts").value(12))
			.andExpect(jsonPath("$.totalRent.amount").value("90000.00"));
		// The investor chooses a longer lease: 3 years of rent.
		mvc.perform(get("/api/v1/marketplace/{id}/projection", product).param("containers", "3").param("years", "3")
				.header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.valid").value(true))
			.andExpect(jsonPath("$.payouts").value(36))
			.andExpect(jsonPath("$.totalRent.amount").value("270000.00"));
		mvc.perform(get("/api/v1/marketplace/{id}/projection", product).param("containers", "3").param("years", "6")
				.header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.valid").value(false))
			.andExpect(jsonPath("$.problems[0]").value("Choose a lease of 1 to 5 years"));

		mvc.perform(get("/api/v1/marketplace/{id}/projection", product).param("containers", "0")
				.header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.valid").value(false))
			.andExpect(jsonPath("$.problems[0]").value("Buy at least one container"));
	}

	@Test
	void theWebsiteListsOpenPlansWithoutSigningIn() throws Exception {
		offerings.published("DRY_40FT", "300000", "2", 2, 5);
		offerings.draft("DRY_40FT", "999000", "2");
		mvc.perform(get("/api/v1/public/plans"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.lessThanOrEqualTo(6)))
			// Newest first: the plan just published, with its terms per container and nothing else.
			.andExpect(jsonPath("$[0].containerType").value("DRY_40FT"))
			.andExpect(jsonPath("$[0].containerTypeLabel").value("40ft dry"))
			.andExpect(jsonPath("$[0].price.amount").value("300000.00"))
			.andExpect(jsonPath("$[0].monthlyRent").doesNotExist())
			.andExpect(jsonPath("$[0].minTenureYears").value(2))
			.andExpect(jsonPath("$[0].maxTenureYears").value(5))
			.andExpect(jsonPath("$[0].id").doesNotExist())
			.andExpect(jsonPath("$[*].price.amount", not(hasItem("999000.00"))));
		mvc.perform(post("/api/v1/public/plans")).andExpect(status().isUnauthorized());
	}

	@Test
	void unnamedListingsAreNamedAfterTheirContainerType() throws Exception {
		mvc.perform(post("/api/v1/admin/investment-products").header(HttpHeaders.AUTHORIZATION, admin.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(planBody("HIGH_CUBE_40FT", "300000", "2", 1, 3)
					.replace("\"title\":\"20ft dry container plan\",", "")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.title").value("40ft High Cube container"));
	}

	@Test
	void rentFollowsTheOrderValue() throws Exception {
		// Minimum order ₹1,00,000; 5% from ₹1,00,000, 5.5% from ₹5,00,000, 6% from ₹10,00,000.
		jdbc.update("UPDATE order_settings SET min_order_amount = 100000 WHERE id = 1");
		setTiers("""
				{"tiers":[{"fromAmount":"100000","toAmount":"499999","monthlyRentPercent":"5"},
				          {"fromAmount":"500000","toAmount":"999999","monthlyRentPercent":"5.5"},
				          {"fromAmount":"1000000","monthlyRentPercent":"6"}]}""").andExpect(status().isOk())
			.andExpect(jsonPath("$.tiers[0].fromAmount.amount").value("100000.00"))
			.andExpect(jsonPath("$.tiers[0].toAmount.amount").value("499999.00"))
			.andExpect(jsonPath("$.tiers[2].toAmount").doesNotExist());
		// Bands follow each other: no gap, no overlap, and only the last is open-ended.
		setTiers("""
				{"tiers":[{"fromAmount":"100000","toAmount":"400000","monthlyRentPercent":"5"},
				          {"fromAmount":"500000","monthlyRentPercent":"6"}]}""").andExpect(status().isBadRequest());
		setTiers("""
				{"tiers":[{"fromAmount":"100000","monthlyRentPercent":"5"},
				          {"fromAmount":"500000","monthlyRentPercent":"6"}]}""").andExpect(status().isBadRequest());
		try {
			String plan = offerings.published(OfferingFixtures.TYPE, "50000", "0", 1, 20).toString();
			Account investor = approvedInvestor();
			mvc.perform(get("/api/v1/marketplace/{id}", plan).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
				.andExpect(jsonPath("$.listing.monthlyRentPercent").doesNotExist());
			// One ₹50,000 container is below the minimum order.
			mvc.perform(get("/api/v1/marketplace/{id}/projection", plan).param("containers", "1")
					.header(HttpHeaders.AUTHORIZATION, investor.bearer()))
				.andExpect(jsonPath("$.problems[0]").value(org.hamcrest.Matchers.startsWith("The minimum order is")));
			// ₹1,00,000 earns 5% (₹5,000 a month); ₹5,00,000 earns 5.5%; ₹10,00,000 earns 6% (₹60,000 a month).
			for (String[] c : new String[][] { { "2", "5", "5000.00" }, { "10", "5.5", "27500.00" }, { "20", "6", "60000.00" } }) {
				mvc.perform(get("/api/v1/marketplace/{id}/projection", plan).param("containers", c[0])
						.header(HttpHeaders.AUTHORIZATION, investor.bearer()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.problems").isEmpty())
					.andExpect(jsonPath("$.monthlyRentPercent").value(Double.parseDouble(c[1])))
					.andExpect(jsonPath("$.monthlyRent.amount").value(c[2]));
			}
		}
		finally {
			jdbc.update("UPDATE order_settings SET min_order_amount = 0 WHERE id = 1");
			setTiers("""
					{"tiers":[{"fromAmount":"0","toAmount":"99999","monthlyRentPercent":"2"},
					          {"fromAmount":"100000","monthlyRentPercent":"5"}]}""")
				.andExpect(status().isOk());
		}
	}

	private ResultActions setTiers(String body) throws Exception {
		return mvc.perform(put("/api/v1/admin/settings/rent-tiers").header(HttpHeaders.AUTHORIZATION, admin.bearer())
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	// ------------------------------------------------------------------------------ helpers

	/** Identity verified and a verified bank account: able to buy. */
	private Account approvedInvestor() throws Exception {
		Account investor = api.register();
		jdbc.update("UPDATE user_profiles SET kyc_status = 'APPROVED' WHERE user_id = ?", investor.id());
		new InvestorFixtures(mvc, jdbc, api).ensureVerifiedBankAccount(investor);
		return investor;
	}

	/** Uploads a PNG photo to the plan as the admin; returns its document id. */
	private UUID uploadPhoto(String productId) throws Exception {
		String body = mvc.perform(multipart("/api/v1/admin/investment-products/{id}/photos", productId)
				.file(new MockMultipartFile("file", "photo.png", "image/png", PNG))
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

	private static String planBody(String type, String price, String rentPercent, int minYears, int maxYears) {
		return """
				{"containerType":"%s","title":"20ft dry container plan","summary":"Own a container, earn monthly",
				 "description":"Buy a container and lease it out.","currency":"INR","price":"%s",
				 "monthlyRentPercent":"%s","minTenureYears":%d,"maxTenureYears":%d}
				""".formatted(type, price, rentPercent, minYears, maxYears);
	}

}
