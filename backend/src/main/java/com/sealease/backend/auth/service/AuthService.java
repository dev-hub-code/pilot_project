package com.sealease.backend.auth.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.auth.config.AuthProperties;
import com.sealease.backend.auth.dto.AuthTokensResponse;
import com.sealease.backend.auth.dto.ChangePasswordRequest;
import com.sealease.backend.auth.dto.CurrentUserResponse;
import com.sealease.backend.auth.dto.LoginRequest;
import com.sealease.backend.auth.dto.RegisterRequest;
import com.sealease.backend.auth.entity.SessionRevocationReason;
import com.sealease.backend.auth.jwt.AccessTokenService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.ratelimit.FixedWindowRateLimiter;
import com.sealease.backend.common.web.ClientInfo;
import com.sealease.backend.referral.service.ReferralService;
import com.sealease.backend.role.dto.UserAuthorities;
import com.sealease.backend.role.service.UserRoleService;
import com.sealease.backend.security.AuthenticatedUser;
import com.sealease.backend.user.dto.UserAccount;
import com.sealease.backend.user.entity.UserStatus;
import com.sealease.backend.user.service.CredentialVerification;
import com.sealease.backend.user.service.EmailNormalizer;
import com.sealease.backend.user.service.UserAccountService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Authentication use cases. Transactions are demarcated explicitly with {@link TransactionTemplate}
 * because several flows must <em>commit</em> state (failure counters, theft revocations) and then
 * report a failure to the client - which declarative rollback-on-exception would undo.
 */
@Service
@EnableConfigurationProperties(AuthProperties.class)
public class AuthService {

	private static final String ENTITY_USER = "USER";
	private static final String ENTITY_SESSION = "AUTH_SESSION";

	private final UserAccountService accounts;
	private final UserRoleService userRoles;
	private final ReferralService referrals;
	private final SessionService sessions;
	private final AccessTokenService accessTokens;
	private final AuditService audit;
	private final AuthProperties properties;
	private final TransactionTemplate tx;

	private final FixedWindowRateLimiter loginPerIp;
	private final FixedWindowRateLimiter loginPerEmail;
	private final FixedWindowRateLimiter registerPerIp;
	private final FixedWindowRateLimiter refreshPerIp;
	private final FixedWindowRateLimiter passwordChangePerUser;

	public AuthService(UserAccountService accounts, UserRoleService userRoles, ReferralService referrals,
			SessionService sessions, AccessTokenService accessTokens, AuditService audit, AuthProperties properties,
			PlatformTransactionManager transactionManager, Clock clock) {
		this.accounts = accounts;
		this.userRoles = userRoles;
		this.referrals = referrals;
		this.sessions = sessions;
		this.accessTokens = accessTokens;
		this.audit = audit;
		this.properties = properties;
		this.tx = new TransactionTemplate(transactionManager);

		AuthProperties.RateLimits limits = Objects.requireNonNull(properties.rateLimit(), "app.auth.rate-limit");
		this.loginPerIp = limiter(limits.loginPerIp(), clock);
		this.loginPerEmail = limiter(limits.loginPerEmail(), clock);
		this.registerPerIp = limiter(limits.registerPerIp(), clock);
		this.refreshPerIp = limiter(limits.refreshPerIp(), clock);
		this.passwordChangePerUser = limiter(limits.passwordChangePerUser(), clock);
	}

	public AuthTokensResponse register(RegisterRequest request, ClientInfo client) {
		registerPerIp.acquire(keyOf(client));
		return tx.execute(status -> {
			UserAccount account = accounts.register(request.email(), request.password(), request.firstName(),
					request.lastName());
			userRoles.grantSystemRole(account.id(), properties.defaultRole());
			if (request.referralCode() != null && !request.referralCode().isBlank()) {
				referrals.link(account.id(), request.referralCode());
			}
			return openSession(account.id(), client);
		});
	}

	public AuthTokensResponse login(LoginRequest request, ClientInfo client) {
		String emailKey = EmailNormalizer.normalize(request.email());
		loginPerIp.acquire(keyOf(client));
		loginPerEmail.acquire(emailKey);

		// Commits on its own so failure counters and lockouts persist.
		CredentialVerification result = accounts.verifyCredentials(request.email(), request.password());
		return switch (result) {
			case CredentialVerification.Verified verified -> {
				loginPerEmail.reset(emailKey);
				yield tx.execute(status -> openSession(verified.account().id(), client));
			}
			case CredentialVerification.InvalidCredentials ignored ->
				throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password");
			case CredentialVerification.Locked ignored -> throw new BusinessException(ErrorCode.ACCOUNT_LOCKED,
					"Account temporarily locked after repeated failed sign-in attempts; try again later");
			case CredentialVerification.Inactive ignored ->
				throw new BusinessException(ErrorCode.ACCOUNT_DISABLED, "This account is not active");
			case CredentialVerification.TemporaryPasswordExpired ignored ->
				throw new BusinessException(ErrorCode.TEMPORARY_PASSWORD_EXPIRED,
						"Your temporary password has expired; ask an administrator for a new one");
		};
	}

	public AuthTokensResponse refresh(String refreshToken, ClientInfo client) {
		refreshPerIp.acquire(keyOf(client));
		// The callback returns null on rejection instead of throwing, so revocations commit.
		AuthTokensResponse tokens = tx.execute(status -> {
			SessionService.RotationResult result = sessions.rotate(refreshToken);
			return switch (result) {
				case SessionService.RotationResult.Rejected rejected -> {
					if (rejected.reuseRevoked()) {
						audit.record(AuditRecord.of(rejected.userId(), AuditAction.TOKEN_REFRESH_REUSE_DETECTED,
								ENTITY_USER, rejected.userId()));
					}
					yield null;
				}
				case SessionService.RotationResult.Rotated rotated -> {
					UserAccount account = accounts.getAccount(rotated.userId());
					if (account.status() != UserStatus.ACTIVE) {
						sessions.revoke(rotated.sessionId(), SessionRevocationReason.ACCOUNT_INACTIVE);
						yield null;
					}
					UserAuthorities authorities = userRoles.authoritiesOf(account.id());
					AccessTokenService.IssuedAccessToken access = accessTokens.issue(account.id(),
							rotated.sessionId(), authorities, account.mustChangePassword());
					yield toResponse(access, rotated.refreshToken());
				}
			};
		});
		if (tokens == null) {
			throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN, "Refresh token is invalid or expired");
		}
		return tokens;
	}

	/** Idempotent: unknown or already-revoked tokens are accepted silently. */
	public void logout(String refreshToken) {
		tx.executeWithoutResult(status -> sessions.revokeByRefreshToken(refreshToken, SessionRevocationReason.LOGOUT)
			.ifPresent(userId -> audit.record(AuditRecord.of(userId, AuditAction.LOGOUT, ENTITY_USER, userId))));
	}

	public void logoutAll(AuthenticatedUser user) {
		tx.executeWithoutResult(status -> {
			int revoked = sessions.revokeAll(user.userId(), SessionRevocationReason.LOGOUT_ALL);
			audit.record(AuditRecord.of(user.userId(), AuditAction.LOGOUT_ALL, ENTITY_USER, user.userId())
				.withNewValue(Map.of("sessionsRevoked", revoked)));
		});
	}

	/** Changes the password and signs out every other session; the current one stays valid. */
	public void changePassword(AuthenticatedUser user, ChangePasswordRequest request) {
		passwordChangePerUser.acquire(user.userId().toString());
		tx.executeWithoutResult(status -> {
			accounts.changePassword(user.userId(), request.currentPassword(), request.newPassword());
			int revoked = sessions.revokeAllExcept(user.userId(), user.sessionId(),
					SessionRevocationReason.PASSWORD_CHANGED);
			audit.record(AuditRecord.of(user.userId(), AuditAction.PASSWORD_CHANGE, ENTITY_USER, user.userId())
				.withNewValue(Map.of("otherSessionsRevoked", revoked)));
		});
	}

	public CurrentUserResponse currentUser(AuthenticatedUser user) {
		UserAccount account = accounts.getAccount(user.userId());
		UserAuthorities authorities = userRoles.authoritiesOf(user.userId());
		return new CurrentUserResponse(account.id(), account.email(), account.firstName(), account.lastName(),
				account.status().name(), authorities.roles(), authorities.permissions(), account.mustChangePassword());
	}

	private AuthTokensResponse openSession(UUID userId, ClientInfo client) {
		SessionService.OpenedSession session = sessions.open(userId, client);
		audit.record(AuditRecord.of(userId, AuditAction.LOGIN, ENTITY_SESSION, session.sessionId()));
		UserAuthorities authorities = userRoles.authoritiesOf(userId);
		AccessTokenService.IssuedAccessToken access = accessTokens.issue(userId, session.sessionId(), authorities,
				accounts.getAccount(userId).mustChangePassword());
		return toResponse(access, session.refreshToken());
	}

	private static AuthTokensResponse toResponse(AccessTokenService.IssuedAccessToken access,
			SessionService.IssuedRefreshToken refresh) {
		return new AuthTokensResponse("Bearer", access.value(), access.expiresAt(), refresh.value(),
				refresh.expiresAt());
	}

	private static String keyOf(ClientInfo client) {
		return client.ipAddress() == null ? "unknown" : client.ipAddress();
	}

	private static FixedWindowRateLimiter limiter(AuthProperties.Limit limit, Clock clock) {
		return new FixedWindowRateLimiter(limit.requests(), limit.window(), clock);
	}

}
