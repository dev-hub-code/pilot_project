package com.sealease.backend.role.service;

import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.permission.PermissionCode;
import com.sealease.backend.permission.entity.Permission;
import com.sealease.backend.role.dto.CreateStaffRequest;
import com.sealease.backend.role.dto.StaffCredentialsResponse;
import com.sealease.backend.role.dto.UserAuthorities;
import com.sealease.backend.role.entity.Role;
import com.sealease.backend.role.repository.RoleRepository;
import com.sealease.backend.user.event.CredentialsResetEvent;
import com.sealease.backend.user.service.UserAccountService;
import com.sealease.backend.user.service.UserAccountService.IssuedCredentials;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Staff accounts created by administrators. The account and its roles are created together; the
 * usual escalation guard applies (you can only grant permissions you hold). Credentials are a
 * temporary password, shown once, that must be replaced at first sign-in.
 */
@Service
public class StaffAccountService {

	private final UserAccountService accounts;
	private final UserRoleService userRoles;
	private final RoleRepository roles;
	private final ApplicationEventPublisher events;

	public StaffAccountService(UserAccountService accounts, UserRoleService userRoles, RoleRepository roles,
			ApplicationEventPublisher events) {
		this.accounts = accounts;
		this.userRoles = userRoles;
		this.roles = roles;
		this.events = events;
	}

	@Transactional
	public StaffCredentialsResponse create(UUID actorId, CreateStaffRequest request) {
		List<Role> requested = roles.findByNameIn(request.roles());
		boolean investorRole = requested.stream()
			.flatMap(r -> r.getPermissions().stream())
			.map(Permission::getCode)
			.anyMatch(PermissionCode.INVESTOR_PORTAL.name()::equals);
		if (investorRole) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Staff accounts cannot hold investor roles; investors register themselves");
		}
		IssuedCredentials issued = accounts.createStaffAccount(request.email(), request.firstName(), request.lastName(),
				actorId);
		// Validates the role names and applies the escalation guard; a refusal rolls the account back too.
		UserAuthorities granted = userRoles.replaceRoles(actorId, issued.account().id(), request.roles());
		if (granted.permissions().stream().noneMatch(PermissionCode::isAdministrative)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Give the staff member at least one staff role");
		}
		return view(issued, granted);
	}

	/**
	 * Issues a new temporary password to a staff member who forgot theirs (or let it expire) and ends
	 * their sessions. Only for staff accounts, and only by someone holding every permission they have.
	 */
	@Transactional
	public StaffCredentialsResponse resetPassword(UUID actorId, UUID userId) {
		if (actorId.equals(userId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "Change your own password from your account instead");
		}
		UserAuthorities authorities = userRoles.authoritiesOf(userId);
		if (authorities.permissions().stream().noneMatch(PermissionCode::isAdministrative)) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"Temporary passwords are only issued to staff accounts");
		}
		userRoles.requireActorCovers(actorId, userId);
		IssuedCredentials issued = accounts.issueTemporaryPassword(userId, actorId);
		events.publishEvent(new CredentialsResetEvent(userId));
		return view(issued, authorities);
	}

	private static StaffCredentialsResponse view(IssuedCredentials issued, UserAuthorities authorities) {
		return new StaffCredentialsResponse(issued.account().id(), issued.account().email(), issued.account().firstName(),
				issued.account().lastName(), authorities.roles(), issued.temporaryPassword(), issued.expiresAt());
	}

}
