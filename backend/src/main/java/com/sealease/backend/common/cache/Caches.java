package com.sealease.backend.common.cache;

import java.time.Duration;

/**
 * Every cache in the application, with how long an entry is kept and how many are kept at most.
 * <p>
 * Caches are in memory and per instance: a write clears the cache on the instance that made it, and the other
 * instances see the change once their entry expires. So only data that is public, read often, changed rarely and
 * harmless to show for a little while after it changes is cached here. Balances, payments, withdrawals, fees, sessions
 * and permissions are never cached.
 */
public enum Caches {

	/** Open online plans on the website and the open API (terms only; no stock or investor data). */
	PUBLIC_PLANS(Names.PUBLIC_PLANS, Duration.ofSeconds(60), 200),
	/** The terms and conditions shown; an account always accepts the version read fresh at sign-up. */
	TERMS(Names.TERMS, Duration.ofSeconds(60), 1),
	/** How often rent is credited, as the website shows it; crediting itself always reads the setting fresh. */
	PUBLIC_RENT_CREDIT(Names.PUBLIC_RENT_CREDIT, Duration.ofSeconds(60), 1),
	/** The track record on the website: aggregates over all holdings, too costly to run per visitor. */
	PLATFORM_STATS(Names.PLATFORM_STATS, Duration.ofMinutes(5), 1),
	/** Countries, states and cities: reference data that changes only with a migration. */
	LOCATIONS(Names.LOCATIONS, Duration.ofHours(24), 5_000);

	private final String cacheName;
	private final Duration ttl;
	private final long maximumSize;

	Caches(String cacheName, Duration ttl, long maximumSize) {
		this.cacheName = cacheName;
		this.ttl = ttl;
		this.maximumSize = maximumSize;
	}

	public String cacheName() {
		return cacheName;
	}

	public Duration ttl() {
		return ttl;
	}

	public long maximumSize() {
		return maximumSize;
	}

	/** The names, as constants for annotations. */
	public static final class Names {

		public static final String PUBLIC_PLANS = "public-plans";
		public static final String TERMS = "terms";
		public static final String PUBLIC_RENT_CREDIT = "public-rent-credit";
		public static final String PLATFORM_STATS = "platform-stats";
		public static final String LOCATIONS = "locations";

		private Names() {
		}

	}

}
