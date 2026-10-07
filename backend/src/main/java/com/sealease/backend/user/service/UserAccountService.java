package com.sealease.backend.user.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.user.dto.UserAccount;
import com.sealease.backend.user.entity.User;
import com.sealease.backend.user.entity.UserStatus;
import com.sealease.backend.user.repository.UserRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Account lifecycle and credential checks. The {@link User} entity never leaves this module;
 * other modules receive {@link UserAccount} views.
 */
@Service
@EnableConfigurationProperties(LockoutProperties.class)
public class UserAccountService {

	private static final String ENTITY = "USER";

	private final UserRepository users;
	private final UserProfileService profiles;
	private final PasswordEncoder passwordEncoder;
	private final PasswordPolicy passwordPolicy;
	private final LockoutProperties lockout;
	private final AuditService audit;
	private final Clock clock;

	/**
	 * Hash compared against when the email is unknown, so that response time does not reveal
	 * whether an account exists.
	 */
	private final String dummyHash;

	public UserAccountService(UserRepository users, UserProfileService profiles, PasswordEncoder passwordEncoder,
			PasswordPolicy passwordPolicy, LockoutProperties lockout, AuditService audit, Clock clock) {
		this.users = users;
		this.profiles = profiles;
		this.passwordEncoder = passwordEncoder;
		this.passwordPolicy = passwordPolicy;
		this.lockout = lockout;
		this.audit = audit;
		this.clock = clock;
		this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
	}

	@Transactional
	public UserAccount register(String email, String rawPassword, String firstName, String lastName) {
		String normalizedEmail = EmailNormalizer.normalize(email);
		passwordPolicy.validate(rawPassword, normalizedEmail);
		if (users.existsByEmail(normalizedEmail)) {
			throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED, "An account with this email already exists");
		}
		User user = users.saveAndFlush(new User(normalizedEmail, passwordEncoder.encode(rawPassword),
				firstName.strip(), lastName.strip(), clock.instant()));
		profiles.createFor(user);
		audit.record(AuditRecord.of(user.getId(), AuditAction.USER_REGISTERED, ENTITY, user.getId())
			.withNewValue(Map.of("email", normalizedEmail, "status", user.getStatus())));
		return UserAccount.from(user);
	}

	/**
	 * Verifies a sign-in attempt, maintaining failure counters and lockout. The user row is locked
	 * for the duration so that concurrent attempts are counted exactly.
	 */
	@Transactional
	public CredentialVerification verifyCredentials(String email, String rawPassword) {
		Optional<UUID> userId = users.findByEmail(EmailNormalizer.normalize(email)).map(User::getId);
		if (userId.isEmpty()) {
			passwordEncoder.matches(rawPassword, dummyHash);
			return new CredentialVerification.InvalidCredentials();
		}
		User user = users.findByIdForUpdate(userId.get()).orElseThrow();
		Instant now = clock.instant();

		if (user.isLockedAt(now)) {
			return new CredentialVerification.Locked(user.getLockedUntil());
		}
		if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
			boolean nowLocked = user.registerFailedLogin(lockout.maxFailedAttempts(), now.plus(lockout.lockDuration()));
			audit.record(AuditRecord.of(null, AuditAction.LOGIN_FAILED, ENTITY, user.getId()));
			if (nowLocked) {
				audit.record(AuditRecord.of(null, AuditAction.ACCOUNT_LOCKED, ENTITY, user.getId())
					.withNewValue(Map.of("lockedUntil", user.getLockedUntil().toString())));
				return new CredentialVerification.Locked(user.getLockedUntil());
			}
			return new CredentialVerification.InvalidCredentials();
		}
		// Correct password from here on: reveal account state only to someone who knows it.
		if (user.getStatus() != UserStatus.ACTIVE) {
			return new CredentialVerification.Inactive();
		}
		if (passwordEncoder.upgradeEncoding(user.getPasswordHash())) {
			user.upgradePasswordHash(passwordEncoder.encode(rawPassword));
		}
		user.registerSuccessfulLogin(now);
		return new CredentialVerification.Verified(UserAccount.from(user));
	}

	/**
	 * Changes the password after re-verifying the current one. The caller is responsible for
	 * revoking other sessions and auditing within the same transaction.
	 */
	@Transactional
	public void changePassword(UUID userId, String currentPassword, String newPassword) {
		User user = users.findByIdForUpdate(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
		if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
			throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Current password is incorrect");
		}
		if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "New password must differ from the current one");
		}
		passwordPolicy.validate(newPassword, user.getEmail());
		user.changePasswordHash(passwordEncoder.encode(newPassword), clock.instant());
	}

	@Transactional(readOnly = true)
	public UserAccount getAccount(UUID userId) {
		return users.findById(userId).map(UserAccount::from)
			.orElseThrow(() -> new ResourceNotFoundException("User", userId));
	}

	@Transactional(readOnly = true)
	public Map<UUID, UserAccount> getAccounts(Collection<UUID> userIds) {
		return users.findAllById(userIds).stream()
			.collect(Collectors.toMap(User::getId, UserAccount::from));
	}

	@Transactional(readOnly = true)
	public Optional<UserAccount> findByEmail(String email) {
		return users.findByEmail(EmailNormalizer.normalize(email)).map(UserAccount::from);
	}

	/** Creates an account without self-registration checks; used by system bootstrap only. */
	@Transactional
	public UserAccount createSystemAccount(String email, String rawPassword, String firstName, String lastName) {
		String normalizedEmail = EmailNormalizer.normalize(email);
		passwordPolicy.validate(rawPassword, normalizedEmail);
		User user = users.saveAndFlush(new User(normalizedEmail, passwordEncoder.encode(rawPassword),
				firstName, lastName, clock.instant()));
		profiles.createFor(user);
		audit.record(AuditRecord.of(null, AuditAction.USER_REGISTERED, ENTITY, user.getId())
			.withNewValue(Map.of("email", normalizedEmail, "source", "BOOTSTRAP")));
		return UserAccount.from(user);
	}

}
