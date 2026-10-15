package com.sealease.backend.reporting;

import com.jayway.jsonpath.JsonPath;
import com.sealease.backend.support.IntegrationTest;
import com.sealease.backend.support.TestApi;
import com.sealease.backend.support.TestApi.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The security dashboard and the audit log (AUDIT_VIEW). */
@IntegrationTest
class SecurityAuditIntegrationTest {

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
	void onlyStaffWithAuditViewMaySeeIt() throws Exception {
		Account investor = api.register();
		Account finance = api.staff("FINANCE");
		for (String path : List.of("/api/v1/admin/security/overview", "/api/v1/admin/audit-logs",
				"/api/v1/admin/audit-logs/export")) {
			call(investor, path).andExpect(status().isForbidden());
			call(finance, path).andExpect(status().isForbidden());
		}
		call(admin, "/api/v1/admin/security/overview").andExpect(status().isOk());
		call(admin, "/api/v1/admin/audit-logs").andExpect(status().isOk());
	}

	@Test
	void failedSignInsAndLockoutsShowOnTheDashboard() throws Exception {
		Account target = api.register();
		for (int i = 0; i < 5; i++) {
			api.loginResult(target.email(), "Wrong-Password-1");
		}

		call(admin, "/api/v1/admin/security/overview?hours=24").andExpect(status().isOk())
			.andExpect(jsonPath("$.hours").value(24))
			.andExpect(jsonPath("$.bucket").value("HOUR"))
			.andExpect(jsonPath("$.activity.length()").value(25))
			.andExpect(jsonPath("$.totals.failedSignIns").value(greaterThanOrEqualTo(5)))
			.andExpect(jsonPath("$.totals.lockouts").value(greaterThanOrEqualTo(1)))
			.andExpect(jsonPath("$.totals.activeSessions").value(greaterThanOrEqualTo(1)))
			.andExpect(jsonPath("$.lockedAccounts[*].account.email").value(hasItem(target.email())))
			.andExpect(jsonPath("$.failedByIp[*].ipAddress").value(hasItem("127.0.0.1")))
			.andExpect(jsonPath("$.securityEvents[*].subject.email").value(hasItem(target.email())));

		String month = call(admin, "/api/v1/admin/security/overview?hours=720").andExpect(status().isOk())
			.andExpect(jsonPath("$.bucket").value("DAY"))
			.andExpect(jsonPath("$.activity.length()").value(31))
			.andReturn().getResponse().getContentAsString();
		List<Integer> failed = JsonPath.read(month, "$.activity[*].failedSignIns");
		assertThat(failed.stream().mapToInt(Integer::intValue).sum()).isGreaterThanOrEqualTo(5);

		call(admin, "/api/v1/admin/security/overview?hours=5").andExpect(status().isBadRequest());
	}

	@Test
	void staffAccessToPersonalDataAndRoleChangesAreListed() throws Exception {
		Account auditor = api.staff("ADMIN");
		call(auditor, "/api/v1/admin/audit-logs/export?action=LOGIN&from=2020-01-01&to=2020-01-01")
			.andExpect(status().isOk());

		call(admin, "/api/v1/admin/security/overview").andExpect(status().isOk())
			.andExpect(jsonPath("$.sensitiveAccess[*].staff.email").value(hasItem(auditor.email())))
			.andExpect(jsonPath("$.privilegedChanges[*].action").value(hasItem("USER_ROLES_CHANGED")));
	}

	@Test
	void theLogIsSearchedByPersonActionAndDate() throws Exception {
		Account investor = api.register();
		api.loginResult(investor.email(), "Wrong-Password-1");
		api.login(investor.email(), TestApi.PASSWORD);

		String byActor = call(admin, "/api/v1/admin/audit-logs?actor=" + investor.email()).andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].actor.email").value(investor.email()))
			.andExpect(jsonPath("$.content[*].action").value(hasItem("LOGIN")))
			.andReturn().getResponse().getContentAsString();
		List<String> actions = JsonPath.read(byActor, "$.content[*].action");
		assertThat(actions).doesNotContain("LOGIN_FAILED"); // a failed sign-in has no actor

		String failed = call(admin, "/api/v1/admin/audit-logs?action=LOGIN_FAILED&entityType=user&entityId=" + investor.id())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].category").value("SIGN_IN"))
			.andExpect(jsonPath("$.content[0].actor").value(nullValue()))
			.andExpect(jsonPath("$.content[0].subject.email").value(investor.email()))
			.andExpect(jsonPath("$.content[0].ipAddress").value("127.0.0.1"))
			.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(failed, "$.content[0].id");
		call(admin, "/api/v1/admin/audit-logs/" + id).andExpect(status().isOk())
			.andExpect(jsonPath("$.action").value("LOGIN_FAILED"))
			.andExpect(jsonPath("$.subject.id").value(investor.id().toString()));

		call(admin, "/api/v1/admin/audit-logs?actor=" + investor.id() + "&category=SIGN_IN&from=2020-01-01&to=2020-01-31")
			.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
		call(admin, "/api/v1/admin/audit-logs?actor=nobody@example.com").andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(0));
		call(admin, "/api/v1/admin/audit-logs?from=2026-02-01&to=2026-01-01").andExpect(status().isBadRequest());
		call(admin, "/api/v1/admin/audit-logs?action=NOT_AN_ACTION").andExpect(status().isBadRequest());
	}

	@Test
	void exportsAreCsvAndAudited() throws Exception {
		Account investor = api.register();
		api.loginResult(investor.email(), "=cmd|' /C calc'!A0");

		byte[] csv = call(admin, "/api/v1/admin/audit-logs/export?entityType=USER&entityId=" + investor.id())
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("text/csv")))
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("audit-log-")))
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
			.andReturn().getResponse().getContentAsByteArray();
		String text = new String(csv, StandardCharsets.UTF_8);
		assertThat(text).startsWith("﻿\"time_utc\",\"action\"");
		assertThat(text).contains("\"LOGIN_FAILED\"", "\"SIGN_IN\"", "\"" + investor.email() + "\"");
		assertThat(text).doesNotContain("calc"); // the attempted password is never logged

		Integer exports = jdbc.queryForObject("""
				SELECT count(*) FROM audit_logs WHERE action = 'AUDIT_LOG_EXPORTED' AND actor_user_id = ?
				AND new_value ->> 'entityId' = ?""", Integer.class, admin.id(), investor.id().toString());
		assertThat(exports).isEqualTo(1);
	}

	private ResultActions call(Account account, String path) throws Exception {
		return mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, account.bearer()));
	}

}
