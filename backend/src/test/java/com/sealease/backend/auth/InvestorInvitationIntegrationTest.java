package com.sealease.backend.auth;

import com.jayway.jsonpath.JsonPath;
import com.sealease.backend.support.IntegrationTest;
import com.sealease.backend.support.SentMail;
import com.sealease.backend.support.TestApi;
import com.sealease.backend.support.TestApi.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Any employee invites a client by email; the client opens their own network investor account from the link. */
@IntegrationTest
class InvestorInvitationIntegrationTest {

	private static final Pattern LINK = Pattern.compile("/invitation\\?token=([A-Za-z0-9_-]+)");

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoBean
	private JavaMailSender mail;

	private TestApi api;

	@BeforeEach
	void setUp() {
		api = new TestApi(mvc, jdbc);
	}

	@Test
	void anyEmployeeInvitesAndTheClientOpensANetworkAccount() throws Exception {
		// A support agent holds no investor permissions at all.
		Account agent = api.staff("SUPPORT");
		String email = uniqueEmail();

		invite(agent, email).andExpect(status().isCreated())
			.andExpect(jsonPath("$.email").value(email))
			.andExpect(jsonPath("$.emailed").value(true));
		SentMail sent = SentMail.lastTo(mail, email);
		assertThat(sent.subject()).isEqualTo("You're invited to open a SeaLease investor account");
		String token = tokenIn(sent);

		lookup(token).andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(email))
			.andExpect(jsonPath("$.firstName").value("Ina"));

		String body = accept(token).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		String accessToken = JsonPath.read(body, "$.accessToken");
		mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
			.andExpect(jsonPath("$.email").value(email))
			.andExpect(jsonPath("$.emailVerified").value(true))
			.andExpect(jsonPath("$.passwordChangeRequired").value(false));

		UUID userId = api.userIdByEmail(email);
		assertThat(jdbc.queryForObject("SELECT signups_join_network FROM network_investors WHERE user_id = ?",
				Boolean.class, userId)).isTrue();
		// No referrer: the employee who invited them is not one, so earns no referral income on them.
		assertThat(jdbc.queryForObject("SELECT count(*) FROM referrals WHERE user_id = ?", Integer.class, userId)).isZero();

		// The link works once.
		accept(token).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_TOKEN"));
		lookup(token).andExpect(status().isBadRequest());
	}

	@Test
	void investorsCannotInvite() throws Exception {
		invite(api.register(), uniqueEmail()).andExpect(status().isForbidden());
		mvc.perform(post("/api/v1/admin/investor-invitations").contentType(MediaType.APPLICATION_JSON)
			.content(inviteJson(uniqueEmail()))).andExpect(status().isUnauthorized());
	}

	@Test
	void theClientsNameIsRequired() throws Exception {
		mvc.perform(post("/api/v1/admin/investor-invitations").header(HttpHeaders.AUTHORIZATION, api.staff("SUPPORT").bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"%s\",\"firstName\":\" \",\"signupsJoinNetwork\":true}".formatted(uniqueEmail())))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[*].field").value(org.hamcrest.Matchers.containsInAnyOrder("firstName", "lastName")));
	}

	@Test
	void registeredAddressesAreRefused() throws Exception {
		invite(api.staff("SUPPORT"), api.register().email()).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
	}

	@Test
	void aNewInvitationRetiresTheEarlierLinkAndExpiredLinksFail() throws Exception {
		Account agent = api.staff("SUPPORT");
		String email = uniqueEmail();
		invite(agent, email).andExpect(status().isCreated());
		String first = tokenIn(SentMail.lastTo(mail, email));
		invite(agent, email).andExpect(status().isCreated());
		String second = tokenIn(SentMail.lastTo(mail, email));

		lookup(first).andExpect(status().isBadRequest());
		jdbc.update("""
				UPDATE investor_invitations SET created_at = now() - interval '9 days', expires_at = now() - interval '1 day'
				WHERE email = ? AND revoked_at IS NULL""", email);
		accept(second).andExpect(status().isBadRequest());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Integer.class, email)).isZero();
	}

	private ResultActions invite(Account actor, String email) throws Exception {
		return mvc.perform(post("/api/v1/admin/investor-invitations").header(HttpHeaders.AUTHORIZATION, actor.bearer())
			.contentType(MediaType.APPLICATION_JSON).content(inviteJson(email)));
	}

	private static String inviteJson(String email) {
		return """
				{"email":"%s","firstName":"Ina","lastName":"Vited","signupsJoinNetwork":true}
				""".formatted(email);
	}

	private ResultActions lookup(String token) throws Exception {
		return mvc.perform(post("/api/v1/auth/invitations/lookup").contentType(MediaType.APPLICATION_JSON)
			.content("{\"token\":\"%s\"}".formatted(token)));
	}

	private ResultActions accept(String token) throws Exception {
		return mvc.perform(post("/api/v1/auth/invitations/accept").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"token":"%s","password":"%s","firstName":"Ina","lastName":"Vited","phone":"%s","phoneCountry":"IN",
					 "acceptTerms":true}""".formatted(token, TestApi.PASSWORD, TestApi.mobile())));
	}

	private static String tokenIn(SentMail sent) {
		Matcher link = LINK.matcher(sent.text());
		assertThat(link.find()).as("invitation link in: %s", sent.text()).isTrue();
		return link.group(1);
	}

	private static String uniqueEmail() {
		return "invite-" + UUID.randomUUID() + "@example.com";
	}

}
