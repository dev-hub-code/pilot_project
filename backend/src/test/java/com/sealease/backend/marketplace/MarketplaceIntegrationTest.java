package com.sealease.backend.marketplace;

import com.jayway.jsonpath.JsonPath;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.money.Money;
import com.sealease.backend.container.service.ContainerNumbers;
import com.sealease.backend.investment.dto.CapacityView;
import com.sealease.backend.investment.service.CapacityService;
import com.sealease.backend.support.IntegrationTest;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase 4: containers, offerings, marketplace visibility, eligibility and capacity integrity. */
@IntegrationTest
class MarketplaceIntegrationTest {

	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1, 2, 3, 4 };

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private CapacityService capacity;

	private TestApi api;
	private Account admin;

	@BeforeEach
	void setUp() throws Exception {
		api = new TestApi(mvc, jdbc);
		admin = api.admin();
	}

	// -------------------------------------------------------------------------- containers

	@Test
	void containerRegistrationValidatesIdentityAndPhysics() throws Exception {
		String number = randomContainerNumber();
		String wrongCheckDigit = number.substring(0, 10) + ((number.charAt(10) - '0' + 1) % 10);

		createContainer(wrongCheckDigit, 2300, 30480).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("ISO 6346")));
		createContainer(number, 31000, 30480).andExpect(status().isBadRequest());
		createContainer(number.toLowerCase(), 2300, 30480).andExpect(status().isCreated())
			.andExpect(jsonPath("$.container.containerNumber").value(number));
		createContainer(number, 2300, 30480).andExpect(status().isConflict());
	}

	@Test
	void containerBackingALiveOfferingCannotBeRetired() throws Exception {
		Offering offering = publishedRetailOffering("10000", "1000", "1000");
		mvc.perform(post("/api/v1/admin/containers/{id}/status", offering.containerId()).header(HttpHeaders.AUTHORIZATION, admin.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RETIRED\",\"reason\":\"Damaged\"}"))
			.andExpect(status().isUnprocessableContent());
	}

	// ---------------------------------------------------------------------- offering lifecycle

	@Test
	void offeringLifecycleDraftPublishAndFrozenTerms() throws Exception {
		UUID containerId = newContainer();
		String body = productBody(containerId, "RETAIL", "50000", "1000", "500", "1500");
		String created = mvc.perform(post("/api/v1/admin/investment-products").header(HttpHeaders.AUTHORIZATION, admin.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.matchesPattern("CONT-\\d{5,}")))
			.andExpect(jsonPath("$.price.amount").value("50000.00"))
			.andExpect(jsonPath("$.expectedAnnualReturnPercent").value(36.00))
			.andExpect(jsonPath("$.capacity.available.amount").value("50000.00"))
			.andReturn().getResponse().getContentAsString();
		String productId = JsonPath.read(created, "$.id");

		// Not visible to investors while in draft.
		Account investor = approvedInvestor();
		mvc.perform(get("/api/v1/marketplace/{id}", productId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isNotFound());

		// A container backs one live offering at a time.
		mvc.perform(post("/api/v1/admin/investment-products").header(HttpHeaders.AUTHORIZATION, admin.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isConflict());

		publish(productId).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("photo")));
		uploadPhoto(containerId, true);
		publish(productId).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OPEN"));
		publish(productId).andExpect(status().isConflict());

		mvc.perform(put("/api/v1/admin/investment-products/{id}", productId).header(HttpHeaders.AUTHORIZATION, admin.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(body.replace("\"1500\"", "\"9000\"")))
			.andExpect(status().isUnprocessableContent());
		assertThat(api.auditCount("PRODUCT_PUBLISHED", productId)).isEqualTo(1);
	}

	@Test
	void standaloneOfferingsAreAlwaysWholeContainer() throws Exception {
		UUID containerId = newContainer();
		mvc.perform(post("/api/v1/admin/investment-products").header(HttpHeaders.AUTHORIZATION, admin.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(productBody(containerId, "HNI", "75000", "100", "100", "2000")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.minimumInvestment.amount").value("75000.00"))
			.andExpect(jsonPath("$.investmentIncrement.amount").value("75000.00"));
	}

	@Test
	void publishingRequiresApprovalPermission() throws Exception {
		Account administration = api.staff("ADMINISTRATION");
		UUID containerId = newContainer();
		String productId = JsonPath.read(mvc.perform(post("/api/v1/admin/investment-products")
				.header(HttpHeaders.AUTHORIZATION, administration.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(productBody(containerId, "RETAIL", "20000", "1000", "1000", "500")))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
		mvc.perform(post("/api/v1/admin/investment-products/{id}/publish", productId)
				.header(HttpHeaders.AUTHORIZATION, administration.bearer()))
			.andExpect(status().isForbidden());

		Account investor = approvedInvestor();
		createContainer(randomContainerNumber(), 2300, 30480, investor).andExpect(status().isForbidden());
	}

	// --------------------------------------------------------------------------- marketplace

	@Test
	void marketplaceShowsOnlyListedOfferingsAndVisibleDocuments() throws Exception {
		Offering offering = publishedRetailOffering("50000", "1000", "500");
		UUID hiddenDoc = uploadPhoto(offering.containerId(), false);
		Account investor = approvedInvestor();

		mvc.perform(get("/api/v1/marketplace").param("size", "100").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[*].id", hasItem(offering.productId())));

		String detail = mvc.perform(get("/api/v1/marketplace/{id}", offering.productId())
				.header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.listing.coverPhotoId").value(offering.photoId().toString()))
			.andExpect(jsonPath("$.documents[*].documentId", not(hasItem(hiddenDoc.toString()))))
			.andExpect(jsonPath("$.eligibility.eligible").value(true))
			.andExpect(jsonPath("$.termsVersion").value("2026.1"))
			.andReturn().getResponse().getContentAsString();
		assertThat(detail).doesNotContain("acquisitionCost");

		mvc.perform(get("/api/v1/marketplace/{id}/documents/{doc}", offering.productId(), offering.photoId())
				.header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk())
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(PNG));
		mvc.perform(get("/api/v1/marketplace/{id}/documents/{doc}", offering.productId(), hiddenDoc)
				.header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isNotFound());
	}

	@Test
	void eligibilityExplainsWhyAnInvestorCannotInvest() throws Exception {
		Offering retail = publishedRetailOffering("50000", "1000", "500");
		Offering standalone = publishedOffering("HNI", "60000", "60000", "60000", "1800");

		Account unverified = api.register();
		mvc.perform(get("/api/v1/marketplace/{id}", retail.productId()).header(HttpHeaders.AUTHORIZATION, unverified.bearer()))
			.andExpect(jsonPath("$.eligibility.eligible").value(false))
			.andExpect(jsonPath("$.eligibility.reasons", hasItem("Verify your identity before investing")));

		Account retailInvestor = approvedInvestor();
		mvc.perform(get("/api/v1/marketplace/{id}", standalone.productId())
				.header(HttpHeaders.AUTHORIZATION, retailInvestor.bearer()))
			.andExpect(jsonPath("$.eligibility.reasons", hasItem("Standalone containers are reserved for HNI investors")));

		Account hniInvestor = approvedInvestor();
		jdbc.update("UPDATE user_profiles SET investor_type = 'HNI' WHERE user_id = ?", hniInvestor.id());
		mvc.perform(get("/api/v1/marketplace/{id}", standalone.productId()).header(HttpHeaders.AUTHORIZATION, hniInvestor.bearer()))
			.andExpect(jsonPath("$.eligibility.eligible").value(true));
	}

	@Test
	void projectionUsesExactDecimalArithmetic() throws Exception {
		Offering offering = publishedRetailOffering("50000", "1000", "500");
		Account investor = approvedInvestor();

		mvc.perform(get("/api/v1/marketplace/{id}/projection", offering.productId()).param("amount", "10000")
				.header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.valid").value(true))
			.andExpect(jsonPath("$.ownershipPercent").value(20.0))
			.andExpect(jsonPath("$.rentalPerPayment.amount").value("300.00"))
			.andExpect(jsonPath("$.expectedAnnualIncome.amount").value("3600.00"))
			.andExpect(jsonPath("$.paymentsOverTerm").value(36))
			.andExpect(jsonPath("$.expectedIncomeOverTerm.amount").value("10800.00"));

		mvc.perform(get("/api/v1/marketplace/{id}/projection", offering.productId()).param("amount", "1250")
				.header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.valid").value(false))
			.andExpect(jsonPath("$.problems[0]").value("Amount must be 1000.00 USD plus multiples of 500.00 USD"));
	}

	@Test
	void sortsByYield() throws Exception {
		Offering low = publishedRetailOffering("100000", "1000", "1000", "500");
		Offering high = publishedRetailOffering("10000", "1000", "1000", "500");
		Account investor = approvedInvestor();
		List<String> ids = JsonPath.read(mvc.perform(get("/api/v1/marketplace").param("sort", "HIGHEST_YIELD").param("size", "100")
				.header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andReturn().getResponse().getContentAsString(), "$.content[*].id");
		assertThat(ids.indexOf(high.productId())).isLessThan(ids.indexOf(low.productId()));
	}

	// ------------------------------------------------------------------------------ capacity

	@Test
	void concurrentInvestorsCannotOversellCapacity() throws Exception {
		Offering offering = publishedRetailOffering("10000", "1000", "1000");
		UUID productId = UUID.fromString(offering.productId());
		List<Account> investors = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			investors.add(approvedInvestor());
		}

		List<Callable<Boolean>> attempts = investors.stream().<Callable<Boolean>>map(investor -> () -> {
			try {
				capacity.reserve(productId, investor.id(), Money.of("1000", "USD"), "cart-" + investor.id());
				return true;
			}
			catch (BusinessException rejected) {
				return false;
			}
		}).toList();

		int successes = 0;
		try (ExecutorService pool = Executors.newFixedThreadPool(20)) {
			for (Future<Boolean> result : pool.invokeAll(attempts)) {
				successes += result.get() ? 1 : 0;
			}
		}
		assertThat(successes).isEqualTo(10);
		CapacityView view = capacity.snapshot(productId);
		assertThat(view.reserved().amount()).isEqualTo("10000.00");
		assertThat(view.available().amount()).isEqualTo("0.00");
		Integer movements = jdbc.queryForObject("SELECT count(*) FROM capacity_movements WHERE product_id = ?",
				Integer.class, productId);
		assertThat(movements).isEqualTo(10);
	}

	@Test
	void reservationsAreIdempotentAndSettleExactlyOnce() throws Exception {
		Offering offering = publishedRetailOffering("5000", "1000", "1000");
		UUID productId = UUID.fromString(offering.productId());
		Account a = approvedInvestor();
		Account b = approvedInvestor();
		Money thousand = Money.of("1000", "USD");

		capacity.reserve(productId, a.id(), thousand, "order-a");
		capacity.reserve(productId, a.id(), thousand, "order-a");
		assertThatThrownBy(() -> capacity.reserve(productId, a.id(), Money.of("2000", "USD"), "order-a"))
			.isInstanceOf(BusinessException.class).hasMessageContaining("already used");
		assertThat(capacity.snapshot(productId).reserved().amount()).isEqualTo("1000.00");

		capacity.commit(productId, "order-a");
		capacity.commit(productId, "order-a");
		assertThatThrownBy(() -> capacity.release(productId, "order-a"))
			.isInstanceOf(BusinessException.class).hasMessageContaining("COMMIT");
		assertThat(capacity.snapshot(productId).committed().amount()).isEqualTo("1000.00");

		capacity.reserve(productId, b.id(), Money.of("4000", "USD"), "order-b");
		capacity.release(productId, "order-b");
		capacity.reserve(productId, b.id(), Money.of("4000", "USD"), "order-b2");
		capacity.commit(productId, "order-b2");

		mvc.perform(get("/api/v1/admin/investment-products/{id}", productId).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
			.andExpect(jsonPath("$.status").value("FUNDED"))
			.andExpect(jsonPath("$.capacity.fundedPercent").value(100.0));
		mvc.perform(get("/api/v1/admin/investment-products/{id}/capacity-movements", productId)
				.header(HttpHeaders.AUTHORIZATION, admin.bearer()))
			.andExpect(jsonPath("$.length()").value(6));
		assertThatThrownBy(() -> jdbc.update("UPDATE capacity_movements SET amount = 1 WHERE product_id = ?", productId))
			.hasMessageContaining("append-only");
	}

	@Test
	void standaloneContainerGoesToExactlyOneHniInvestor() throws Exception {
		Offering offering = publishedOffering("HNI", "60000", "60000", "60000", "1800");
		UUID productId = UUID.fromString(offering.productId());
		Account retail = approvedInvestor();
		Account first = approvedInvestor();
		Account second = approvedInvestor();
		jdbc.update("UPDATE user_profiles SET investor_type = 'HNI' WHERE user_id IN (?, ?)", first.id(), second.id());

		assertThatThrownBy(() -> capacity.reserve(productId, retail.id(), Money.of("60000", "USD"), "r1"))
			.hasMessageContaining("HNI");
		assertThatThrownBy(() -> capacity.reserve(productId, first.id(), Money.of("30000", "USD"), "h1"))
			.hasMessageContaining("in full");
		capacity.reserve(productId, first.id(), Money.of("60000", "USD"), "h2");
		assertThatThrownBy(() -> capacity.reserve(productId, second.id(), Money.of("60000", "USD"), "h3"))
			.hasMessageContaining("fully subscribed");
	}

	@Test
	void offeringWithReservationsCannotBeCancelled() throws Exception {
		Offering offering = publishedRetailOffering("5000", "1000", "1000");
		UUID productId = UUID.fromString(offering.productId());
		Account investor = approvedInvestor();
		capacity.reserve(productId, investor.id(), Money.of("1000", "USD"), "c1");

		cancel(offering.productId()).andExpect(status().isUnprocessableContent());
		capacity.release(productId, "c1");
		cancel(offering.productId()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
		mvc.perform(get("/api/v1/marketplace/{id}", productId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isNotFound());
	}

	// ------------------------------------------------------------------------------ helpers

	private record Offering(String productId, UUID containerId, UUID photoId) {
	}

	private Offering publishedRetailOffering(String total, String minimum, String increment) throws Exception {
		return publishedOffering("RETAIL", total, minimum, increment, "1500");
	}

	private Offering publishedRetailOffering(String total, String minimum, String increment, String rental) throws Exception {
		return publishedOffering("RETAIL", total, minimum, increment, rental);
	}

	private Offering publishedOffering(String type, String total, String minimum, String increment, String rental)
			throws Exception {
		UUID containerId = newContainer();
		UUID photo = uploadPhoto(containerId, true);
		String productId = JsonPath.read(mvc.perform(post("/api/v1/admin/investment-products")
				.header(HttpHeaders.AUTHORIZATION, admin.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(productBody(containerId, type, total, minimum, increment, rental)))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
		publish(productId).andExpect(status().isOk());
		return new Offering(productId, containerId, photo);
	}

	private Account approvedInvestor() throws Exception {
		Account investor = api.register();
		jdbc.update("UPDATE user_profiles SET kyc_status = 'APPROVED' WHERE user_id = ?", investor.id());
		return investor;
	}

	private UUID newContainer() throws Exception {
		String body = createContainer(randomContainerNumber(), 2300, 30480).andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(body, "$.container.id"));
	}

	private ResultActions createContainer(String number, int tare, int maxGross) throws Exception {
		return createContainer(number, tare, maxGross, admin);
	}

	private ResultActions createContainer(String number, int tare, int maxGross, Account actor) throws Exception {
		return mvc.perform(post("/api/v1/admin/containers").header(HttpHeaders.AUTHORIZATION, actor.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("""
					{"containerNumber":"%s","containerType":"DRY_40FT","condition":"CARGO_WORTHY","capacityCbm":67.7,
					 "maxGrossKg":%d,"tareKg":%d,"manufactureYear":2022,"manufacturer":"CIMC",
					 "currentLocation":"Port of Rotterdam","locationCountry":"NL",
					 "acquisitionCost":4200,"acquisitionCurrency":"USD"}
					""".formatted(number, maxGross, tare)));
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

	private ResultActions publish(String productId) throws Exception {
		return mvc.perform(post("/api/v1/admin/investment-products/{id}/publish", productId)
			.header(HttpHeaders.AUTHORIZATION, admin.bearer()));
	}

	private ResultActions cancel(String productId) throws Exception {
		return mvc.perform(post("/api/v1/admin/investment-products/{id}/cancel", productId)
			.header(HttpHeaders.AUTHORIZATION, admin.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Lessee withdrew\"}"));
	}

	private static String productBody(UUID containerId, String type, String total, String minimum, String increment,
			String rental) {
		String closes = Instant.now().plus(60, ChronoUnit.DAYS).toString();
		return """
				{"containerId":"%s","investmentType":"%s","title":"40ft dry container on lease","summary":"Leased to a global carrier",
				 "description":"A cargo-worthy 40ft container.","currency":"USD","totalAmount":"%s",
				 "minimumInvestment":"%s","investmentIncrement":"%s","expectedRentalAmount":"%s",
				 "rentalFrequency":"MONTHLY","durationMonths":36,"lesseeName":"Ocean Carrier Ltd","riskLevel":"MEDIUM",
				 "riskDisclosure":"Rental income depends on the lessee.","termsAndConditions":"Standard terms.",
				 "termsVersion":"2026.1","offerClosesAt":"%s"}
				""".formatted(containerId, type, total, minimum, increment, rental, closes);
	}

	private static String randomContainerNumber() {
		return ContainerNumbers.withCheckDigit("SLSU" + ThreadLocalRandom.current().nextInt(100000, 1000000));
	}

}
