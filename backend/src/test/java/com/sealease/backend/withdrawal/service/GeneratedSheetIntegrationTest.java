package com.sealease.backend.withdrawal.service;

import com.jayway.jsonpath.JsonPath;
import com.sealease.backend.support.IntegrationTest;
import com.sealease.backend.support.InvestorFixtures;
import com.sealease.backend.support.TestApi;
import com.sealease.backend.support.TestApi.Account;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** What a withdrawal run generates: a kept Excel sheet for bank withdrawals, nothing for cash (paid by hand). */
@IntegrationTest
class GeneratedSheetIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private BatchFileStore files;

	@Test
	void aCashBatchHasNoSheetAndIsReadyToMarkByHand() throws Exception {
		TestApi api = new TestApi(mvc, jdbc);
		Account finance = api.staff("FINANCE");
		Account investor = new InvestorFixtures(mvc, jdbc, api).approvedInvestor();
		// Referral paid in cash at the office: no bank account needed.
		mvc.perform(put("/api/v1/withdrawals/referral-mode").header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"OFFICE_CASH\"}"))
			.andExpect(status().isOk());
		mvc.perform(post("/api/v1/admin/ledger/adjustments").header(HttpHeaders.AUTHORIZATION, finance.bearer())
				.header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content("""
						{"userId":"%s","currency":"INR","direction":"CREDIT","amount":"400","reason":"Test funding","balance":"REFERRAL"}
						""".formatted(investor.id())))
			.andExpect(status().isCreated());

		String run = mvc.perform(post("/api/v1/admin/withdrawal-runs").param("kind", "REFERRAL").header(HttpHeaders.AUTHORIZATION,
				finance.bearer())).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		UUID cashBatch = UUID.fromString(JsonPath.read(run, "$.cashBatchId"));

		assertThat(files.payment(cashBatch)).isEmpty();
		assertThat(jdbc.queryForObject("SELECT status FROM withdrawal_batches WHERE id = ?", String.class, cashBatch))
			.isEqualTo("SENT");
		assertThat(jdbc.queryForObject("SELECT status FROM withdrawals WHERE user_id = ?::uuid AND batch_id = ?",
				String.class, investor.id(), cashBatch)).isEqualTo("PROCESSING");
	}

}
