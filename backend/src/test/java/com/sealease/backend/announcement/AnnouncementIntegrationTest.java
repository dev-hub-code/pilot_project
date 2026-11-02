package com.sealease.backend.announcement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
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
import org.springframework.test.web.servlet.ResultActions;

/** Administrators send announcements; every active account in the audience gets one notification. */
@IntegrationTest
class AnnouncementIntegrationTest {

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
	void anAnnouncementToEveryoneReachesEveryActiveAccountAsANotification() throws Exception {
		Account admin = api.admin();
		Account investor = api.register();
		Account staff = api.staff("FINANCE");
		Account suspended = api.register();
		jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", suspended.id());
		Account disabled = api.register();
		jdbc.update("UPDATE users SET status = 'DISABLED' WHERE id = ?", disabled.id());
		int active = jdbc.queryForObject("SELECT count(*) FROM users WHERE status = 'ACTIVE'", Integer.class);
		String title = "Maintenance " + UUID.randomUUID();

		String sent = publish(admin, """
				{"title":"%s","body":"  The site is down on Sunday\\r\\nfrom 2 to 3 am.  ","link":"/support","audience":"EVERYONE"}
				""".formatted(title))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.recipients").value(active))
			.andExpect(jsonPath("$.body").value("The site is down on Sunday\nfrom 2 to 3 am."))
			.andExpect(jsonPath("$.audience").value("EVERYONE"))
			.andExpect(jsonPath("$.createdByName").isNotEmpty())
			.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(sent, "$.id");

		for (Account account : List.of(investor, staff, admin)) {
			mvc.perform(get("/api/v1/notifications").header(HttpHeaders.AUTHORIZATION, account.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].type").value("ANNOUNCEMENT"))
				.andExpect(jsonPath("$.content[0].title").value(title))
				.andExpect(jsonPath("$.content[0].link").value("/support"))
				.andExpect(jsonPath("$.content[0].read").value(false));
		}
		assertThat(received(suspended, title)).isZero();
		assertThat(received(disabled, title)).isZero();
		assertThat(api.auditCount("ANNOUNCEMENT_PUBLISHED", id)).isEqualTo(1);

		mvc.perform(get("/api/v1/admin/announcements").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].id").value(id))
			.andExpect(jsonPath("$.content[0].recipients").value(active));
	}

	@Test
	void anAudienceOfInvestorsOrStaffReachesOnlyThem() throws Exception {
		Account admin = api.admin();
		Account investor = api.register();
		Account staff = api.staff("FINANCE");

		String toInvestors = "For investors " + UUID.randomUUID();
		publish(admin, request(toInvestors, "INVESTORS")).andExpect(status().isCreated());
		assertThat(received(investor, toInvestors)).isEqualTo(1);
		assertThat(received(staff, toInvestors)).isZero();
		assertThat(received(admin, toInvestors)).isZero();

		String toStaff = "For staff " + UUID.randomUUID();
		publish(admin, request(toStaff, "STAFF")).andExpect(status().isCreated());
		assertThat(received(investor, toStaff)).isZero();
		assertThat(received(staff, toStaff)).isEqualTo(1);
		assertThat(received(admin, toStaff)).isEqualTo(1);
	}

	@Test
	void investorsCanBeReachedAsOnlineOrNetworkInvestorsAndOnlyActiveOnesGetIt() throws Exception {
		Account admin = api.admin();
		Account staff = api.staff("FINANCE");
		Account online = api.register();
		Account network = network(api.register());
		Account suspendedOnline = api.register();
		Account disabledNetwork = network(api.register());
		jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", suspendedOnline.id());
		jdbc.update("UPDATE users SET status = 'DISABLED' WHERE id = ?", disabledNetwork.id());

		String toOnline = "Online only " + UUID.randomUUID();
		publish(admin, request(toOnline, "ONLINE_INVESTORS")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.audience").value("ONLINE_INVESTORS"));
		assertThat(received(online, toOnline)).isEqualTo(1);
		assertThat(received(network, toOnline)).isZero();
		assertThat(received(staff, toOnline)).isZero();
		assertThat(received(suspendedOnline, toOnline)).isZero();

		String toNetwork = "Network only " + UUID.randomUUID();
		publish(admin, request(toNetwork, "NETWORK_INVESTORS")).andExpect(status().isCreated());
		assertThat(received(network, toNetwork)).isEqualTo(1);
		assertThat(received(online, toNetwork)).isZero();
		assertThat(received(staff, toNetwork)).isZero();
		assertThat(received(disabledNetwork, toNetwork)).isZero();

		// All investors: both kinds, still only the active ones.
		String toInvestors = "All investors " + UUID.randomUUID();
		publish(admin, request(toInvestors, "INVESTORS")).andExpect(status().isCreated());
		assertThat(received(online, toInvestors)).isEqualTo(1);
		assertThat(received(network, toInvestors)).isEqualTo(1);
		assertThat(received(suspendedOnline, toInvestors)).isZero();
		assertThat(received(disabledNetwork, toInvestors)).isZero();
		int expected = jdbc.queryForObject("""
				SELECT count(*) FROM users u
				WHERE u.status = 'ACTIVE' AND EXISTS (SELECT 1 FROM network_investors ni WHERE ni.user_id = u.id)
				""", Integer.class);
		assertThat(jdbc.queryForObject("SELECT recipients FROM announcements WHERE title = ?", Integer.class, toNetwork))
			.isEqualTo(expected);
	}

	@Test
	void onlyHoldersOfTheAnnouncementPermissionMaySend() throws Exception {
		Account investor = api.register();
		Account staff = api.staff("FINANCE");
		String title = "Not allowed " + UUID.randomUUID();
		for (Account account : List.of(investor, staff)) {
			publish(account, request(title, "EVERYONE")).andExpect(status().isForbidden());
			mvc.perform(get("/api/v1/admin/announcements").header(HttpHeaders.AUTHORIZATION, account.bearer()))
				.andExpect(status().isForbidden());
		}
		assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE title = ?", Integer.class, title)).isZero();
	}

	@Test
	void linksMustStayOnTheSiteAndTheMessageIsRequired() throws Exception {
		Account admin = api.admin();
		for (String link : List.of("https://example.com", "//example.com", "/\\\\example.com", "support", "/a b")) {
			publish(admin, """
					{"title":"Link","body":"Body","link":"%s","audience":"EVERYONE"}""".formatted(link))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("link"));
		}
		publish(admin, """
				{"title":"","body":" ","audience":"EVERYONE"}""").andExpect(status().isBadRequest());
		publish(admin, """
				{"title":"No audience","body":"Body"}""").andExpect(status().isBadRequest());
		publish(admin, """
				{"title":"Too long","body":"%s","audience":"EVERYONE"}""".formatted("x".repeat(1001)))
			.andExpect(status().isBadRequest());
	}

	private ResultActions publish(Account actor, String json) throws Exception {
		return mvc.perform(post("/api/v1/admin/announcements").header(HttpHeaders.AUTHORIZATION, actor.bearer())
			.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	/** Puts the account on the network scheme, as an administrator-made investor is. */
	private Account network(Account investor) {
		jdbc.update("INSERT INTO network_investors (user_id, signups_join_network, created_at) VALUES (?, false, now())",
				investor.id());
		return investor;
	}

	private static String request(String title, String audience) {
		return """
				{"title":"%s","body":"Hello","audience":"%s"}""".formatted(title, audience);
	}

	private int received(Account account, String title) {
		return jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ? AND title = ?", Integer.class,
				account.id(), title);
	}

}
