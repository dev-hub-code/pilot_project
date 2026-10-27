package com.sealease.backend.support;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** Account helpers for integration tests. */
public final class TestApi {

	public static final String PASSWORD = "Correct-Horse-9";
	public static final String ADMIN_EMAIL = "root-admin@sealease.test";
	public static final String ADMIN_PASSWORD = "Super-Admin-2026!";

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
						+ "\",\"firstName\":\"Test\",\"lastName\":\"Investor\",\"phone\":\"" + mobile() + "\""
						+ (referralCode == null ? "" : ",\"referralCode\":\"" + referralCode + "\"") + "}"))
			.andReturn();
	}

	/** A random Indian mobile number: a number may belong to one account only, and tests share a database. */
	public static String mobile() {
		return "9" + String.format("%09d", java.util.concurrent.ThreadLocalRandom.current().nextInt(1_000_000_000));
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

	/**
	 * Assigns a review to the reviewer, as they must before deciding it: {@code queue} is "kyc" or "bank-accounts".
	 * Not asserted, so the decision that follows is what a test checks.
	 */
	public void assignReview(String queue, Object id, Account reviewer) throws Exception {
		mvc.perform(post("/api/v1/admin/{queue}/{id}/assign", queue, id).header(HttpHeaders.AUTHORIZATION, reviewer.bearer()));
	}

	/** Takes the task of assigning a paid investment's container, as finance must before assigning it. Not asserted. */
	public void takeContainerTask(Object holdingId, Account actor) throws Exception {
		mvc.perform(post("/api/v1/admin/container-assignments/{id}/take", holdingId)
			.header(HttpHeaders.AUTHORIZATION, actor.bearer()));
	}

	/** Takes the task of confirming or rejecting a payment, as finance must before deciding it. Not asserted. */
	public void takePayment(Object paymentId, Account actor) throws Exception {
		mvc.perform(post("/api/v1/admin/payments/{id}/take", paymentId).header(HttpHeaders.AUTHORIZATION, actor.bearer()));
	}

	/** Takes a finance task (a withdrawal to pay), as finance must before marking it. Not asserted. */
	public void takeFinanceTask(Object withdrawalId, Account actor) throws Exception {
		mvc.perform(post("/api/v1/admin/finance-tasks/{id}/take", withdrawalId).header(HttpHeaders.AUTHORIZATION, actor.bearer()));
	}

	/** The admin's current session, shared by every test while it lasts. */
	private static Account admin;

	/**
	 * The bootstrap admin, signed in. A sign-in ends the user's other sessions, so the session is reused while it is
	 * active: signing in afresh for every call would end the token a test is still holding.
	 */
	public Account admin() throws Exception {
		synchronized (TestApi.class) {
			if (admin == null || mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
				.andReturn().getResponse().getStatus() != 200) {
				admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
			}
			return admin;
		}
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
