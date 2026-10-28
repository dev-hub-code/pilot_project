package com.sealease.backend.marketplace;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.sealease.backend.support.IntegrationTest;
import com.sealease.backend.support.OfferingFixtures;
import com.sealease.backend.support.TestApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** The open API shows the marketplace to anyone, read-only: listed plans and rent bands, nothing else. */
@IntegrationTest
class OpenMarketplaceIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void anyoneReadsTheMarketplaceButCannotActOnIt() throws Exception {
		OfferingFixtures offerings = new OfferingFixtures(mvc, new TestApi(mvc, jdbc).admin());
		String open = code(offerings.plan());
		String draft = code(offerings.draft("DRY_40FT", "75000", "2", 1, 5));

		// No sign-in: the open plan is listed, the draft is not.
		mvc.perform(get("/api/v1/open/marketplace").param("size", "50"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[*].code").value(hasItem(open)))
			.andExpect(jsonPath("$.content[*].code").value(not(hasItem(draft))))
			.andExpect(jsonPath("$.content[0].status").value("OPEN"));
		mvc.perform(get("/api/v1/open/marketplace/{code}", open))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(open))
			.andExpect(jsonPath("$.price.amount").value("50000.00"))
			.andExpect(jsonPath("$.description").isNotEmpty())
			.andExpect(jsonPath("$.photoIds").isArray())
			// Nothing about the viewer, buyers or staff.
			.andExpect(jsonPath("$.eligibility").doesNotExist())
			.andExpect(jsonPath("$.containersSold").doesNotExist())
			.andExpect(jsonPath("$.id").doesNotExist());
		mvc.perform(get("/api/v1/open/marketplace/{code}", draft)).andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/open/marketplace/{code}/photos/{id}", open, UUID.randomUUID()))
			.andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/open/rent-bands"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].monthlyRentPercent").isNumber())
			.andExpect(jsonPath("$[0].updatedByName").doesNotExist());
		mvc.perform(get("/api/v1/open/marketplace").param("size", "500")).andExpect(status().isBadRequest());

		// Read-only: no other method is open.
		mvc.perform(post("/api/v1/open/marketplace").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isUnauthorized());
		mvc.perform(delete("/api/v1/open/marketplace/{code}", open)).andExpect(status().isUnauthorized());
	}

	private String code(UUID plan) {
		return jdbc.queryForObject("SELECT code FROM investment_products WHERE id = ?", String.class, plan);
	}

}
