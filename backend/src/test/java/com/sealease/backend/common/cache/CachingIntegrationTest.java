package com.sealease.backend.common.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import com.sealease.backend.earning.service.RentCreditSettingsService;
import com.sealease.backend.investment.dto.PlanView;
import com.sealease.backend.investment.service.PlanCatalogService;
import com.sealease.backend.support.IntegrationTest;
import com.sealease.backend.support.OfferingFixtures;
import com.sealease.backend.support.TestApi;
import com.sealease.backend.support.TestApi.Account;
import com.sealease.backend.terms.dto.TermsRequest;
import com.sealease.backend.terms.service.TermsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Public, read-mostly data is served from memory, and a change made through the application clears it at once.
 * Rows changed behind the application's back (here, by SQL) show only once the entry expires, which is why only data
 * that is harmless to show slightly late is cached.
 */
@IntegrationTest
class CachingIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private CacheManager caches;

	@Autowired
	private PlanCatalogService plans;

	@Autowired
	private TermsService terms;

	@Autowired
	private RentCreditSettingsService rentCredit;

	@Autowired
	private TransactionTemplate tx;

	private TestApi api;

	private Account admin;

	@BeforeEach
	void setUp() throws Exception {
		api = new TestApi(mvc, jdbc);
		admin = api.admin();
		for (Caches c : Caches.values()) {
			caches.getCache(c.cacheName()).clear();
		}
	}

	@Test
	void everyCacheIsConfigured() {
		for (Caches c : Caches.values()) {
			assertThat(caches.getCache(c.cacheName())).as(c.cacheName()).isNotNull();
		}
	}

	@Test
	void publicPlansAreServedFromMemoryUntilAPlanChanges() throws Exception {
		UUID product = new OfferingFixtures(mvc, admin).plan();
		String code = jdbc.queryForObject("SELECT code FROM investment_products WHERE id = ?", String.class, product);
		assertThat(plans.openPlan(code).title()).isNotEqualTo("Changed behind its back");

		// A change the application did not make is not seen while the entry lives.
		jdbc.update("UPDATE investment_products SET title = 'Changed behind its back' WHERE id = ?", product);
		assertThat(plans.openPlan(code).title()).isNotEqualTo("Changed behind its back");

		// Any change to a plan through the application clears the cache.
		mvc.perform(post("/api/v1/admin/investment-products/{id}/close", product)
				.header(HttpHeaders.AUTHORIZATION, admin.bearer()))
			.andExpect(status().isOk());
		PlanView now = plans.openPlan(code);
		assertThat(now.title()).isEqualTo("Changed behind its back");
		assertThat(now.status().name()).isEqualTo("CLOSED");
	}

	@Test
	void anUnknownPlanCodeIsNotCached() {
		assertThatThrownBy(() -> plans.openPlan("NO-SUCH-PLAN"));
		assertThat(caches.getCache(Caches.Names.PUBLIC_PLANS).get("code:NO-SUCH-PLAN")).isNull();
	}

	@Test
	void signingUpRecordsTheTermsInForceEvenWhileTheShownTermsAreCached() throws Exception {
		int shown = terms.current().version();

		// A new version written directly is not shown yet...
		jdbc.update("INSERT INTO terms_versions (id, version, title, body, created_by, created_at) VALUES (?, ?, ?, ?, ?, ?)",
				UUID.randomUUID(), shown + 1, "Direct", "Written directly.", admin.id(), Timestamp.from(Instant.now()));
		assertThat(terms.current().version()).isEqualTo(shown);

		// ...but an account accepts the version actually in force.
		Account investor = api.register();
		assertThat(jdbc.queryForObject("SELECT terms_version FROM users WHERE id = ?", Integer.class, investor.id()))
			.isEqualTo(shown + 1);

		// Saving through the application clears what is shown.
		terms.save(admin.id(), new TermsRequest("Saved", "Saved through the application."));
		assertThat(terms.current().version()).isEqualTo(shown + 2);
	}

	@Test
	void aRolledBackChangeDoesNotClearTheCache() {
		int shown = terms.current().version();
		jdbc.update("INSERT INTO terms_versions (id, version, title, body, created_by, created_at) VALUES (?, ?, ?, ?, ?, ?)",
				UUID.randomUUID(), shown + 1, "Direct", "Written directly.", admin.id(), Timestamp.from(Instant.now()));

		tx.executeWithoutResult(status -> {
			terms.save(admin.id(), new TermsRequest("Rolled back", "Never committed."));
			status.setRollbackOnly();
		});

		// The cleared cache would have shown the directly written version; the rollback kept the cached one.
		assertThat(terms.current().version()).isEqualTo(shown);
	}

	@Test
	void theWebsiteSeesANewCreditIntervalAtOnce() {
		Integer saved = jdbc.queryForObject("SELECT credit_interval_minutes FROM rent_credit_settings WHERE id = 1",
				Integer.class);
		try {
			int chosen = rentCredit.forPublic().intervalMinutes() == 30 ? 60 : 30;
			rentCredit.update(admin.id(), chosen);
			assertThat(rentCredit.forPublic().intervalMinutes()).isEqualTo(chosen);

			// Crediting reads the setting fresh, never from the cache.
			jdbc.update("UPDATE rent_credit_settings SET credit_interval_minutes = 15 WHERE id = 1");
			assertThat(rentCredit.current().minutes()).isEqualTo(15);
			assertThat(rentCredit.forPublic().intervalMinutes()).isEqualTo(chosen);
		}
		finally {
			jdbc.update("UPDATE rent_credit_settings SET credit_interval_minutes = ? WHERE id = 1", saved);
			caches.getCache(Caches.Names.PUBLIC_RENT_CREDIT).clear();
		}
	}

}
