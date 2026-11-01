package com.sealease.backend.investment;

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
class OpenPlansIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void anyoneReadsPlansButCannotActOnThem() throws Exception {
		OfferingFixtures offerings = new OfferingFixtures(mvc, new TestApi(mvc, jdbc).admin());
		String open = code(offerings.plan());
		String draft = code(offerings.draft("ONLINE", "2"));

		// No sign-in: the open plan is listed, the draft is not.
		mvc.perform(get("/api/v1/open/plans").param("size", "50"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[*].code").value(hasItem(open)))
			.andExpect(jsonPath("$.content[*].code").value(not(hasItem(draft))))
			.andExpect(jsonPath("$.content[0].status").value("OPEN"));
		mvc.perform(get("/api/v1/open/plans/{code}", open))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(open))
			.andExpect(jsonPath("$.tenureYears").value(1))
			.andExpect(jsonPath("$.annualReturnPercent").value(24))
			.andExpect(jsonPath("$.unitPrice.amount").value("500.00"))
			.andExpect(jsonPath("$.minUnits").value(1))
			// Nothing about the viewer, buyers or staff.
			.andExpect(jsonPath("$.eligibility").doesNotExist())
			.andExpect(jsonPath("$.containersSold").doesNotExist())
			.andExpect(jsonPath("$.id").doesNotExist());
		mvc.perform(get("/api/v1/open/plans/{code}", draft)).andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/open/plans").param("size", "500")).andExpect(status().isBadRequest());

		// Read-only: no other method is open.
		mvc.perform(post("/api/v1/open/plans").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isUnauthorized());
		mvc.perform(delete("/api/v1/open/plans/{code}", open)).andExpect(status().isUnauthorized());
	}

	private String code(UUID plan) {
		return jdbc.queryForObject("SELECT code FROM investment_products WHERE id = ?", String.class, plan);
	}

}
