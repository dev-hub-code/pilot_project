package com.sealease.backend.withdrawal;

import com.jayway.jsonpath.JsonPath;
import com.sealease.backend.support.BankAccountUploads;
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

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Withdrawals investors see, and cash withdrawn at the office in front of the investor. */
@IntegrationTest
class WithdrawalIntegrationTest {

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
		finance = api.staff("FINANCE");
	}

	@AfterEach
	void restoreDays() {
		jdbc.update("UPDATE withdrawal_settings SET rent_withdrawal_day = 10, referral_withdrawal_day = 15");
	}

	@Test
	void investorsNoLongerRequestWithdrawalsAndSeeTheSchedule() throws Exception {
		Account investor = investors.approvedInvestor();
		mvc.perform(post("/api/v1/withdrawals").header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"100\"}"))
			.andExpect(status().is4xxClientError());
		mvc.perform(get("/api/v1/withdrawals/policy").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.rentWithdrawalDay").value(10))
			.andExpect(jsonPath("$.referralWithdrawalDay").value(15))
			.andExpect(jsonPath("$.officeAddress").isNotEmpty());
	}

	@Test
	void cashIsWithdrawnAtTheOfficeFromTheWithdrawalDayOnceAMonth() throws Exception {
		int today = LocalDate.now(ZoneOffset.UTC).getDayOfMonth();
		assumeThat(today).as("cash can be withdrawn from the 2nd at the earliest").isGreaterThanOrEqualTo(2);
		Account investor = investors.approvedInvestor();
		credit(investor, "400", "RENT_CASH");
		credit(investor, "120", "REFERRAL");
		credit(investor, "900", "RENT_BANK");

		// Before the withdrawal day, nothing can be withdrawn at the office.
		if (today < 28) {
			jdbc.update("UPDATE withdrawal_settings SET rent_withdrawal_day = 28, referral_withdrawal_day = 28");
			options(investor).andExpect(jsonPath("$[?(@.balance == 'RENT_CASH')].refusal")
				.value(org.hamcrest.Matchers.hasItem(containsString("from the 28th"))));
			office(finance, investor, "RENT_CASH").andExpect(status().isUnprocessableContent());
		}
		jdbc.update("UPDATE withdrawal_settings SET rent_withdrawal_day = 2, referral_withdrawal_day = 2");

		// Bank rent is never paid at the office; the investor has no bank account, so referral defaults to cash.
		office(finance, investor, "RENT_BANK").andExpect(status().isUnprocessableContent());
		options(investor)
			.andExpect(jsonPath("$[?(@.balance == 'RENT_CASH')].available.amount").value(org.hamcrest.Matchers.hasItem("400.00")))
			.andExpect(jsonPath("$[?(@.balance == 'RENT_CASH')].refusal").value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.nullValue())));
		office(investor, investor, "RENT_CASH").andExpect(status().isForbidden());

		// The whole balance, ready to hand over against a receipt.
		String id = id(office(finance, investor, "RENT_CASH").andExpect(status().isCreated())
			.andExpect(jsonPath("$.origin").value("OFFICE"))
			.andExpect(jsonPath("$.status").value("APPROVED"))
			.andExpect(jsonPath("$.payoutMethod").value("OFFICE_CASH"))
			.andExpect(jsonPath("$.amount.amount").value("400.00"))
			.andExpect(jsonPath("$.createdBy").value(finance.id().toString())));
		wallet(investor, "RENT_CASH").andExpect(jsonPath("$.wallet[?(@.balance == 'RENT_CASH')].amount.amount")
			.value(org.hamcrest.Matchers.hasItem("0.00")));
		office(finance, investor, "RENT_CASH").andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.message").value(containsString("already in progress")));
		handOver(finance, id, "CR-1001").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PAID")).andExpect(jsonPath("$.payoutReference").value("CR-1001"));
		handOver(finance, id, "CR-1001").andExpect(status().isConflict());

		// Once a month: more rent this month waits for next month.
		credit(investor, "50", "RENT_CASH");
		office(finance, investor, "RENT_CASH").andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.message").value(containsString("already withdrawn this month")));

		// Referral in cash; if the investor leaves without it, finance cancels it and the money goes back.
		String referral = id(office(finance, investor, "REFERRAL").andExpect(status().isCreated())
			.andExpect(jsonPath("$.amount.amount").value("120.00")));
		mvc.perform(post("/api/v1/admin/withdrawals/{id}/reject", referral).header(HttpHeaders.AUTHORIZATION, finance.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Investor did not come\"}"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
		wallet(investor, "REFERRAL").andExpect(jsonPath("$.wallet[?(@.balance == 'REFERRAL')].amount.amount")
			.value(org.hamcrest.Matchers.hasItem("120.00")));
		// A rejected one does not count: it can be made again this month.
		office(finance, investor, "REFERRAL").andExpect(status().isCreated());

		mvc.perform(get("/api/v1/withdrawals").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.totalElements").value(3))
			.andExpect(jsonPath("$.content[0].createdBy").doesNotExist());
		assertLedgerBalanced();
	}

	@Test
	void referralCommissionOfInvestorsWhoChoseBankIsNotPaidAtTheOffice() throws Exception {
		Account investor = investors.approvedInvestor();
		String bankAccount = addBankAccount(investor, "Jo Example");
		mvc.perform(post("/api/v1/admin/bank-accounts/{id}/verify", bankAccount).header(HttpHeaders.AUTHORIZATION, finance.bearer()))
			.andExpect(status().isOk());
		credit(investor, "120", "REFERRAL");
		jdbc.update("UPDATE withdrawal_settings SET rent_withdrawal_day = 2, referral_withdrawal_day = 2");
		office(finance, investor, "REFERRAL").andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.message").value(containsString("paid into this investor's bank account")));
		mvc.perform(put("/api/v1/admin/users/{id}/referral-mode", investor.id()).header(HttpHeaders.AUTHORIZATION, finance.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"OFFICE_CASH\"}"))
			.andExpect(status().isOk());
		if (LocalDate.now(ZoneOffset.UTC).getDayOfMonth() >= 2) {
			office(finance, investor, "REFERRAL").andExpect(status().isCreated());
		}
	}

	@Test
	void rentOnAContainerPaidAtTheOfficeGoesToTheCashBalance() throws Exception {
		Account investor = investors.approvedInvestor();
		String orderId = payOrderAtOffice(investor);
		assertThat(jdbc.queryForList("SELECT paid_at_office FROM holdings WHERE order_id = ?::uuid", Boolean.class, orderId))
			.containsExactly(true);
	}

	// ----------------------------------------------------------------------------- helpers

	/** Buys a container and pays for it in cash at the office, confirmed by finance; returns the order. */
	private String payOrderAtOffice(Account investor) throws Exception {
		String orderId = investors.placeOrder(investor, new OfferingFixtures(mvc, api.admin()).plan(), 1);
		String paymentId = JsonPath.read(mvc.perform(post("/api/v1/orders/{id}/payments", orderId)
				.header(HttpHeaders.AUTHORIZATION, investor.bearer()).header("Idempotency-Key", key())
				.contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"OFFICE_CASH\"}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
		mvc.perform(post("/api/v1/admin/payments/{id}/confirm", paymentId).header(HttpHeaders.AUTHORIZATION, finance.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"externalReference\":\"CR-ORDER-1\",\"amountReceived\":\"50000\"}"))
			.andExpect(status().isOk());
		return orderId;
	}

	private ResultActions options(Account investor) throws Exception {
		return mvc.perform(get("/api/v1/admin/withdrawals/office").param("userId", investor.id().toString())
			.header(HttpHeaders.AUTHORIZATION, finance.bearer())).andExpect(status().isOk());
	}

	private ResultActions office(Account actor, Account investor, String balance) throws Exception {
		return mvc.perform(post("/api/v1/admin/withdrawals/office").header(HttpHeaders.AUTHORIZATION, actor.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"userId\":\"%s\",\"balance\":\"%s\"}".formatted(investor.id(), balance)));
	}

	private ResultActions handOver(Account actor, String id, String receipt) throws Exception {
		return mvc.perform(post("/api/v1/admin/withdrawals/{id}/hand-over", id).header(HttpHeaders.AUTHORIZATION, actor.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"receiptNumber\":\"" + receipt + "\"}"));
	}

	private ResultActions wallet(Account investor, String balance) throws Exception {
		return mvc.perform(get("/api/v1/earnings/summary").header(HttpHeaders.AUTHORIZATION, investor.bearer()));
	}

	private String addBankAccount(Account investor, String holder) throws Exception {
		String number = String.valueOf(ThreadLocalRandom.current().nextLong(100_000_000_000L, 999_999_999_999L));
		return JsonPath.read(mvc.perform(BankAccountUploads.addAccount("""
						{"accountHolderName":"%s","bankName":"Test Bank","country":"US","currency":"INR",
						 "accountNumber":"%s","routingCode":"HDFC0001234"}
						""".formatted(holder, number)).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
	}

	private void credit(Account investor, String amount, String balance) throws Exception {
		mvc.perform(post("/api/v1/admin/ledger/adjustments").header(HttpHeaders.AUTHORIZATION, finance.bearer())
				.header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON).content("""
						{"userId":"%s","currency":"INR","direction":"CREDIT","amount":"%s","reason":"Test funding","balance":"%s"}
						""".formatted(investor.id(), amount, balance)))
			.andExpect(status().isCreated());
	}

	private static String id(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
	}

	private static String key() {
		return UUID.randomUUID().toString();
	}

	private void assertLedgerBalanced() {
		List<Boolean> balanced = jdbc.queryForList("""
				SELECT sum(CASE direction WHEN 'DEBIT' THEN amount ELSE -amount END) = 0
				FROM ledger_entries e JOIN ledger_transactions t ON t.id = e.transaction_id GROUP BY t.currency
				""", Boolean.class);
		assertThat(balanced).containsOnly(true);
	}

}
