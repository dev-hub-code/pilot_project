package com.sealease.backend.auth.service;

import com.sealease.backend.auth.config.SessionProperties;
import com.sealease.backend.auth.entity.AuthSession;
import com.sealease.backend.auth.entity.RefreshToken;
import com.sealease.backend.auth.entity.SessionRevocationReason;
import com.sealease.backend.auth.repository.AuthSessionRepository;
import com.sealease.backend.auth.repository.RefreshTokenRepository;
import com.sealease.backend.common.web.ClientInfo;
import com.sealease.backend.role.event.UserAuthoritiesChangedEvent;
import com.sealease.backend.user.entity.UserStatus;
import com.sealease.backend.user.event.UserStatusChangedEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Login sessions and their refresh-token chains.
 *
 * <p>Refresh tokens are single-use. Each successful refresh consumes the presented token and
 * issues a successor in the same session. If a consumed token is presented again after the grace
 * period, it has most likely been stolen (either the thief or the user already used it), so the
 * entire session is revoked - cutting off both parties - and the user must sign in again.
 */
@Service
@EnableConfigurationProperties(SessionProperties.class)
public class SessionService {

	private final AuthSessionRepository sessions;
	private final RefreshTokenRepository refreshTokens;
	private final SessionProperties properties;
	private final Clock clock;

	public SessionService(AuthSessionRepository sessions, RefreshTokenRepository refreshTokens,
			SessionProperties properties, Clock clock) {
		this.sessions = sessions;
		this.refreshTokens = refreshTokens;
		this.properties = properties;
		this.clock = clock;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public OpenedSession open(UUID userId, ClientInfo client) {
		Instant now = clock.instant();
		// Flushed immediately: refresh_tokens references it by plain id column, which Hibernate's
		// insert ordering cannot see as a dependency.
		AuthSession session = sessions.saveAndFlush(new AuthSession(userId, now, now.plus(properties.maxLifetime()),
				client.ipAddress(), client.userAgent()));
		IssuedRefreshToken refresh = issueRefreshToken(session, now);
		return new OpenedSession(session.getId(), refresh);
	}

	/**
	 * Consumes a refresh token and issues its successor. Callers must commit the transaction even
	 * when the outcome is a failure, so that theft-triggered revocations persist.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public RotationResult rotate(String presentedToken) {
		Instant now = clock.instant();
		Optional<RefreshToken> found = refreshTokens.findByTokenHashForUpdate(OpaqueTokens.hash(presentedToken));
		if (found.isEmpty()) {
			return new RotationResult.Rejected(null, false);
		}
		RefreshToken token = found.get();
		AuthSession session = sessions.findByIdForUpdate(token.getSessionId()).orElseThrow();

		if (token.isConsumed()) {
			boolean withinGrace = now.isBefore(token.getConsumedAt().plus(properties.reuseGracePeriod()));
			if (withinGrace) {
				return new RotationResult.Rejected(session.getUserId(), false);
			}
			boolean wasActive = session.isActiveAt(now);
			session.revoke(SessionRevocationReason.REFRESH_TOKEN_REUSE, now);
			return new RotationResult.Rejected(session.getUserId(), wasActive);
		}
		if (token.isExpiredAt(now) || !session.isActiveAt(now)) {
			return new RotationResult.Rejected(session.getUserId(), false);
		}

		IssuedRefreshToken successor = issueRefreshToken(session, now);
		token.consume(now, successor.id());
		session.touch(now);
		return new RotationResult.Rotated(session.getUserId(), session.getId(), successor);
	}

	/** Revokes the session that owns the given refresh token, if any. */
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<UUID> revokeByRefreshToken(String presentedToken, SessionRevocationReason reason) {
		return refreshTokens.findByTokenHashForUpdate(OpaqueTokens.hash(presentedToken))
			.flatMap(token -> sessions.findByIdForUpdate(token.getSessionId()))
			.map(session -> {
				session.revoke(reason, clock.instant());
				return session.getUserId();
			});
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void revoke(UUID sessionId, SessionRevocationReason reason) {
		sessions.findByIdForUpdate(sessionId).ifPresent(session -> session.revoke(reason, clock.instant()));
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public int revokeAll(UUID userId, SessionRevocationReason reason) {
		return sessions.revokeAllForUsers(Set.of(userId), reason, clock.instant());
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public int revokeAllExcept(UUID userId, UUID keepSessionId, SessionRevocationReason reason) {
		return sessions.revokeOthersForUser(userId, keepSessionId, reason, clock.instant());
	}

	/** Access tokens embed authorities, so changed users must re-authenticate. */
	@EventListener
	@Transactional(propagation = Propagation.MANDATORY)
	public void onAuthoritiesChanged(UserAuthoritiesChangedEvent event) {
		if (!event.userIds().isEmpty()) {
			sessions.revokeAllForUsers(event.userIds(), SessionRevocationReason.AUTHORITIES_CHANGED, clock.instant());
		}
	}

	/** A suspended or disabled account loses every session immediately. */
	@EventListener
	@Transactional(propagation = Propagation.MANDATORY)
	public void onUserStatusChanged(UserStatusChangedEvent event) {
		if (event.newStatus() != UserStatus.ACTIVE) {
			sessions.revokeAllForUsers(Set.of(event.userId()), SessionRevocationReason.ACCOUNT_INACTIVE, clock.instant());
		}
	}

	@Transactional(readOnly = true)
	public boolean isActive(UUID sessionId) {
		return sessions.isActive(sessionId, clock.instant());
	}

	private IssuedRefreshToken issueRefreshToken(AuthSession session, Instant now) {
		Instant idleExpiry = now.plus(properties.refreshTokenTtl());
		Instant expiresAt = idleExpiry.isBefore(session.getExpiresAt()) ? idleExpiry : session.getExpiresAt();
		String value = OpaqueTokens.generate();
		RefreshToken token = refreshTokens.save(new RefreshToken(session.getId(), OpaqueTokens.hash(value), now, expiresAt));
		return new IssuedRefreshToken(token.getId(), value, expiresAt);
	}

	public record IssuedRefreshToken(UUID id, String value, Instant expiresAt) {

		@Override
		public String toString() {
			return "IssuedRefreshToken[id=" + id + ", expiresAt=" + expiresAt + "]";
		}

	}

	public record OpenedSession(UUID sessionId, IssuedRefreshToken refreshToken) {
	}

	public sealed interface RotationResult {

		record Rotated(UUID userId, UUID sessionId, IssuedRefreshToken refreshToken) implements RotationResult {
		}

		/**
		 * @param userId       owner of the token's session, when the token was recognised
		 * @param reuseRevoked the session was just revoked because a consumed token was replayed
		 */
		record Rejected(UUID userId, boolean reuseRevoked) implements RotationResult {
		}

	}

}
