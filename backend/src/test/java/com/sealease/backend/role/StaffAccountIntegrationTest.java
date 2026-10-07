package com.sealease.backend.role;

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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Staff accounts created by administrators, with a temporary password that must be replaced first. */
@IntegrationTest
class StaffAccountIntegrationTest {

	private static final String NEW_PASSWORD = "a brand new staff passphrase";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	private TestApi api;
	private Account admin;

	@BeforeEach
	void setUp() throws Exception {
		api = new TestApi(mvc, jdbc);
		admin = api.admin();
	}

	@Test
	void aNewStaffMemberMustReplaceTheTemporaryPasswordBeforeUsingTheApi() throws Exception {
		String email = email();
		String temporary = JsonPath.read(create(admin, email, "SUPPORT").andExpect(status().isCreated())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
			.andExpect(jsonPath("$.roles[0]").value("SUPPORT"))
			.andExpect(jsonPath("$.temporaryPassword").value(matchesPattern("([2-9A-HJ-NP-Z]{4}-){3}[2-9A-HJ-NP-Z]{4}")))
			.andExpect(jsonPath("$.expiresAt").isNotEmpty())
			.andReturn().getResponse().getContentAsString(), "$.temporaryPassword");
		UUID userId = api.userIdByEmail(email);
		assertThat(api.auditCount("STAFF_ACCOUNT_CREATED", userId)).isEqualTo(1);

		String body = login(email, temporary).getResponse().getContentAsString();
		String access = JsonPath.read(body, "$.accessToken");
		String refresh = JsonPath.read(body, "$.refreshToken");
		mvc.perform(get("/api/v1/admin/support/tickets").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
		mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
			.andExpect(jsonPath("$.passwordChangeRequired").value(true))
			.andExpect(jsonPath("$.permissions").value(not(hasItem("INVESTOR_PORTAL"))));

		changePassword(access, temporary, NEW_PASSWORD).andExpect(status().isNoContent());
		String renewed = JsonPath.read(mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\":\"" + refresh + "\"}")).andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString(), "$.accessToken");
		mvc.perform(get("/api/v1/admin/support/tickets").header(HttpHeaders.AUTHORIZATION, "Bearer " + renewed))
			.andExpect(status().isOk());
		mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + renewed))
			.andExpect(jsonPath("$.passwordChangeRequired").value(false));
		assertThat(api.loginResult(email, temporary).getResponse().getStatus()).isEqualTo(401);
		assertThat(api.loginResult(email, NEW_PASSWORD).getResponse().getStatus()).isEqualTo(200);
	}

	@Test
	void creationIsGuardedAgainstEscalationAndMistakes() throws Exception {
		create(admin, email(), "INVESTOR").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(containsString("investor roles")));
		create(admin, email(), "NO_SUCH_ROLE").andExpect(status().isBadRequest());
		String email = email();
		create(admin, email, "SALES").andExpect(status().isCreated());
		create(admin, email.toUpperCase(), "SALES").andExpect(status().isConflict());

		// Without USER_ROLE_ASSIGN: refused. With it but lacking a role's permissions: refused, nothing created.
		create(api.staff("SUPPORT"), email(), "SUPPORT").andExpect(status().isForbidden());
		String finance = email();
		create(api.staff("ADMIN"), finance, "FINANCE").andExpect(status().isForbidden());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Integer.class, finance)).isZero();
	}

	@Test
	void aResetIssuesANewTemporaryPasswordAndEndsEverySession() throws Exception {
		String email = email();
		String first = JsonPath.read(create(admin, email, "SALES").andReturn().getResponse().getContentAsString(),
				"$.temporaryPassword");
		UUID userId = api.userIdByEmail(email);
		String access = JsonPath.read(login(email, first).getResponse().getContentAsString(), "$.accessToken");
		changePassword(access, first, NEW_PASSWORD).andExpect(status().isNoContent());
		String session = JsonPath.read(login(email, NEW_PASSWORD).getResponse().getContentAsString(), "$.accessToken");

		String second = JsonPath.read(reset(admin, userId).andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString(), "$.temporaryPassword");
		assertThat(second).isNotEqualTo(first);
		mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + session))
			.andExpect(status().isUnauthorized());
		assertThat(api.loginResult(email, NEW_PASSWORD).getResponse().getStatus()).isEqualTo(401);
		assertThat(api.loginResult(email, second).getResponse().getStatus()).isEqualTo(200);
		assertThat(api.auditCount("TEMPORARY_PASSWORD_ISSUED", userId)).isEqualTo(1);

		reset(admin, api.register().id()).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.message").value(containsString("only issued to staff")));
		reset(admin, api.userIdByEmail(TestApi.ADMIN_EMAIL)).andExpect(status().isForbidden());
	}

	@Test
	void anAccountIsEitherAnInvestorOrStaff() throws Exception {
		mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
			.andExpect(jsonPath("$.permissions").value(not(hasItem("INVESTOR_PORTAL"))));

		Account investor = api.register();
		mvc.perform(put("/api/v1/admin/users/{id}/roles", investor.id()).header(HttpHeaders.AUTHORIZATION, admin.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"INVESTOR\",\"FINANCE\"]}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(containsString("either an investor or staff")));

		mvc.perform(post("/api/v1/admin/roles").header(HttpHeaders.AUTHORIZATION, admin.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"name":"INVESTING_AUDITOR","description":"Mixed","permissions":["INVESTOR_PORTAL","AUDIT_VIEW"]}
						"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(containsString("cannot combine")));
	}

	@Test
	void anExpiredTemporaryPasswordNoLongerSignsIn() throws Exception {
		String email = email();
		String temporary = JsonPath.read(create(admin, email, "SALES").andReturn().getResponse().getContentAsString(),
				"$.temporaryPassword");
		jdbc.update("UPDATE users SET temporary_password_expires_at = now() - interval '1 minute' WHERE email = ?", email);

		MvcResult result = api.loginResult(email, temporary);
		assertThat(result.getResponse().getStatus()).isEqualTo(401);
		assertThat(result.getResponse().getContentAsString()).contains("TEMPORARY_PASSWORD_EXPIRED");
	}

	// ----------------------------------------------------------------------------- helpers

	private static String email() {
		return "staff-" + UUID.randomUUID() + "@example.com";
	}

	private ResultActions create(Account actor, String email, String role) throws Exception {
		return mvc.perform(post("/api/v1/admin/staff").header(HttpHeaders.AUTHORIZATION, actor.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("""
					{"email":"%s","firstName":"Sam","lastName":"Staff","roles":["%s"]}
					""".formatted(email, role)));
	}

	private ResultActions reset(Account actor, UUID userId) throws Exception {
		return mvc.perform(post("/api/v1/admin/users/{id}/temporary-password", userId)
			.header(HttpHeaders.AUTHORIZATION, actor.bearer()));
	}

	private MvcResult login(String email, String password) throws Exception {
		MvcResult result = api.loginResult(email, password);
		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		return result;
	}

	private ResultActions changePassword(String accessToken, String current, String next) throws Exception {
		return mvc.perform(post("/api/v1/auth/password").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}"));
	}

}
