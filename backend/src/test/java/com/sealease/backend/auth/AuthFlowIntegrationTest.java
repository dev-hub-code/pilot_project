package com.sealease.backend.auth;

import com.jayway.jsonpath.JsonPath;
import com.sealease.backend.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end authentication and access-control flows against PostgreSQL.
 */
@IntegrationTest
class AuthFlowIntegrationTest {

	private static final String PASSWORD = "correct horse battery staple";
	private static final String ADMIN_EMAIL = "root-admin@sealease.test";
	private static final String ADMIN_PASSWORD = "Integration-Test-Admin-2026";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	// ------------------------------------------------------------------ session lifecycle

	@Test
	void registerThenUseRefreshAndLogOut() throws Exception {
		String email = uniqueEmail();
		Tokens registered = register(email);

		mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, registered.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(email))
			.andExpect(jsonPath("$.roles[0]").value("INVESTOR"))
			.andExpect(jsonPath("$.permissions[0]").value("INVESTOR_PORTAL"));

		Tokens refreshed = refresh(registered.refreshToken());
		assertThat(refreshed.refreshToken()).isNotEqualTo(registered.refreshToken());
		mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, refreshed.bearer()))
			.andExpect(status().isOk());

		mvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
				.content(refreshBody(refreshed.refreshToken())))
			.andExpect(status().isNoContent());

		// Logout takes effect immediately for access tokens of that session, not at expiry.
		mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, refreshed.bearer()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
		mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
				.content(refreshBody(refreshed.refreshToken())))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
	}

	@Test
	void emailsAreUniqueCaseInsensitively() throws Exception {
		String email = uniqueEmail();
		register(email);
		mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content(registerBody(email.toUpperCase())))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
	}

	@Test
	void unknownEmailAndWrongPasswordAreIndistinguishable() throws Exception {
		String email = uniqueEmail();
		register(email);
		String wrongPassword = login(email, "not the right password").getResponse().getContentAsString();
		String unknownUser = login(uniqueEmail(), PASSWORD).getResponse().getContentAsString();

		assertThat(JsonPath.<String>read(wrongPassword, "$.message"))
			.isEqualTo(JsonPath.<String>read(unknownUser, "$.message"));
		assertThat(JsonPath.<String>read(wrongPassword, "$.code")).isEqualTo("INVALID_CREDENTIALS");
	}

	@Test
	void accountLocksAfterRepeatedFailuresEvenForCorrectPassword() throws Exception {
		String email = uniqueEmail();
		register(email);
		for (int attempt = 1; attempt <= 4; attempt++) {
			assertThat(login(email, "wrong password " + attempt).getResponse().getStatus()).isEqualTo(401);
		}
		assertThat(login(email, "wrong password 5").getResponse().getStatus()).isEqualTo(423);
		assertThat(login(email, PASSWORD).getResponse().getStatus()).isEqualTo(423);

		assertThat(auditCount("ACCOUNT_LOCKED", userIdByEmail(email))).isEqualTo(1);
		assertThat(auditCount("LOGIN_FAILED", userIdByEmail(email))).isEqualTo(5);
	}

	@Test
	void replayedRefreshTokenRevokesTheWholeSession() throws Exception {
		Tokens original = register(uniqueEmail());
		Tokens rotated = refresh(original.refreshToken());

		// Test profile sets no grace period, so a replay is always treated as theft.
		mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
				.content(refreshBody(original.refreshToken())))
			.andExpect(status().isUnauthorized());

		// Both the legitimate holder's tokens and the replayed chain are now dead.
		mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, rotated.bearer()))
			.andExpect(status().isUnauthorized());
		mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
				.content(refreshBody(rotated.refreshToken())))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void concurrentRefreshesWithOneTokenYieldExactlyOneSuccess() throws Exception {
		Tokens tokens = register(uniqueEmail());
		Callable<Integer> attempt = () -> mvc.perform(post("/api/v1/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content(refreshBody(tokens.refreshToken())))
			.andReturn()
			.getResponse()
			.getStatus();

		try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
			List<Future<Integer>> results = pool.invokeAll(List.of(attempt, attempt, attempt, attempt));
			long successes = 0;
			for (Future<Integer> result : results) {
				if (result.get() == 200) {
					successes++;
				}
			}
			assertThat(successes).isEqualTo(1);
		}
	}

	@Test
	void passwordChangeKeepsCurrentSessionAndEndsOthers() throws Exception {
		String email = uniqueEmail();
		Tokens current = register(email);
		Tokens otherDevice = tokensOf(login(email, PASSWORD));
		String newPassword = "a completely new passphrase";

		mvc.perform(post("/api/v1/auth/password").header(HttpHeaders.AUTHORIZATION, current.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + newPassword + "\"}"))
			.andExpect(status().isNoContent());

		mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, current.bearer()))
			.andExpect(status().isOk());
		mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, otherDevice.bearer()))
			.andExpect(status().isUnauthorized());
		assertThat(login(email, PASSWORD).getResponse().getStatus()).isEqualTo(401);
		assertThat(login(email, newPassword).getResponse().getStatus()).isEqualTo(200);
		assertThat(auditCount("PASSWORD_CHANGE", userIdByEmail(email))).isEqualTo(1);
	}

	@Test
	void passwordsAreStoredAsArgon2idHashes() throws Exception {
		String email = uniqueEmail();
		register(email);
		String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE email = ?", String.class, email);
		assertThat(hash).startsWith("{argon2id}$argon2id$").doesNotContain(PASSWORD);
	}

	@Test
	void refreshTokensAreStoredOnlyAsHashes() throws Exception {
		Tokens tokens = register(uniqueEmail());
		Integer plaintextMatches = jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE token_hash = ?",
				Integer.class, tokens.refreshToken());
		assertThat(plaintextMatches).isZero();
	}

	// --------------------------------------------------------------------- access control

	@Test
	void bootstrapSuperAdminExistsExactlyOnce() {
		Integer admins = jdbc.queryForObject("""
				SELECT count(*) FROM user_roles ur JOIN roles r ON r.id = ur.role_id WHERE r.name = 'SUPER_ADMIN'
				""", Integer.class);
		assertThat(admins).isEqualTo(1);
	}

	@Test
	void investorCannotUseAdministration() throws Exception {
		Tokens investor = register(uniqueEmail());
		mvc.perform(get("/api/v1/admin/roles").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void grantingARoleTakesEffectOnNextSignIn() throws Exception {
		Tokens admin = adminTokens();
		String roleName = "AUDITOR_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
		mvc.perform(post("/api/v1/admin/roles").header(HttpHeaders.AUTHORIZATION, admin.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"" + roleName + "\",\"description\":\"Auditors\",\"permissions\":[\"AUDIT_VIEW\"]}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.permissions[0]").value("AUDIT_VIEW"));

		String email = uniqueEmail();
		Tokens investor = register(email);
		UUID investorId = userIdByEmail(email);
		mvc.perform(put("/api/v1/admin/users/{id}/roles", investorId).header(HttpHeaders.AUTHORIZATION, admin.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"roles\":[\"" + roleName + "\"]}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.permissions").value(org.hamcrest.Matchers.hasItem("AUDIT_VIEW")));

		// Old token carried stale authorities, so its session was revoked.
		mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isUnauthorized());
		Tokens fresh = tokensOf(login(email, PASSWORD));
		mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, fresh.bearer()))
			.andExpect(jsonPath("$.permissions").value(org.hamcrest.Matchers.hasItem("AUDIT_VIEW")));
		assertThat(auditCount("USER_ROLES_CHANGED", investorId)).isEqualTo(1);
	}

	@Test
	void adminCannotEscalatePrivileges() throws Exception {
		Tokens superAdmin = adminTokens();
		String adminEmail = uniqueEmail();
		register(adminEmail);
		UUID adminId = userIdByEmail(adminEmail);
		assignRoles(superAdmin, adminId, "[\"ADMIN\"]").andExpect(status().isOk());
		Tokens admin = tokensOf(login(adminEmail, PASSWORD));

		String victimEmail = uniqueEmail();
		register(victimEmail);
		UUID victimId = userIdByEmail(victimEmail);

		// ADMIN lacks SYSTEM_SETTINGS_MANAGE etc., so cannot hand out SUPER_ADMIN...
		assignRoles(admin, victimId, "[\"SUPER_ADMIN\"]")
			.andExpect(status().isForbidden());
		// ...nor change their own roles...
		assignRoles(admin, adminId, "[\"SUPER_ADMIN\"]").andExpect(status().isForbidden());
		// ...nor mint a role containing permissions they do not hold.
		mvc.perform(post("/api/v1/admin/roles").header(HttpHeaders.AUTHORIZATION, admin.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"SNEAKY\",\"description\":\"x\",\"permissions\":[\"SYSTEM_SETTINGS_MANAGE\"]}"))
			.andExpect(status().isForbidden());
		// ...but can grant roles within their own authority.
		assignRoles(admin, victimId, "[\"SUPPORT\"]").andExpect(status().isOk());
	}

	@Test
	void systemRolesCannotBeDeleted() throws Exception {
		Tokens admin = adminTokens();
		UUID investorRoleId = jdbc.queryForObject("SELECT id FROM roles WHERE name = 'INVESTOR'", UUID.class);
		mvc.perform(delete("/api/v1/admin/roles/{id}", investorRoleId).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
			.andExpect(status().isUnprocessableContent());
	}

	@Test
	void unsupportedSortPropertyIsABadRequest() throws Exception {
		Tokens admin = adminTokens();
		mvc.perform(get("/api/v1/admin/roles?sort=password_hash").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
			.andExpect(status().isBadRequest());
	}

	// ------------------------------------------------------------------------ audit & keys

	@Test
	void auditLogIsWrittenAndImmutable() throws Exception {
		String email = uniqueEmail();
		register(email);
		UUID userId = userIdByEmail(email);
		assertThat(auditCount("USER_REGISTERED", userId)).isEqualTo(1);
		Integer logins = jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'LOGIN' AND actor_user_id = ?",
				Integer.class, userId);
		assertThat(logins).isEqualTo(1);

		assertThatThrownBy(() -> jdbc.update("UPDATE audit_logs SET action = 'TAMPERED' WHERE actor_user_id = ?", userId))
			.hasMessageContaining("append-only");
		assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_logs WHERE actor_user_id = ?", userId))
			.hasMessageContaining("append-only");
	}

	@Test
	void jwksIsPublicAndContainsOnlyPublicKey() throws Exception {
		String body = mvc.perform(get("/api/v1/auth/jwks"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.keys[0].kty").value("RSA"))
			.andExpect(jsonPath("$.keys[0].alg").value("RS256"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(body).doesNotContain("\"d\"");
	}

	// -------------------------------------------------------------------------- helpers

	private Tokens register(String email) throws Exception {
		MvcResult result = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content(registerBody(email)))
			.andExpect(status().isCreated())
			.andReturn();
		return tokensOf(result);
	}

	private MvcResult login(String email, String password) throws Exception {
		return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
			.andReturn();
	}

	private Tokens refresh(String refreshToken) throws Exception {
		return tokensOf(mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
				.content(refreshBody(refreshToken)))
			.andExpect(status().isOk())
			.andReturn());
	}

	private Tokens adminTokens() throws Exception {
		MvcResult result = login(ADMIN_EMAIL, ADMIN_PASSWORD);
		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		return tokensOf(result);
	}

	private org.springframework.test.web.servlet.ResultActions assignRoles(Tokens actor, UUID target, String roles)
			throws Exception {
		MockHttpServletRequestBuilder request = put("/api/v1/admin/users/{id}/roles", target)
			.header(HttpHeaders.AUTHORIZATION, actor.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"roles\":" + roles + "}");
		return mvc.perform(request);
	}

	private UUID userIdByEmail(String email) {
		return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email.toLowerCase());
	}

	private int auditCount(String action, UUID entityId) {
		Integer count = jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = ? AND entity_id = ?",
				Integer.class, action, entityId.toString());
		return count == null ? 0 : count;
	}

	private static Tokens tokensOf(MvcResult result) throws Exception {
		String body = result.getResponse().getContentAsString();
		return new Tokens(JsonPath.read(body, "$.accessToken"), JsonPath.read(body, "$.refreshToken"));
	}

	private static String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@example.com";
	}

	private static String registerBody(String email) {
		return "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"firstName\":\"Test\",\"lastName\":\"User\"}";
	}

	private static String refreshBody(String refreshToken) {
		return "{\"refreshToken\":\"" + refreshToken + "\"}";
	}

	private record Tokens(String accessToken, String refreshToken) {

		String bearer() {
			return "Bearer " + accessToken;
		}

	}

}
