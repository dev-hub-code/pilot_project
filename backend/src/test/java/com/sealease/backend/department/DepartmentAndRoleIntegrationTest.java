package com.sealease.backend.department;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Departments organise staff without granting anything; roles stay guarded against escalation. */
@IntegrationTest
class DepartmentAndRoleIntegrationTest {

	private static final AtomicInteger NUMBER = new AtomicInteger();

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

	private ResultActions as(Account who, MockHttpServletRequestBuilder request) throws Exception {
		return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, who.bearer()));
	}

	private ResultActions send(Account who, MockHttpServletRequestBuilder request, String json) throws Exception {
		return as(who, request.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private static String unique(String prefix) {
		return prefix + "_" + NUMBER.incrementAndGet() + "_" + Long.toString(System.nanoTime(), 36).toUpperCase();
	}

	private String department() throws Exception {
		String body = send(admin, post("/api/v1/admin/departments"), """
				{"code":"%s","name":"Treasury"}""".formatted(unique("TREASURY")))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	private String role(Account actor, String... permissions) throws Exception {
		String body = send(actor, post("/api/v1/admin/roles"), """
				{"name":"%s","description":"A role","permissions":%s}"""
			.formatted(unique("ROLE"), quoted(permissions)))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	private static String quoted(String... codes) {
		return java.util.Arrays.stream(codes).map(c -> "\"" + c + "\"")
			.collect(java.util.stream.Collectors.joining(",", "[", "]"));
	}

	private String roleName(String id) {
		return jdbc.queryForObject("SELECT name FROM roles WHERE id = ?::uuid", String.class, id);
	}

	@Test
	void theStartingDepartmentsAndRoleTemplatesExist() throws Exception {
		as(admin, get("/api/v1/admin/departments"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].code", hasItem("FINANCE")))
			.andExpect(jsonPath("$[*].code", hasItem("BUSINESS_INTELLIGENCE")));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM departments WHERE code IN ('PLATFORM_ADMIN', 'INVESTOR_RELATIONS',"
				+ " 'INVESTMENT_OPS', 'FINANCE', 'COMPLIANCE', 'CUSTOMER_SUPPORT', 'SALES_CRM', 'PARTNER_OPS',"
				+ " 'BUSINESS_INTELLIGENCE')", Integer.class)).isEqualTo(9);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM roles WHERE name = 'FINANCE_APPROVER' AND NOT system", Integer.class))
			.isEqualTo(1);
	}

	@Test
	void departmentsAreCreatedAndKeepTheirHistory() throws Exception {
		String id = department();
		Account employee = api.staff("SUPPORT");
		send(admin, post("/api/v1/admin/departments/{id}/employees", id), """
				{"userId":"%s","primary":true}""".formatted(employee.id()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.employees").value(1));
		send(admin, put("/api/v1/admin/departments/{id}/head", id), """
				{"userId":"%s"}""".formatted(employee.id())).andExpect(status().isOk());

		// A head leaves only after being replaced.
		as(admin, delete("/api/v1/admin/departments/{id}/employees/{user}", id, employee.id()))
			.andExpect(status().isUnprocessableContent());

		as(admin, post("/api/v1/admin/departments/{id}/deactivate", id)).andExpect(status().isOk())
			.andExpect(jsonPath("$.active").value(false));
		Account another = api.staff("SUPPORT");
		send(admin, post("/api/v1/admin/departments/{id}/employees", id), """
				{"userId":"%s","primary":false}""".formatted(another.id())).andExpect(status().isUnprocessableContent());

		as(admin, get("/api/v1/admin/departments/{id}", id)).andExpect(status().isOk())
			.andExpect(jsonPath("$.employees[0].primary").value(true))
			.andExpect(jsonPath("$.employees[0].head").value(true))
			.andExpect(jsonPath("$.history[*].action", hasItem("HEAD_CHANGED")))
			.andExpect(jsonPath("$.history[*].action", hasItem("DEACTIVATED")));
		assertThat(api.auditCount("DEPARTMENT_STAFF_CHANGED", id)).isEqualTo(1);
	}

	@Test
	void anEmployeeHasOnePrimaryDepartmentAndInvestorsCannotJoin() throws Exception {
		String first = department();
		String second = department();
		Account employee = api.staff("SUPPORT");
		for (String id : new String[] { first, second }) {
			send(admin, post("/api/v1/admin/departments/{id}/employees", id), """
					{"userId":"%s","primary":true}""".formatted(employee.id())).andExpect(status().isOk());
		}
		as(admin, get("/api/v1/admin/staff/{id}", employee.id())).andExpect(status().isOk())
			.andExpect(jsonPath("$.departments.length()").value(2))
			.andExpect(jsonPath("$.departments[0].id").value(second))
			.andExpect(jsonPath("$.departments[0].primary").value(true))
			.andExpect(jsonPath("$.departments[1].primary").value(false));

		send(admin, post("/api/v1/admin/departments/{id}/employees", first), """
				{"userId":"%s","primary":false}""".formatted(api.register().id()))
			.andExpect(status().isUnprocessableContent());
	}

	@Test
	void joiningADepartmentGrantsNoPermission() throws Exception {
		String id = department();
		Account employee = api.staff("SUPPORT");
		jdbc.update("UPDATE roles SET department_id = ?::uuid WHERE name = 'FINANCE_APPROVER'", id);
		send(admin, post("/api/v1/admin/departments/{id}/employees", id), """
				{"userId":"%s","primary":true}""".formatted(employee.id())).andExpect(status().isOk());

		Account again = api.login(employee.email(), TestApi.PASSWORD);
		as(again, get("/api/v1/admin/ledger/trial-balance")).andExpect(status().isForbidden());
	}

	@Test
	void onlyThoseWhoManageDepartmentsSeeOthers() throws Exception {
		String mine = department();
		String other = department();
		String viewing = role(admin, "DEPARTMENT_VIEW");
		Account viewer = api.staff(roleName(viewing));
		send(admin, post("/api/v1/admin/departments/{id}/employees", mine), """
				{"userId":"%s","primary":true}""".formatted(viewer.id())).andExpect(status().isOk());

		as(viewer, get("/api/v1/admin/departments")).andExpect(status().isOk())
			.andExpect(jsonPath("$[*].id", hasItem(mine)))
			.andExpect(jsonPath("$[*].id", not(hasItem(other))));
		as(viewer, get("/api/v1/admin/departments/{id}", mine)).andExpect(status().isOk());
		as(viewer, get("/api/v1/admin/departments/{id}", other)).andExpect(status().isNotFound());
		send(viewer, post("/api/v1/admin/departments"), """
				{"code":"NOPE_DEPARTMENT","name":"No"}""").andExpect(status().isForbidden());
	}

	@Test
	void theApiRefusesThoseWithoutThePermission() throws Exception {
		Account support = api.staff("SUPPORT");
		as(support, get("/api/v1/admin/departments")).andExpect(status().isForbidden());
		as(support, get("/api/v1/admin/staff")).andExpect(status().isForbidden());
		as(support, get("/api/v1/admin/approvals")).andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/admin/departments")).andExpect(status().isUnauthorized());
	}

	@Test
	void staffSearchListsEmployeesOnlyWithoutNeedingToSeeInvestors() throws Exception {
		Account investor = api.register();
		Account employee = api.staff("SUPPORT");
		Account viewer = api.staff(roleName(role(admin, "STAFF_ACCOUNT_VIEW")));

		as(viewer, get("/api/v1/admin/staff/search").param("q", employee.email())).andExpect(status().isOk())
			.andExpect(jsonPath("$.content[*].id", hasItem(employee.id().toString())));
		as(viewer, get("/api/v1/admin/staff/search").param("q", investor.email())).andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(0));
		as(viewer, get("/api/v1/admin/users")).andExpect(status().isForbidden());
	}

	@Test
	void rolesKeepARevisionForEachChange() throws Exception {
		String id = role(admin, "REPORT_VIEW");
		send(admin, put("/api/v1/admin/roles/{id}", id), """
				{"description":"Wider","permissions":["REPORT_VIEW","LEDGER_VIEW"],"version":%d}"""
			.formatted(jdbc.queryForObject("SELECT version FROM roles WHERE id = ?::uuid", Long.class, id)))
			.andExpect(status().isOk()).andExpect(jsonPath("$.permissions.length()").value(2));

		as(admin, get("/api/v1/admin/roles/{id}/revisions", id)).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].revision").value(2))
			.andExpect(jsonPath("$[0].permissions.length()").value(2))
			.andExpect(jsonPath("$[1].permissions[0]").value("REPORT_VIEW"));
	}

	@Test
	void nobodyGrantsAPermissionTheyDoNotHold() throws Exception {
		String builder = role(admin, "ROLE_MANAGE");
		Account actor = api.staff(roleName(builder));
		send(actor, post("/api/v1/admin/roles"), """
				{"name":"%s","description":"Escalation","permissions":["FINANCE_ADJUST"]}""".formatted(unique("ESCALATE")))
			.andExpect(status().isForbidden());
	}

	@Test
	void reservedPermissionsAreMarkedAndGrantNothingYet() throws Exception {
		as(admin, get("/api/v1/admin/permissions")).andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.code == 'COMPLIANCE_CASE_VIEW')].reserved", hasItem(true)))
			.andExpect(jsonPath("$[?(@.code == 'LEDGER_VIEW')].reserved", hasItem(false)))
			.andExpect(jsonPath("$[?(@.code == 'FINANCE_ADJUST')].sensitive", hasItem(true)));
	}

	@Test
	void theFineGrainedLedgerPermissionIsEnforced() throws Exception {
		Account reader = api.staff(roleName(role(admin, "LEDGER_VIEW")));
		as(reader, get("/api/v1/admin/ledger/trial-balance")).andExpect(status().isOk());
		as(reader, get("/api/v1/admin/payouts")).andExpect(status().isForbidden());
	}

	@Test
	void changingARolesPermissionsEndsItsHoldersSessions() throws Exception {
		String id = role(admin, "LEDGER_VIEW");
		Account holder = api.staff(roleName(id));
		as(holder, get("/api/v1/admin/ledger/trial-balance")).andExpect(status().isOk());

		send(admin, put("/api/v1/admin/roles/{id}", id), """
				{"description":"Narrower","permissions":["REPORT_VIEW"],"version":%d}"""
			.formatted(jdbc.queryForObject("SELECT version FROM roles WHERE id = ?::uuid", Long.class, id)))
			.andExpect(status().isOk());

		as(holder, get("/api/v1/admin/ledger/trial-balance")).andExpect(status().isUnauthorized());
	}

	@Test
	void theSuperAdminRoleIsProtected() throws Exception {
		String id = jdbc.queryForObject("SELECT id::text FROM roles WHERE name = 'SUPER_ADMIN'", String.class);
		send(admin, put("/api/v1/admin/roles/{id}", id), """
				{"description":"Weakened","permissions":["REPORT_VIEW"],"version":0}""").andExpect(status().isForbidden());
		as(admin, delete("/api/v1/admin/roles/{id}", id)).andExpect(status().isForbidden());
	}

	@Test
	void theDashboardShowsOnlyWhatTheViewerMaySee() throws Exception {
		Account support = api.staff("SUPPORT");
		as(support, get("/api/v1/admin/dashboard")).andExpect(status().isOk())
			.andExpect(jsonPath("$[*].key", hasItem("support")))
			.andExpect(jsonPath("$[*].key", not(hasItem("finance"))));
		as(admin, get("/api/v1/admin/dashboard")).andExpect(status().isOk())
			.andExpect(jsonPath("$[*].key", hasItem("finance")))
			.andExpect(jsonPath("$[*].key", hasItem("platform")));
		as(api.register(), get("/api/v1/admin/dashboard")).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(0));
	}

}
