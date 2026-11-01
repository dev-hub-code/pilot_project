package com.sealease.backend.withdrawal;

import com.jayway.jsonpath.JsonPath;
import com.sealease.backend.support.IntegrationTest;
import com.sealease.backend.support.InvestorFixtures;
import com.sealease.backend.support.TestApi;
import com.sealease.backend.support.TestApi.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Online investors request withdrawals whenever they choose; network investors are paid by the runs. */
@IntegrationTest
class WithdrawalRequestIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	private TestApi api;
	private InvestorFixtures investors;
	private Account finance;

	@BeforeEach
	void setUp() throws Exception {
		api = new TestApi(mvc, jdbc);
		investors = new InvestorFixtures(mvc, jdbc, api);
		finance = api.staff("SUPER_ADMIN", "FINANCE");
	}

	@Test
	void anOnlineInvestorWithdrawsAWholeWalletAndFinanceSeesItAtOnce() throws Exception {
		Account investor = withBank();
		credit(investor, "1234.56", "RENT_BANK");

		String body = request(investor, "RENT_BANK", key()).andExpect(status().isCreated())
			.andExpect(jsonPath("$.amount.amount").value(1234.56))
			.andExpect(jsonPath("$.origin").value("REQUESTED"))
			.andExpect(jsonPath("$.status").value("PROCESSING"))
			.andReturn().getResponse().getContentAsString();

		mvc.perform(get("/api/v1/earnings/summary").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.withdrawalInProgress[0].amount").value(1234.56));
		String reference = JsonPath.read(body, "$.reference");
		mvc.perform(get("/api/v1/admin/finance-tasks").param("view", "OPEN").param("q", reference)
				.header(HttpHeaders.AUTHORIZATION, finance.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].reference").value(reference))
			.andExpect(jsonPath("$.content[0].payoutMethod").value("BANK_TRANSFER"));
	}

	@Test
	void aRetryChangesNothing() throws Exception {
		Account investor = withBank();
		credit(investor, "1000", "RENT_BANK");
		String key = key();

		String first = request(investor, "RENT_BANK", key).andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		String again = request(investor, "RENT_BANK", key).andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();

		assertThat(JsonPath.<String>read(again, "$.id")).isEqualTo(JsonPath.<String>read(first, "$.id"));
		assertThat(count(investor)).isEqualTo(1);
	}

	@Test
	void aSecondRequestWaitsForTheFirstToFinish() throws Exception {
		Account investor = withBank();
		credit(investor, "1000", "RENT_BANK");
		request(investor, "RENT_BANK", key()).andExpect(status().isCreated());
		credit(investor, "1000", "RENT_BANK");

		request(investor, "RENT_BANK", key()).andExpect(status().isConflict());
		assertThat(count(investor)).isEqualTo(1);
	}

	@Test
	void networkInvestorsCannotRequest() throws Exception {
		Account investor = withBank();
		jdbc.update("INSERT INTO network_investors (user_id, signups_join_network, created_at) VALUES (?, false, now())",
				investor.id());
		credit(investor, "1000", "RENT_BANK");

		refused(request(investor, "RENT_BANK", key()), "Network investors");
		assertThat(count(investor)).isZero();
	}

	@Test
	void theMinimumAndAPaymentRouteAreRequired() throws Exception {
		Account small = withBank();
		String policy = mvc.perform(get("/api/v1/withdrawals/policy").header(HttpHeaders.AUTHORIZATION, small.bearer()))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		BigDecimal minimum = new BigDecimal(JsonPath.read(policy, "$.minimumAmount").toString());
		credit(small, minimum.subtract(new BigDecimal("0.01")).toPlainString(), "RENT_BANK");
		refused(request(small, "RENT_BANK", key()), "at least");

		Account noBank = investors.approvedInvestor();
		credit(noBank, "1000", "RENT_BANK");
		refused(request(noBank, "RENT_BANK", key()), "No verified primary bank account");

		assertThat(count(small) + count(noBank)).isZero();
	}

	@Test
	void anUnverifiedInvestorCannotRequest() throws Exception {
		Account investor = withBank();
		credit(investor, "1000", "RENT_BANK");
		jdbc.update("UPDATE user_profiles SET kyc_status = 'PENDING' WHERE user_id = ?", investor.id());

		refused(request(investor, "RENT_BANK", key()), "Identity verification");
		assertThat(count(investor)).isZero();
	}

	@Test
	void onlineInvestorsNeedAVerifiedBankAccountToWithdraw() throws Exception {
		Account investor = investors.approvedInvestor();
		credit(investor, "600", "REFERRAL");

		refused(request(investor, "REFERRAL", key()), "bank account");
	}

	private static void refused(ResultActions result, String message) throws Exception {
		result.andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.message").value(containsString(message)));
	}

	private Account withBank() throws Exception {
		Account investor = investors.approvedInvestor();
		investors.ensureVerifiedBankAccount(investor);
		return investor;
	}

	private ResultActions request(Account investor, String balance, String key) throws Exception {
		return mvc.perform(post("/api/v1/withdrawals").header(HttpHeaders.AUTHORIZATION, investor.bearer())
			.header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
			.content("{\"balance\":\"%s\"}".formatted(balance)));
	}

	private void credit(Account investor, String amount, String balance) throws Exception {
		mvc.perform(post("/api/v1/admin/ledger/adjustments").header(HttpHeaders.AUTHORIZATION, api.admin().bearer())
				.header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON).content("""
						{"userId":"%s","currency":"INR","direction":"CREDIT","amount":"%s","reason":"Test funding","balance":"%s"}
						""".formatted(investor.id(), amount, balance)))
			.andExpect(status().isCreated());
	}

	private int count(Account investor) {
		return jdbc.queryForObject("SELECT count(*) FROM withdrawals WHERE user_id = ?", Integer.class, investor.id());
	}

	private static String key() {
		return UUID.randomUUID().toString();
	}

}
