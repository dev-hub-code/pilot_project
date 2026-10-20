package com.sealease.backend.location;

import com.jayway.jsonpath.JsonPath;
import com.sealease.backend.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Countries, then a country's states, then a state's cities: public, for address dropdowns. */
@IntegrationTest
class LocationIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Test
	void countriesThenStatesThenCitiesWithoutLoggingIn() throws Exception {
		String countries = mvc.perform(get("/api/v1/public/locations/countries"))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("max-age=86400")))
			.andExpect(jsonPath("$.length()").value(250))
			.andReturn().getResponse().getContentAsString();
		assertThat((List<String>) JsonPath.read(countries, "$[?(@.code == 'IN')].name")).containsExactly("India");
		List<String> names = JsonPath.read(countries, "$[*].name");
		assertThat(names).isSortedAccordingTo(String::compareTo);

		// India's 36 states and union territories.
		String states = mvc.perform(get("/api/v1/public/locations/countries/{code}/states", "in"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(36))
			.andReturn().getResponse().getContentAsString();
		List<Integer> maharashtra = JsonPath.read(states, "$[?(@.name == 'Maharashtra')].id");
		assertThat(maharashtra).hasSize(1);

		String cities = mvc.perform(get("/api/v1/public/locations/states/{id}/cities", maharashtra.getFirst()))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		assertThat((List<String>) JsonPath.read(cities, "$[*].name")).contains("Mumbai", "Pune", "Nagpur").hasSizeGreaterThan(500);
	}

	@Test
	void unknownCountriesAndStatesAreNotFound() throws Exception {
		mvc.perform(get("/api/v1/public/locations/countries/{code}/states", "ZZ")).andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/public/locations/states/{id}/cities", 999999999)).andExpect(status().isNotFound());
		// A country without states answers with none.
		mvc.perform(get("/api/v1/public/locations/countries/{code}/states", "AQ"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
	}

}
