package com.sealease.backend.role.controller;

import com.sealease.backend.permission.controller.PermissionController;
import com.sealease.backend.permission.service.PermissionService;
import com.sealease.backend.role.dto.RoleResponse;
import com.sealease.backend.role.dto.UserAuthorities;
import com.sealease.backend.role.service.RoleService;
import com.sealease.backend.role.service.UserRoleService;
import com.sealease.backend.support.TestJwts;
import com.sealease.backend.support.WebSliceTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Permission matrix for the access-control administration endpoints. */
@WebMvcTest(controllers = { RoleController.class, UserRoleController.class, PermissionController.class })
@Import(WebSliceTestConfig.class)
class RoleAuthorizationTest {

	private static final String CREATE_BODY = """
			{"name":"AUDITOR","description":"Read-only auditors","permissions":["AUDIT_VIEW"]}
			""";

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private RoleService roleService;

	@MockitoBean
	private UserRoleService userRoleService;

	@MockitoBean
	private PermissionService permissionService;

	@Test
	void anonymousCallerIsUnauthorized() throws Exception {
		mvc.perform(get("/api/v1/admin/roles")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/admin/permissions")).andExpect(status().isUnauthorized());
	}

	@Test
	void investorCannotReachAdministration() throws Exception {
		var investor = TestJwts.userWith("INVESTOR_PORTAL");
		mvc.perform(get("/api/v1/admin/roles").with(investor)).andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/admin/permissions").with(investor)).andExpect(status().isForbidden());
		mvc.perform(post("/api/v1/admin/roles").with(investor).contentType(MediaType.APPLICATION_JSON)
				.content(CREATE_BODY))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		mvc.perform(put("/api/v1/admin/users/{id}/roles", UUID.randomUUID()).with(investor)
				.contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"SUPER_ADMIN\"]}"))
			.andExpect(status().isForbidden());
		verifyNoInteractions(roleService, userRoleService, permissionService);
	}

	@Test
	void viewPermissionAllowsReadingButNotWriting() throws Exception {
		when(roleService.list(any())).thenReturn(Page.empty());
		var viewer = TestJwts.userWith("ROLE_VIEW");

		mvc.perform(get("/api/v1/admin/roles").with(viewer)).andExpect(status().isOk());
		mvc.perform(post("/api/v1/admin/roles").with(viewer).contentType(MediaType.APPLICATION_JSON)
				.content(CREATE_BODY))
			.andExpect(status().isForbidden());
		mvc.perform(delete("/api/v1/admin/roles/{id}", UUID.randomUUID()).with(viewer))
			.andExpect(status().isForbidden());
	}

	@Test
	void managePermissionCreatesRoleAsActor() throws Exception {
		UUID actor = UUID.randomUUID();
		when(roleService.create(eq(actor), any()))
			.thenReturn(new RoleResponse(UUID.randomUUID(), "AUDITOR", "Read-only auditors", false,
					List.of("AUDIT_VIEW"), 0));

		mvc.perform(post("/api/v1/admin/roles").with(TestJwts.userWith(actor, "ROLE_MANAGE"))
				.contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("AUDITOR"));
		verify(roleService).create(eq(actor), any());
	}

	@Test
	void roleNamesMustBeUpperSnakeCase() throws Exception {
		mvc.perform(post("/api/v1/admin/roles").with(TestJwts.userWith("ROLE_MANAGE"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"auditors team\",\"description\":\"x\",\"permissions\":[]}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
	}

	@Test
	void assigningRolesRequiresAssignPermission() throws Exception {
		UUID actor = UUID.randomUUID();
		UUID target = UUID.randomUUID();
		when(userRoleService.replaceRoles(eq(actor), eq(target), any()))
			.thenReturn(new UserAuthorities(Set.of("SALES"), Set.of("LEAD_VIEW")));

		mvc.perform(put("/api/v1/admin/users/{id}/roles", target).with(TestJwts.userWith(actor, "ROLE_MANAGE"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"SALES\"]}"))
			.andExpect(status().isForbidden());
		mvc.perform(put("/api/v1/admin/users/{id}/roles", target).with(TestJwts.userWith(actor, "USER_ROLE_ASSIGN"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"SALES\"]}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.roles[0]").value("SALES"));
	}

	@Test
	void roleAuthorityCannotImpersonatePermission() throws Exception {
		// A token whose *role* is "VIEW" must not satisfy hasAuthority('ROLE_VIEW').
		var tokenWithRoleView = TestJwts.userWith().jwt(jwt -> jwt.claim("roles", List.of("VIEW")));
		mvc.perform(get("/api/v1/admin/roles").with(tokenWithRoleView)).andExpect(status().isForbidden());
	}

}
