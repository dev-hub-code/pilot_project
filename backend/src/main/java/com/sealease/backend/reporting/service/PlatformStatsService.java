package com.sealease.backend.reporting.service;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyProperties;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.reporting.dto.PlatformStats;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Aggregates for the public website. The endpoint is unauthenticated, so results are held for a few minutes
 * and anonymous traffic never turns into a stream of aggregate queries.
 */
@Service
public class PlatformStatsService {

	private static final Duration CACHE_FOR = Duration.ofMinutes(5);

	private final EntityManager entityManager;
	private final MoneyProperties money;
	private final Clock clock;

	private volatile Cached cached;

	public PlatformStatsService(EntityManager entityManager, MoneyProperties money, Clock clock) {
		this.entityManager = entityManager;
		this.money = money;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public PlatformStats current() {
		Instant now = clock.instant();
		Cached snapshot = cached;
		if (snapshot == null || snapshot.expiresAt().isBefore(now)) {
			snapshot = new Cached(compute(), now.plus(CACHE_FOR));
			cached = snapshot;
		}
		return snapshot.stats();
	}

	private PlatformStats compute() {
		String currency = money.defaultCurrency().getCurrencyCode();
		// Rent (and capital) credited so far, and the containers it has been credited on.
		Object[] payouts = entityManager.createQuery("""
				select count(a), sum(a.creditedRent + a.creditedCapital) from RentAccrual a
				where a.currency = :currency and a.creditedUntil > a.startsAt
				""", Object[].class)
			.setParameter("currency", currency)
			.getSingleResult();
		Object[] holdings = entityManager.createQuery(
				"select count(h), count(distinct h.userId) from Holding h", Object[].class)
			.getSingleResult();

		BigDecimal total = payouts[1] == null ? BigDecimal.ZERO : (BigDecimal) payouts[1];
		Money paid = Money.of(total, money.defaultCurrency());
		return new PlatformStats(MoneyResponse.from(paid), (Long) payouts[0], (Long) holdings[1], (Long) holdings[0]);
	}

	private record Cached(PlatformStats stats, Instant expiresAt) {
	}

}
