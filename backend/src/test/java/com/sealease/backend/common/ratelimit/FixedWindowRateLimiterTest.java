package com.sealease.backend.common.ratelimit;

import com.sealease.backend.support.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FixedWindowRateLimiterTest {

	private final MutableClock clock = new MutableClock(Instant.parse("2026-10-07T10:00:00Z"));
	private final FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(3, Duration.ofMinutes(1), clock);

	@Test
	void allowsUpToLimitThenRejectsWithRetryAfter() {
		limiter.acquire("1.2.3.4");
		limiter.acquire("1.2.3.4");
		limiter.acquire("1.2.3.4");
		clock.advance(Duration.ofSeconds(20));

		assertThatThrownBy(() -> limiter.acquire("1.2.3.4"))
			.isInstanceOfSatisfying(RateLimitExceededException.class,
					ex -> assertThat(ex.retryAfter()).isEqualTo(Duration.ofSeconds(40)));
	}

	@Test
	void keysAreIndependent() {
		for (int i = 0; i < 3; i++) {
			limiter.acquire("a");
		}
		assertThatCode(() -> limiter.acquire("b")).doesNotThrowAnyException();
	}

	@Test
	void windowResets() {
		for (int i = 0; i < 3; i++) {
			limiter.acquire("a");
		}
		clock.advance(Duration.ofMinutes(1));
		assertThatCode(() -> limiter.acquire("a")).doesNotThrowAnyException();
	}

	@Test
	void resetForgetsKey() {
		for (int i = 0; i < 3; i++) {
			limiter.acquire("a");
		}
		limiter.reset("a");
		assertThatCode(() -> limiter.acquire("a")).doesNotThrowAnyException();
	}

}
