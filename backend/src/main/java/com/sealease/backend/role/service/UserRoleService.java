package com.sealease.backend.role.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.permission.PermissionCode;
import com.sealease.backend.permission.entity.Permission;
import com.sealease.backend.role.dto.UserAuthorities;
import com.sealease.backend.role.entity.Role;
import com.sealease.backend.role.entity.UserRole;
import com.sealease.backend.role.event.UserAuthoritiesChangedEvent;
import com.sealease.backend.role.repository.RoleRepository;
import com.sealease.backend.role.repository.UserRoleRepository;
import com.sealease.backend.user.service.AuthorityGuard;
import com.sealease.backend.user.service.UserAccountService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Grants roles to users and answers "what may this user do?". */
@Service
public class UserRoleService implements AuthorityGuard {

	private final UserRoleRepository userRoles;
	private final RoleRepository roles;
	private final UserAccountService accounts;
	private final AuditService audit;
	private final ApplicationEventPublisher events;
	private final Clock clock;

	public UserRoleService(UserRoleRepository userRoles, RoleRepository roles, UserAccountService accounts,
			AuditService audit, ApplicationEventPublisher events, Clock clock) {
		this.userRoles = userRoles;
		this.roles = roles;
		this.accounts = accounts;
		this.audit = audit;
		this.events = events;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public UserAuthorities authoritiesOf(UUID userId) {
		List<UserRole> grants = userRoles.findByIdUserId(userId);
		Set<String> roleNames = grants.stream().map(g -> g.getRole().getName()).collect(Collectors.toSet());
		Set<String> permissions = grants.stream()
			.flatMap(g -> g.getRole().getPermissions().stream())
			.map(Permission::getCode)
			.collect(Collectors.toSet());
		return new UserAuthorities(roleNames, permissions);
	}

	@Transactional(readOnly = true)
	public boolean anyUserHasRole(String roleName) {
		return userRoles.existsByRoleName(roleName);
	}

	/** System grant without an acting user (self-registration, bootstrap). */
	@Transactional
	public void grantSystemRole(UUID userId, String roleName) {
		Role role = roles.findByName(roleName)
			.orElseThrow(() -> new IllegalStateException("Configured role does not exist: " + roleName));
		userRoles.save(new UserRole(userId, role, null, clock.instant()));
	}

	/**
	 * Replaces a user's roles. Guards against privilege escalation: the actor may not change their
	 * own roles, and may only grant or remove roles whose permissions they hold themselves.
	 */
	@Transactional
	public UserAuthorities replaceRoles(UUID actorId, UUID targetUserId, Set<String> requestedRoleNames) {
		if (actorId.equals(targetUserId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "You cannot change your own roles");
		}
		accounts.getAccount(targetUserId);

		Map<String, Role> requested = roles.findByNameIn(requestedRoleNames).stream()
			.collect(Collectors.toMap(Role::getName, Function.identity()));
		if (requested.size() != requestedRoleNames.size()) {
			Set<String> unknown = new TreeSet<>(requestedRoleNames);
			unknown.removeAll(requested.keySet());
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Unknown roles: " + unknown);
		}

		List<UserRole> current = userRoles.findByIdUserId(targetUserId);
		Set<String> currentNames = current.stream().map(g -> g.getRole().getName()).collect(Collectors.toSet());

		Set<Role> changed = new HashSet<>();
		requested.values().stream().filter(r -> !currentNames.contains(r.getName())).forEach(changed::add);
		current.stream().map(UserRole::getRole).filter(r -> !requested.containsKey(r.getName())).forEach(changed::add);
		if (changed.isEmpty()) {
			return authoritiesOf(targetUserId);
		}
		requireActorHoldsPermissionsOf(actorId, changed);

		Instant now = clock.instant();
		current.stream().filter(g -> !requested.containsKey(g.getRole().getName())).forEach(userRoles::delete);
		requested.values().stream()
			.filter(r -> !currentNames.contains(r.getName()))
			.forEach(r -> userRoles.save(new UserRole(targetUserId, r, actorId, now)));
		userRoles.flush();

		audit.record(AuditRecord.of(actorId, AuditAction.USER_ROLES_CHANGED, "USER", targetUserId)
			.withOldValue(Map.of("roles", new TreeSet<>(currentNames)))
			.withNewValue(Map.of("roles", new TreeSet<>(requested.keySet()))));
		events.publishEvent(new UserAuthoritiesChangedEvent(Set.of(targetUserId)));
		return authoritiesOf(targetUserId);
	}

	/** An actor may only act on accounts whose permissions are a subset of their own. */
	@Override
	@Transactional(readOnly = true)
	public void requireActorCovers(UUID actorId, UUID targetUserId) {
		requireActorHolds(actorId, authoritiesOf(targetUserId).permissions());
	}

	/** Shared escalation guard, also used when editing a role's permissions. */
	void requireActorHolds(UUID actorId, Set<String> permissionCodes) {
		Set<String> actorPermissions = authoritiesOf(actorId).permissions();
		Set<String> missing = new TreeSet<>(permissionCodes);
		missing.removeIf(code -> !PermissionCode.isAdministrative(code));
		missing.removeAll(actorPermissions);
		if (!missing.isEmpty()) {
			throw new BusinessException(ErrorCode.FORBIDDEN,
					"You cannot grant or revoke permissions you do not hold: " + missing);
		}
	}

	private void requireActorHoldsPermissionsOf(UUID actorId, Set<Role> changedRoles) {
		requireActorHolds(actorId, changedRoles.stream()
			.flatMap(r -> r.getPermissions().stream())
			.map(Permission::getCode)
			.collect(Collectors.toSet()));
	}

}
