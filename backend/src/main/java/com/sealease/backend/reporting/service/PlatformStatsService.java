package com.sealease.backend.reporting.service;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyProperties;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.reporting.dto.PlatformStats;
import com.sealease.backend.common.cache.Caches;
import jakarta.persistence.EntityManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Aggregates for the public website. The endpoint is unauthenticated, so results are cached for a few minutes
 * ({@link Caches#PLATFORM_STATS}) and anonymous traffic never turns into a stream of aggregate queries.
 */
@Service
public class PlatformStatsService {

	private final EntityManager entityManager;
	private final MoneyProperties money;

	public PlatformStatsService(EntityManager entityManager, MoneyProperties money) {
		this.entityManager = entityManager;
		this.money = money;
	}

	@Cacheable(cacheNames = Caches.Names.PLATFORM_STATS, key = "'current'", sync = true)
	@Transactional(readOnly = true)
	public PlatformStats current() {
		String currency = money.defaultCurrency().getCurrencyCode();
		// Rent (and capital) credited so far, and the units it has been credited on.
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

}
