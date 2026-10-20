package com.sealease.backend.earning;

import com.sealease.backend.earning.service.PayoutService;
import com.sealease.backend.support.IntegrationTest;
import com.sealease.backend.support.InvestorFixtures;
import com.sealease.backend.support.OfferingFixtures;
import com.sealease.backend.support.TestApi;
import com.sealease.backend.support.TestApi.Account;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admins set how often rent is credited; crediting follows the interval in force, losing and repeating nothing. */
@IntegrationTest
class RentCreditIntervalIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PayoutService payouts;

	private TestApi api;
	private Account admin;

	@BeforeEach
	void setUp() throws Exception {
		api = new TestApi(mvc, jdbc);
		admin = api.admin();
	}

	@AfterEach
	void restoreDefault() {
		jdbc.update("UPDATE rent_credit_settings SET credit_interval_minutes = NULL, updated_at = NULL, updated_by = NULL");
	}

	@Test
	void adminsSetTheIntervalAndCreditingFollowsIt() throws Exception {
		// Hourly by default, readable by anyone for the website's wording.
		mvc.perform(get("/api/v1/public/rent-credit"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.intervalMinutes").value(60))
			.andExpect(jsonPath("$.label").value("every hour"));
		mvc.perform(get("/api/v1/admin/settings/rent-credit").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.defaultMinutes").value(60))
			.andExpect(jsonPath("$.updatedAt").doesNotExist())
			.andExpect(jsonPath("$.options[?(@.minutes == 1440)].label").value("every day"));

		// Only offered intervals, and only by admins.
		InvestorFixtures investors = new InvestorFixtures(mvc, jdbc, api);
		Account investor = investors.approvedInvestor();
		save(investor, 1440).andExpect(status().isForbidden());
		save(admin, 7).andExpect(status().isBadRequest());

		// A lease running 50 hours, credited daily: only up to midnight UTC; the rest waits for the next day.
		String orderId = investors.invest(investor, new OfferingFixtures(mvc, admin).plan(), 1);
		UUID holding = jdbc.queryForObject("SELECT id FROM holdings WHERE order_id = ?::uuid", UUID.class, orderId);
		Instant hour = Instant.now().truncatedTo(ChronoUnit.HOURS);
		Instant start = hour.minus(50, ChronoUnit.HOURS);
		jdbc.update("""
				UPDATE rent_accruals SET starts_at = ?, credited_until = ?,
				    ends_at = (? AT TIME ZONE 'UTC' + make_interval(months => tenure_months)) AT TIME ZONE 'UTC'
				WHERE holding_id = ?""", Timestamp.from(start), Timestamp.from(start), Timestamp.from(start), holding);

		save(admin, 1440).andExpect(status().isOk())
			.andExpect(jsonPath("$.intervalMinutes").value(1440))
			.andExpect(jsonPath("$.label").value("every day"))
			.andExpect(jsonPath("$.updatedAt").isNotEmpty());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'RENT_CREDIT_INTERVAL_CHANGED'",
				Integer.class)).isPositive();
		payouts.creditDue();
		Instant midnight = Instant.now().truncatedTo(ChronoUnit.DAYS);
		assertThat(creditedUntil(holding)).isEqualTo(midnight);
		mvc.perform(get("/api/v1/earnings/summary").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.nextCreditAt").value(midnight.plus(1, ChronoUnit.DAYS).toString()));

		// Back to hourly: the hours since midnight are credited at once, and the total is exactly 50 hours' rent.
		save(admin, 60).andExpect(status().isOk());
		payouts.creditDue();
		assertThat(creditedUntil(holding)).isEqualTo(Instant.now().truncatedTo(ChronoUnit.HOURS));
		assertThat(jdbc.queryForObject("SELECT credited_rent = (SELECT sum(rent_amount) FROM rent_credits WHERE holding_id = ?) "
				+ "FROM rent_accruals WHERE holding_id = ?", Boolean.class, holding, holding)).isTrue();
		assertThat(payouts.creditDue()).as("nothing credited twice").isZero();
	}

	private Instant creditedUntil(UUID holding) {
		return jdbc.queryForObject("SELECT credited_until FROM rent_accruals WHERE holding_id = ?", Timestamp.class, holding)
			.toInstant();
	}

	private ResultActions save(Account actor, int minutes) throws Exception {
		return mvc.perform(put("/api/v1/admin/settings/rent-credit").header(HttpHeaders.AUTHORIZATION, actor.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"intervalMinutes\":" + minutes + "}"));
	}

}
