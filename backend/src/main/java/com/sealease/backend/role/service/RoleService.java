package com.sealease.backend.role.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.permission.PermissionCode;
import com.sealease.backend.permission.entity.Permission;
import com.sealease.backend.permission.service.PermissionService;
import com.sealease.backend.role.dto.CreateRoleRequest;
import com.sealease.backend.role.dto.RoleResponse;
import com.sealease.backend.role.dto.UpdateRoleRequest;
import com.sealease.backend.role.entity.Role;
import com.sealease.backend.role.event.UserAuthoritiesChangedEvent;
import com.sealease.backend.role.repository.RoleRepository;
import com.sealease.backend.role.repository.UserRoleRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RoleService {

	private static final String ENTITY = "ROLE";

	private final RoleRepository roles;
	private final UserRoleRepository userRoles;
	private final PermissionService permissions;
	private final UserRoleService userRoleService;
	private final AuditService audit;
	private final ApplicationEventPublisher events;

	public RoleService(RoleRepository roles, UserRoleRepository userRoles, PermissionService permissions,
			UserRoleService userRoleService, AuditService audit, ApplicationEventPublisher events) {
		this.roles = roles;
		this.userRoles = userRoles;
		this.permissions = permissions;
		this.userRoleService = userRoleService;
		this.audit = audit;
		this.events = events;
	}

	@Transactional(readOnly = true)
	public Page<RoleResponse> list(Pageable pageable) {
		return roles.findAllBy(pageable).map(RoleResponse::from);
	}

	@Transactional(readOnly = true)
	public RoleResponse get(UUID id) {
		return RoleResponse.from(load(id));
	}

	@Transactional
	public RoleResponse create(UUID actorId, CreateRoleRequest request) {
		if (roles.existsByName(request.name())) {
			throw new BusinessException(ErrorCode.CONFLICT, "A role with this name already exists");
		}
		Set<Permission> granted = permissions.resolve(request.permissions());
		requireNotMixed(codes(granted));
		userRoleService.requireActorHolds(actorId, codes(granted));

		Role role = roles.saveAndFlush(new Role(request.name(), request.description().strip(), granted));
		audit.record(AuditRecord.of(actorId, AuditAction.ROLE_CREATED, ENTITY, role.getId())
			.withNewValue(snapshot(role)));
		return RoleResponse.from(role);
	}

	@Transactional
	public RoleResponse update(UUID actorId, UUID roleId, UpdateRoleRequest request) {
		Role role = load(roleId);
		if (role.getVersion() != request.version()) {
			throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION,
					"The role was modified concurrently; reload and retry");
		}
		Set<Permission> newPermissions = permissions.resolve(request.permissions());
		Set<String> before = codes(role.getPermissions());
		Set<String> after = codes(newPermissions);
		requireNotMixed(after);

		Set<String> changedCodes = new HashSet<>(before);
		changedCodes.addAll(after);
		Set<String> unchanged = new HashSet<>(before);
		unchanged.retainAll(after);
		changedCodes.removeAll(unchanged);
		userRoleService.requireActorHolds(actorId, changedCodes);

		Map<String, Object> oldSnapshot = snapshot(role);
		role.update(request.description().strip(), newPermissions);
		roles.flush();

		audit.record(AuditRecord.of(actorId, AuditAction.ROLE_UPDATED, ENTITY, role.getId())
			.withOldValue(oldSnapshot)
			.withNewValue(snapshot(role)));
		if (!changedCodes.isEmpty()) {
			events.publishEvent(new UserAuthoritiesChangedEvent(Set.copyOf(userRoles.findUserIdsByRoleId(roleId))));
		}
		return RoleResponse.from(role);
	}

	@Transactional
	public void delete(UUID actorId, UUID roleId) {
		Role role = load(roleId);
		if (role.isSystem()) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "System roles cannot be deleted");
		}
		if (userRoles.existsByIdRoleId(roleId)) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"Role is assigned to users; unassign it first");
		}
		userRoleService.requireActorHolds(actorId, codes(role.getPermissions()));
		audit.record(AuditRecord.of(actorId, AuditAction.ROLE_DELETED, ENTITY, role.getId())
			.withOldValue(snapshot(role)));
		roles.delete(role);
	}

	private Role load(UUID id) {
		return roles.findWithPermissionsById(id).orElseThrow(() -> new ResourceNotFoundException("Role", id));
	}

	private static Set<String> codes(Set<Permission> permissions) {
		return permissions.stream().map(Permission::getCode).collect(Collectors.toSet());
	}

	private static Map<String, Object> snapshot(Role role) {
		List<String> codes = codes(role.getPermissions()).stream().sorted().toList();
		return Map.of("name", role.getName(), "description", role.getDescription(), "permissions", codes);
	}

	/** A role grants either the investor portal or staff permissions, so no account can be both. */
	private static void requireNotMixed(Set<String> codes) {
		if (PermissionCode.mixesInvestorAndStaff(codes)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"A role cannot combine the investor portal with staff permissions");
		}
	}

}
