package com.sealease.backend.common.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory fixed-window rate limiter keyed by an arbitrary string (IP address, email, ...).
 *
 * <p>Limits are per application instance. That is sufficient for brute-force throttling in front
 * of the database-backed account lockout (which is global); a shared store (e.g. Redis) can
 * replace this class without changing callers once the platform runs multiple instances.
 */
public class FixedWindowRateLimiter {

	/** Expired windows are purged when the map grows beyond this size, bounding memory. */
	private static final int PURGE_THRESHOLD = 10_000;

	private final int limit;
	private final Duration window;
	private final Clock clock;
	private final Map<String, Window> windows = new ConcurrentHashMap<>();

	public FixedWindowRateLimiter(int limit, Duration window, Clock clock) {
		if (limit < 1) {
			throw new IllegalArgumentException("limit must be positive");
		}
		this.limit = limit;
		this.window = window;
		this.clock = clock;
	}

	/**
	 * Counts one attempt for {@code key}.
	 * @throws RateLimitExceededException when the key has exhausted its allowance
	 */
	public void acquire(String key) {
		Instant now = clock.instant();
		Window current = windows.compute(key,
				(k, existing) -> existing == null || !now.isBefore(existing.resetsAt())
						? new Window(1, now.plus(window))
						: new Window(existing.count() + 1, existing.resetsAt()));
		if (windows.size() > PURGE_THRESHOLD) {
			windows.values().removeIf(w -> !now.isBefore(w.resetsAt()));
		}
		if (current.count() > limit) {
			throw new RateLimitExceededException(Duration.between(now, current.resetsAt()));
		}
	}

	/** Forgets a key, e.g. after a successful login. */
	public void reset(String key) {
		windows.remove(key);
	}

	private record Window(int count, Instant resetsAt) {
	}

}
