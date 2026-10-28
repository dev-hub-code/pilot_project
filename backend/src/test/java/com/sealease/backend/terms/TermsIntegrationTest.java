package com.sealease.backend.terms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.sealease.backend.support.IntegrationTest;
import com.sealease.backend.support.TestApi;
import com.sealease.backend.support.TestApi.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Super administrators write the terms and conditions; signing up means accepting the version in force. */
@IntegrationTest
class TermsIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	private TestApi api;

	@BeforeEach
	void setUp() {
		api = new TestApi(mvc, jdbc);
	}

	@Test
	void signingUpAcceptsTheTermsInForce() throws Exception {
		// Without accepting, no account is made.
		String email = "terms-" + UUID.randomUUID() + "@example.com";
		mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
				{"email":"%s","password":"Correct-Horse-9","firstName":"Tara","lastName":"Mann","phone":"%s"}"""
			.formatted(email, TestApi.mobile())))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("acceptTerms"));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Integer.class, email)).isZero();

		// Anyone can read the terms in force.
		String terms = mvc.perform(get("/api/v1/public/terms")).andExpect(status().isOk())
			.andExpect(jsonPath("$.body").isNotEmpty())
			.andReturn().getResponse().getContentAsString();
		int inForce = JsonPath.read(terms, "$.version");

		Account investor = api.register();
		assertThat(jdbc.queryForObject("SELECT terms_version FROM users WHERE id = ?", Integer.class, investor.id()))
			.isEqualTo(inForce);
		assertThat(jdbc.queryForObject("SELECT terms_accepted_at IS NOT NULL FROM users WHERE id = ?", Boolean.class,
				investor.id())).isTrue();
	}

	@Test
	void superAdminsSaveANewVersion() throws Exception {
		Account admin = api.admin();
		Account investor = api.register();
		String wording = "{\"title\":\"Website Terms\",\"body\":\"# Use\\r\\n\\r\\nBe fair.  \\r\\n\"}";
		mvc.perform(put("/api/v1/admin/terms").header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(wording))
			.andExpect(status().isForbidden());

		int before = JsonPath.read(mvc.perform(get("/api/v1/public/terms")).andReturn().getResponse().getContentAsString(),
				"$.version");
		mvc.perform(put("/api/v1/admin/terms").header(HttpHeaders.AUTHORIZATION, admin.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(wording))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version").value(before + 1))
			.andExpect(jsonPath("$.body").value("# Use\n\nBe fair."));
		mvc.perform(get("/api/v1/public/terms"))
			.andExpect(jsonPath("$.title").value("Website Terms"))
			.andExpect(jsonPath("$.version").value(before + 1));
		mvc.perform(get("/api/v1/admin/terms").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
			.andExpect(jsonPath("$.current.version").value(before + 1))
			.andExpect(jsonPath("$.history[1].version").value(before))
			.andExpect(jsonPath("$.history[1].body").doesNotExist());

		// Someone signing up now accepts the new version; earlier accounts keep theirs.
		Account later = api.register();
		assertThat(jdbc.queryForObject("SELECT terms_version FROM users WHERE id = ?", Integer.class, later.id()))
			.isEqualTo(before + 1);
		assertThat(jdbc.queryForObject("SELECT terms_version FROM users WHERE id = ?", Integer.class, investor.id()))
			.isEqualTo(before);
	}

}
