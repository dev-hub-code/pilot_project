package com.sealease.backend.auth.service;

import com.sealease.backend.auth.config.SessionProperties;
import com.sealease.backend.auth.entity.AuthSession;
import com.sealease.backend.auth.entity.RefreshToken;
import com.sealease.backend.auth.entity.SessionRevocationReason;
import com.sealease.backend.auth.repository.AuthSessionRepository;
import com.sealease.backend.auth.repository.RefreshTokenRepository;
import com.sealease.backend.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SessionServiceTest {

	private static final String PRESENTED = "presented-token";

	private final MutableClock clock = new MutableClock(Instant.parse("2026-10-07T10:00:00Z"));
	private final AuthSessionRepository sessions = mock(AuthSessionRepository.class);
	private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
	private final SessionProperties properties = new SessionProperties(Duration.ofDays(7), Duration.ofDays(30),
			Duration.ofSeconds(10));
	private final SessionService service = new SessionService(sessions, refreshTokens, properties, clock);

	private final UUID userId = UUID.randomUUID();
	private AuthSession session;
	private RefreshToken token;

	@BeforeEach
	void setUp() {
		Instant now = clock.instant();
		session = new AuthSession(userId, now, now.plus(Duration.ofDays(30)), "10.0.0.1", "test");
		ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
		token = new RefreshToken(session.getId(), OpaqueTokens.hash(PRESENTED), now, now.plus(Duration.ofDays(7)));
		ReflectionTestUtils.setField(token, "id", UUID.randomUUID());

		when(refreshTokens.findByTokenHashForUpdate(anyString())).thenAnswer(inv ->
				inv.getArgument(0).equals(OpaqueTokens.hash(PRESENTED)) ? Optional.of(token) : Optional.empty());
		when(sessions.findByIdForUpdate(session.getId())).thenReturn(Optional.of(session));
		when(refreshTokens.save(any(RefreshToken.class))).thenAnswer(inv -> {
			RefreshToken saved = inv.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
			return saved;
		});
	}

	@Test
	void rotationConsumesTokenAndIssuesSuccessor() {
		SessionService.RotationResult result = service.rotate(PRESENTED);

		assertThat(result).isInstanceOfSatisfying(SessionService.RotationResult.Rotated.class, rotated -> {
			assertThat(rotated.userId()).isEqualTo(userId);
			assertThat(rotated.refreshToken().value()).isNotEqualTo(PRESENTED).hasSizeGreaterThanOrEqualTo(43);
		});
		assertThat(token.isConsumed()).isTrue();
		assertThat(session.isActiveAt(clock.instant())).isTrue();
	}

	@Test
	void replayWithinGracePeriodIsRejectedWithoutRevokingSession() {
		service.rotate(PRESENTED);
		clock.advance(Duration.ofSeconds(5));

		SessionService.RotationResult replay = service.rotate(PRESENTED);

		assertThat(replay).isEqualTo(new SessionService.RotationResult.Rejected(userId, false));
		assertThat(session.isActiveAt(clock.instant())).isTrue();
	}

	@Test
	void replayAfterGracePeriodRevokesSessionAsTheft() {
		service.rotate(PRESENTED);
		clock.advance(Duration.ofSeconds(11));

		SessionService.RotationResult replay = service.rotate(PRESENTED);

		assertThat(replay).isEqualTo(new SessionService.RotationResult.Rejected(userId, true));
		assertThat(session.getRevokedReason()).isEqualTo(SessionRevocationReason.REFRESH_TOKEN_REUSE);
	}

	@Test
	void unknownTokenIsRejected() {
		assertThat(service.rotate("never-issued")).isEqualTo(new SessionService.RotationResult.Rejected(null, false));
	}

	@Test
	void expiredTokenIsRejected() {
		clock.advance(Duration.ofDays(7));
		assertThat(service.rotate(PRESENTED)).isInstanceOf(SessionService.RotationResult.Rejected.class);
		assertThat(token.isConsumed()).isFalse();
	}

	@Test
	void tokenOfRevokedSessionIsRejected() {
		session.revoke(SessionRevocationReason.LOGOUT, clock.instant());
		assertThat(service.rotate(PRESENTED)).isInstanceOf(SessionService.RotationResult.Rejected.class);
	}

	@Test
	void successorNeverOutlivesSession() {
		clock.advance(Duration.ofDays(27));
		token = new RefreshToken(session.getId(), OpaqueTokens.hash(PRESENTED), clock.instant(),
				clock.instant().plus(Duration.ofDays(1)));
		ReflectionTestUtils.setField(token, "id", UUID.randomUUID());

		var rotated = (SessionService.RotationResult.Rotated) service.rotate(PRESENTED);

		assertThat(rotated.refreshToken().expiresAt()).isEqualTo(session.getExpiresAt());
	}

}
