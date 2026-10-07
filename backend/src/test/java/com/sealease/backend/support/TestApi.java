package com.sealease.backend.support;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** Account helpers for integration tests. */
public final class TestApi {

	public static final String PASSWORD = "correct horse battery staple";
	public static final String ADMIN_EMAIL = "root-admin@sealease.test";
	public static final String ADMIN_PASSWORD = "Integration-Test-Admin-2026";

	private final MockMvc mvc;
	private final JdbcTemplate jdbc;

	public TestApi(MockMvc mvc, JdbcTemplate jdbc) {
		this.mvc = mvc;
		this.jdbc = jdbc;
	}

	public record Account(UUID id, String email, String accessToken) {

		public String bearer() {
			return "Bearer " + accessToken;
		}

	}

	public Account register() throws Exception {
		return register(null);
	}

	/** Registers with a referral code ({@code null} for none). */
	public Account register(String referralCode) throws Exception {
		String email = "user-" + UUID.randomUUID() + "@example.com";
		MvcResult result = registerResult(email, referralCode);
		assertThat(result.getResponse().getStatus()).isEqualTo(201);
		return new Account(userIdByEmail(email), email, JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken"));
	}

	public MvcResult registerResult(String email, String referralCode) throws Exception {
		return mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
						+ "\",\"firstName\":\"Test\",\"lastName\":\"Investor\""
						+ (referralCode == null ? "" : ",\"referralCode\":\"" + referralCode + "\"") + "}"))
			.andReturn();
	}

	public Account login(String email, String password) throws Exception {
		MvcResult result = loginResult(email, password);
		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		return new Account(userIdByEmail(email), email, JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken"));
	}

	public MvcResult loginResult(String email, String password) throws Exception {
		return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
			.andReturn();
	}

	public Account admin() throws Exception {
		return login(ADMIN_EMAIL, ADMIN_PASSWORD);
	}

	/** A new account holding exactly the given roles, signed in again so its token carries them. */
	public Account staff(String... roles) throws Exception {
		return becomeStaff(register(), roles);
	}

	/**
	 * Turns an existing (investor) account into a staff account holding exactly the given roles, e.g.
	 * an investor hired by the platform; whatever they did as an investor remains theirs.
	 */
	public Account becomeStaff(Account account, String... roles) throws Exception {
		String json = "[\"" + String.join("\",\"", roles) + "\"]";
		int status = mvc.perform(put("/api/v1/admin/users/{id}/roles", account.id())
				.header(HttpHeaders.AUTHORIZATION, admin().bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"roles\":" + json + "}"))
			.andReturn()
			.getResponse()
			.getStatus();
		assertThat(status).isEqualTo(200);
		return login(account.email(), PASSWORD);
	}

	/** A new, active company bank account investors can pay into, created by the admin. */
	public UUID companyBankAccount() throws Exception {
		String accountNumber = String.valueOf(1_000_000_000L + (long) (Math.random() * 8_999_999_999L));
		MvcResult result = mvc.perform(post("/api/v1/admin/company-bank-accounts")
				.header(HttpHeaders.AUTHORIZATION, admin().bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"accountName":"SeaLease Collections","bankName":"Test Bank","branch":"Fort, Mumbai",
						 "accountNumber":"%s","ifscCode":"HDFC0001234","upiId":"sealease@hdfcbank"}""".formatted(accountNumber)))
			.andReturn();
		assertThat(result.getResponse().getStatus()).isEqualTo(201);
		return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
	}

	public UUID userIdByEmail(String email) {
		return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email.toLowerCase());
	}

	public int auditCount(String action, Object entityId) {
		Integer count = jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = ? AND entity_id = ?",
				Integer.class, action, entityId.toString());
		return count == null ? 0 : count;
	}

}
