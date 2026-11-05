package com.sealease.backend.common.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/**
 * In-memory caches ({@link Caches}), each with its own time to live and size. Writes and clears made inside a
 * transaction take effect when it commits, so a rolled-back change never clears a cache and a reader never caches a
 * value that is replaced by a commit it raced with for longer than the entry lives.
 * <p>
 * {@code app.cache.enabled=false} turns caching off (every call reads the database), for diagnosing.
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching
public class CacheConfig {

	@Bean
	@ConditionalOnProperty(prefix = "app.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
	CacheManager cacheManager(ObjectProvider<MeterRegistry> meters) {
		MeterRegistry registry = meters.getIfAvailable();
		SimpleCacheManager manager = new SimpleCacheManager();
		manager.setCaches(Arrays.stream(Caches.values()).map(c -> {
			com.github.benmanes.caffeine.cache.Cache<Object, Object> store = Caffeine.newBuilder()
				.expireAfterWrite(c.ttl())
				.maximumSize(c.maximumSize())
				.recordStats()
				.build();
			if (registry != null) {
				CaffeineCacheMetrics.monitor(registry, store, c.cacheName());
			}
			// Null results are not cached: a missing row is looked up again next time.
			return new TransactionAwareCacheDecorator(new CaffeineCache(c.cacheName(), store, false));
		}).toList());
		return manager;
	}

	@Bean
	@ConditionalOnProperty(prefix = "app.cache", name = "enabled", havingValue = "false")
	CacheManager noCacheManager() {
		return new NoOpCacheManager();
	}

}
