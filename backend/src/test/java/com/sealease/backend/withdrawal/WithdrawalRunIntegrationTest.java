package com.sealease.backend.withdrawal;

import com.jayway.jsonpath.JsonPath;
import com.sealease.backend.support.BankAccountUploads;
import com.sealease.backend.support.IntegrationTest;
import com.sealease.backend.support.InvestorFixtures;
import com.sealease.backend.support.OfferingFixtures;
import com.sealease.backend.support.TestApi;
import com.sealease.backend.support.TestApi.Account;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Withdrawal runs: an admin withdraws every eligible investor's rent and referral balances into a bank sheet and a
 * cash sheet, finance pays them and uploads the completed sheets.
 */
@IntegrationTest
class WithdrawalRunIntegrationTest {

	private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

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
		finance = api.staff("ADMIN", "FINANCE");
	}

	@Test
	void investorsDoNotRequestWithdrawalsAndSeeHowTheyArePaid() throws Exception {
		Account investor = investors.approvedInvestor();
		mvc.perform(post("/api/v1/withdrawals").header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"100\"}"))
			.andExpect(status().is4xxClientError());
		mvc.perform(get("/api/v1/withdrawals/policy").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.minimumAmount").isNumber())
			.andExpect(jsonPath("$.rentWithdrawalDay").doesNotExist())
			.andExpect(jsonPath("$.officeAddress").isNotEmpty());
		// The one-by-one cash withdrawal at the office is gone.
		mvc.perform(post("/api/v1/admin/withdrawals/office").header(HttpHeaders.AUTHORIZATION, finance.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"%s\",\"balance\":\"RENT_CASH\"}".formatted(investor.id())))
			.andExpect(status().is4xxClientError());
	}

	@Test
	void rentAndReferralRunsMakeFinanceTasksThatFinanceMarksPaidOrFailed() throws Exception {
		// Alice: rent paid by bank and referral by bank.
		Account alice = withBank("Alice Run");
		credit(alice, "1000.50", "RENT_BANK");
		credit(alice, "250", "REFERRAL");
		// Bob: rent on a container paid at the office and referral in cash (cash sheets); and bank rent.
		Account bob = withBank("Bob Run");
		chooseMode(bob, "OFFICE_CASH");
		credit(bob, "700", "RENT_CASH");
		credit(bob, "300", "REFERRAL");
		credit(bob, "900", "RENT_BANK");
		// Carol has no verified bank account: left out of the bank sheet, with the reason.
		Account carol = investors.approvedInvestor();
		credit(carol, "800", "RENT_BANK");
		// Dave's balances come to less than the minimum: they keep growing until a later run.
		Account dave = withBank("Dave Run");
		credit(dave, "20", "RENT_BANK");

		run(alice, "RENT").andExpect(status().isForbidden());
		mvc.perform(post("/api/v1/admin/withdrawal-runs").header(HttpHeaders.AUTHORIZATION, finance.bearer()))
			.andExpect(status().isBadRequest());

		// ------------------------------------------------------------------ the rent run
		String rentRun = run(finance, "RENT").andExpect(status().isCreated())
			.andExpect(jsonPath("$.reference").value(org.hamcrest.Matchers.matchesPattern("WR-\\d{5}")))
			.andExpect(jsonPath("$.kind").value("RENT"))
			.andExpect(jsonPath("$.bankBatchReference").exists())
			.andExpect(jsonPath("$.cashBatchReference").exists())
			.andReturn().getResponse().getContentAsString();
		String rentReference = JsonPath.read(rentRun, "$.reference");
		String rentBank = JsonPath.read(rentRun, "$.bankBatchId");
		String rentCash = JsonPath.read(rentRun, "$.cashBatchId");
		assertThat((List<String>) JsonPath.read(rentRun, "$.skipped[?(@.userId == '" + carol.id() + "')].reason"))
			.containsExactly("No verified primary bank account");
		assertThat((List<String>) JsonPath.read(rentRun, "$.skipped[?(@.userId == '" + carol.id() + "')].userCode"))
			.containsExactly(code(carol));
		assertThat((List<Object>) JsonPath.read(rentRun, "$.skipped[?(@.userId == '" + dave.id() + "')]")).isEmpty();
		assertThat(withdrawalCount(dave)).isZero();

		// Only rent moved to "in progress": referral stays in the wallet for its own run.
		summary(alice).andExpect(jsonPath("$.balances[0].amount").value("250.00"))
			.andExpect(jsonPath("$.withdrawalInProgress[0].amount").value("1000.50"))
			.andExpect(jsonPath("$.totalWithdrawn.length()").value(0));
		summary(bob).andExpect(jsonPath("$.withdrawalInProgress[0].amount").value("1600.00"));
		assertThat(jdbc.queryForList("SELECT DISTINCT balance FROM withdrawals WHERE batch_id IN (?::uuid, ?::uuid)",
				String.class, rentBank, rentCash)).containsOnly("RENT_BANK", "RENT_CASH");

		// The rent bank sheet: one row per investor, by user code, with their account and amount.
		List<List<String>> bankRows = read(download(rentBank, rentReference + "-Rent-Bank.xlsx"));
		assertThat(bankRows.getFirst()).containsExactly("Serial No", "User Code", "Name", "Account Number", "IFSC Code",
				"Amount", "Status");
		List<String> aliceRow = row(bankRows, alice);
		assertThat(aliceRow.get(2)).isEqualTo("Alice Run");
		assertThat(aliceRow.get(4)).isEqualTo("HDFC0001234");
		assertThat(Double.parseDouble(aliceRow.get(5))).isEqualTo(1000.50);
		assertThat(Double.parseDouble(row(bankRows, bob).get(5))).isEqualTo(900.0);
		assertThat(bankRows.stream().map(r -> r.get(1))).doesNotContain(code(carol), code(dave));

		// Cash at the office has no sheet: the batch is being paid by hand from the start.
		mvc.perform(get("/api/v1/admin/withdrawal-batches/{id}/file", rentCash).header(HttpHeaders.AUTHORIZATION, finance.bearer()))
			.andExpect(status().isUnprocessableContent());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM withdrawal_batch_files WHERE batch_id = ?::uuid", Integer.class,
				rentCash)).isZero();
		assertThat(jdbc.queryForObject("SELECT status FROM withdrawal_batches WHERE id = ?::uuid", String.class, rentCash))
			.isEqualTo("SENT");
		assertThat(jdbc.queryForList("SELECT status FROM withdrawals WHERE batch_id = ?::uuid", String.class, rentCash))
			.containsExactly("PROCESSING");

		// -------------------------------------------------------------- the referral run
		String referralRun = run(finance, "REFERRAL").andExpect(status().isCreated())
			.andExpect(jsonPath("$.kind").value("REFERRAL"))
			.andReturn().getResponse().getContentAsString();
		String referralReference = JsonPath.read(referralRun, "$.reference");
		String referralBank = JsonPath.read(referralRun, "$.bankBatchId");
		String referralCash = JsonPath.read(referralRun, "$.cashBatchId");
		assertThat(jdbc.queryForList("SELECT DISTINCT balance FROM withdrawals WHERE batch_id IN (?::uuid, ?::uuid)",
				String.class, referralBank, referralCash)).containsOnly("REFERRAL");
		assertThat(Double.parseDouble(row(read(download(referralBank, referralReference + "-Referral-Bank.xlsx")), alice).get(5)))
			.isEqualTo(250.0);
		assertThat(jdbc.queryForObject("SELECT amount FROM withdrawals WHERE batch_id = ?::uuid AND user_id = ?::uuid",
				java.math.BigDecimal.class, referralCash, bob.id())).isEqualByComparingTo("300");
		summary(alice).andExpect(jsonPath("$.balances[0].amount").value("0.00"))
			.andExpect(jsonPath("$.withdrawalInProgress[0].amount").value("1250.50"));

		// A second rent run while these are in progress leaves those balances alone.
		credit(alice, "600", "RENT_BANK");
		String again = run(finance, "RENT").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		assertThat((List<String>) JsonPath.read(again, "$.skipped[?(@.userId == '" + alice.id() + "')].reason"))
			.contains("A withdrawal is already in progress");

		// ------------------------------------------------------------------ finance tasks
		// Every withdrawal is a task for finance; open bank tasks carry the full account number to pay.
		tasks(investors.approvedInvestor(), "OPEN", rentBank).andExpect(status().isForbidden());
		tasks(finance, "OPEN", rentBank).andExpect(status().isOk())
			.andExpect(jsonPath("$.content[*].userCode").value(org.hamcrest.Matchers.hasItems(code(alice), code(bob))))
			.andExpect(jsonPath("$.content[?(@.userCode == '" + code(alice) + "')].kind").value(org.hamcrest.Matchers.contains("RENT")))
			.andExpect(jsonPath("$.content[?(@.userCode == '" + code(alice) + "')].ifsc")
				.value(org.hamcrest.Matchers.contains("HDFC0001234")))
			.andExpect(jsonPath("$.content[?(@.userCode == '" + code(alice) + "')].accountNumber")
				.value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.matchesPattern("\\d{12}"))))
			.andExpect(jsonPath("$.content[?(@.userCode == '" + code(alice) + "')].batchReference").isNotEmpty());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'FINANCE_TASKS_VIEWED'", Integer.class))
			.isPositive();
		tasks(finance, "OPEN", rentCash).andExpect(jsonPath("$.content[0].phone").isNotEmpty())
			.andExpect(jsonPath("$.content[0].accountNumber").doesNotExist());

		// Search: by user code (any case), the investor's name, the withdrawal's or the batch's reference.
		String aliceRent = jdbc.queryForObject("SELECT reference FROM withdrawals WHERE id = ?::uuid", String.class,
				task(alice, rentBank));
		search(code(alice).toLowerCase()).andExpect(jsonPath("$.content[*].userCode").value(org.hamcrest.Matchers.everyItem(
				org.hamcrest.Matchers.is(code(alice))))).andExpect(jsonPath("$.totalElements").value(2));
		search("alice run").andExpect(jsonPath("$.content[*].reference").value(org.hamcrest.Matchers.hasItem(aliceRent)));
		search(aliceRent.toLowerCase()).andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].reference").value(aliceRent));
		String rentBankReference = jdbc.queryForObject("SELECT reference FROM withdrawal_batches WHERE id = ?::uuid",
				String.class, rentBank);
		search(rentBankReference).andExpect(jsonPath("$.content[*].batchReference").value(org.hamcrest.Matchers.everyItem(
				org.hamcrest.Matchers.is(rentBankReference))));
		search("no such investor 123").andExpect(jsonPath("$.totalElements").value(0));

		// A failed task needs a reason.
		updateTask(task(bob, rentBank), "FAILED", null, " ").andExpect(status().isBadRequest());

		// Finance pays the rent bank tasks: Alice is paid; Bob's bank transfer failed.
		updateTask(task(alice, rentBank), "PAID", "UTR-1", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PAID"))
			.andExpect(jsonPath("$.payoutReference").value("UTR-1"))
			.andExpect(jsonPath("$.accountNumber").value(org.hamcrest.Matchers.startsWith("••••")));
		updateTask(task(bob, rentBank), "FAILED", null, "Account closed").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("FAILED"));
		// Done once: it cannot be updated again.
		updateTask(task(alice, rentBank), "PAID", "UTR-1", null).andExpect(status().is4xxClientError());
		summary(alice).andExpect(jsonPath("$.totalWithdrawn[0].amount").value("1000.50"))
			.andExpect(jsonPath("$.withdrawalInProgress[0].amount").value("250.00"));
		// Then the referral bank task: her referral too.
		updateTask(task(alice, referralBank), "PAID", "UTR-2", null).andExpect(status().isOk());
		summary(alice).andExpect(jsonPath("$.totalWithdrawn[0].amount").value("1250.50"))
			.andExpect(jsonPath("$.withdrawalInProgress.length()").value(0));
		tasks(finance, "DONE", rentBank)
			.andExpect(jsonPath("$.content[*].userCode").value(org.hamcrest.Matchers.hasItems(code(alice), code(bob))));
		// The failed transfer is held, with a ticket, until finance resolves it.
		String bobsBank = task(bob, rentBank);
		mvc.perform(get("/api/v1/admin/withdrawals/{id}", bobsBank).header(HttpHeaders.AUTHORIZATION, finance.bearer()))
			.andExpect(jsonPath("$.status").value("FAILED"))
			.andExpect(jsonPath("$.ticketId").exists());

		// Cash: Bob collects his rent and his referral at the office, each task marked collected with its receipt.
		updateTask(task(bob, rentCash), "PAID", "Receipt 501", null).andExpect(status().isOk());
		updateTask(task(bob, referralCash), "PAID", "Receipt 502", null).andExpect(status().isOk());
		assertThat(jdbc.queryForList("SELECT status || ' ' || coalesce(payout_reference, '') FROM withdrawals "
				+ "WHERE user_id = ?::uuid AND batch_id IN (?::uuid, ?::uuid) ORDER BY payout_reference", String.class, bob.id(),
				rentCash, referralCash)).containsExactly("PAID Receipt 501", "PAID Receipt 502");
		summary(bob).andExpect(jsonPath("$.totalWithdrawn[0].amount").value("1000.00"))
			.andExpect(jsonPath("$.withdrawalInProgress[0].amount").value("900.00"));

		// Bob's failed transfer goes back to his balance.
		mvc.perform(post("/api/v1/admin/withdrawals/{id}/resolve", bobsBank).header(HttpHeaders.AUTHORIZATION, finance.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"RETURNED\",\"note\":\"Please update your bank details.\"}"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RETURNED"));
		summary(bob).andExpect(jsonPath("$.withdrawalInProgress.length()").value(0));

		mvc.perform(get("/api/v1/admin/withdrawal-runs").header(HttpHeaders.AUTHORIZATION, finance.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.reference == '" + rentReference + "')].kind").value(org.hamcrest.Matchers.contains("RENT")))
			.andExpect(jsonPath("$[?(@.reference == '" + referralReference + "')].kind")
				.value(org.hamcrest.Matchers.contains("REFERRAL")));
		assertLedgerBalanced();
	}

	@Test
	void rentOnAContainerPaidAtTheOfficeGoesToTheCashBalance() throws Exception {
		Account investor = investors.approvedInvestor();
		String orderId = investors.placeOrder(investor, new OfferingFixtures(mvc, api.admin()).plan(), 1);
		String paymentId = JsonPath.read(mvc.perform(post("/api/v1/orders/{id}/payments", orderId)
				.header(HttpHeaders.AUTHORIZATION, investor.bearer()).header("Idempotency-Key", key())
				.contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"OFFICE_CASH\"}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
		api.takePayment(paymentId, finance);
		mvc.perform(post("/api/v1/admin/payments/{id}/confirm", paymentId).header(HttpHeaders.AUTHORIZATION, finance.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"externalReference\":\"CR-ORDER-1\",\"amountReceived\":\"50000\"}"))
			.andExpect(status().isOk());
		assertThat(jdbc.queryForList("SELECT paid_at_office FROM holdings WHERE order_id = ?::uuid", Boolean.class, orderId))
			.containsExactly(true);
	}

	@Test
	void theReferralPayoutModeIsChosenOnceAYear() throws Exception {
		Account investor = withBank("Ivy Example");
		mvc.perform(get("/api/v1/withdrawals/referral-mode").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.mode").value("BANK_TRANSFER"))
			.andExpect(jsonPath("$.chosen").value(false))
			.andExpect(jsonPath("$.year").value(LocalDate.now(ZoneOffset.UTC).getYear()));
		chooseMode(investor, "OFFICE_CASH").andExpect(status().isOk())
			.andExpect(jsonPath("$.mode").value("OFFICE_CASH")).andExpect(jsonPath("$.chosen").value(true));
		chooseMode(investor, "BANK_TRANSFER").andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value(containsString("already chosen")));

		// Staff change it on the investor's request.
		mvc.perform(put("/api/v1/admin/users/{id}/referral-mode", investor.id()).header(HttpHeaders.AUTHORIZATION, finance.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"BANK_TRANSFER\"}"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.mode").value("BANK_TRANSFER"));
		assertThat(api.auditCount("REFERRAL_PAYOUT_MODE_CHOSEN", investor.id())).isEqualTo(2);

		// Without a verified bank account, it defaults to cash and bank cannot be chosen.
		Account noBank = investors.approvedInvestor();
		mvc.perform(get("/api/v1/withdrawals/referral-mode").header(HttpHeaders.AUTHORIZATION, noBank.bearer()))
			.andExpect(jsonPath("$.mode").value("OFFICE_CASH"));
		chooseMode(noBank, "BANK_TRANSFER").andExpect(status().isUnprocessableContent());
	}

	// ----------------------------------------------------------------------------- helpers

	@Test
	void anAdminSetsTheMinimumWithdrawalForTheNextRuns() throws Exception {
		Account admin = api.admin();
		Account investor = withBank("Erin Minimum");
		credit(investor, "1500", "RENT_BANK");
		try {
			// Admins set it (they generate the runs); finance and support do not.
			minimum(api.staff("FINANCE"), "2000").andExpect(status().isForbidden());
			minimum(api.staff("SUPPORT"), "2000").andExpect(status().isForbidden());
			minimum(admin, "0").andExpect(status().isBadRequest());
			minimum(admin, "12.345").andExpect(status().isBadRequest());
			minimum(api.staff("ADMIN"), "2000").andExpect(status().isOk())
				.andExpect(jsonPath("$.minimumAmount").value(2000))
				.andExpect(jsonPath("$.defaultAmount").value(50))
				.andExpect(jsonPath("$.updatedAt").isNotEmpty());
			mvc.perform(get("/api/v1/admin/settings/withdrawals").header(HttpHeaders.AUTHORIZATION, finance.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.minimumAmount").value(2000))
				.andExpect(jsonPath("$.currency").value("INR"));
			mvc.perform(get("/api/v1/withdrawals/policy").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
				.andExpect(jsonPath("$.minimumAmount").value(2000));

			// ₹1,500 is now below the minimum: it stays in the wallet.
			run(finance, "RENT").andExpect(status().isCreated());
			assertThat(withdrawalCount(investor)).isZero();

			minimum(admin, "1000").andExpect(status().isOk());
			run(finance, "RENT").andExpect(status().isCreated());
			assertThat(withdrawalCount(investor)).isEqualTo(1);
			assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'WITHDRAWAL_MINIMUM_CHANGED'",
					Integer.class)).isGreaterThanOrEqualTo(2);
		}
		finally {
			jdbc.update("UPDATE withdrawal_settings SET minimum_amount = NULL, updated_at = NULL, updated_by = NULL");
		}
	}

	private ResultActions minimum(Account actor, String amount) throws Exception {
		return mvc.perform(put("/api/v1/admin/settings/withdrawals").header(HttpHeaders.AUTHORIZATION, actor.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"minimumAmount\":\"" + amount + "\"}"));
	}

	/** A rent ({@code RENT}) or referral ({@code REFERRAL}) withdrawal run. */
	private ResultActions run(Account actor, String kind) throws Exception {
		return mvc.perform(post("/api/v1/admin/withdrawal-runs").param("kind", kind)
			.header(HttpHeaders.AUTHORIZATION, actor.bearer()));
	}

	private byte[] download(String batchId, String filename) throws Exception {
		return mvc.perform(get("/api/v1/admin/withdrawal-batches/{id}/file", batchId)
				.header(HttpHeaders.AUTHORIZATION, finance.bearer()))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_TYPE, XLSX))
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString(filename)))
			.andReturn().getResponse().getContentAsByteArray();
	}

	private ResultActions summary(Account investor) throws Exception {
		return mvc.perform(get("/api/v1/earnings/summary").header(HttpHeaders.AUTHORIZATION, investor.bearer()));
	}

	private ResultActions chooseMode(Account investor, String mode) throws Exception {
		return mvc.perform(put("/api/v1/withdrawals/referral-mode").header(HttpHeaders.AUTHORIZATION, investor.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"" + mode + "\"}"));
	}

	private int withdrawalCount(Account investor) {
		Integer count = jdbc.queryForObject("SELECT count(*) FROM withdrawals WHERE user_id = ?::uuid", Integer.class, investor.id());
		return count == null ? 0 : count;
	}

	/** An approved investor with a verified primary bank account in their name. */
	private Account withBank(String name) throws Exception {
		Account investor = investors.approvedInvestor();
		jdbc.update("UPDATE users SET first_name = ?, last_name = '' WHERE id = ?", name, investor.id());
		String number = String.valueOf(ThreadLocalRandom.current().nextLong(100_000_000_000L, 999_999_999_999L));
		String bankAccountId = JsonPath.read(mvc.perform(BankAccountUploads.addAccount("""
						{"accountHolderName":"%s","bankName":"Test Bank","country":"US","currency":"INR",
						 "accountNumber":"%s","routingCode":"HDFC0001234"}
						""".formatted(name, number)).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
		api.assignReview("bank-accounts", bankAccountId, finance);
		mvc.perform(post("/api/v1/admin/bank-accounts/{id}/verify", bankAccountId).header(HttpHeaders.AUTHORIZATION, finance.bearer()))
			.andExpect(status().isOk());
		return investor;
	}

	private void credit(Account investor, String amount, String balance) throws Exception {
		mvc.perform(post("/api/v1/admin/ledger/adjustments").header(HttpHeaders.AUTHORIZATION, api.admin().bearer())
				.header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON).content("""
						{"userId":"%s","currency":"INR","direction":"CREDIT","amount":"%s","reason":"Test funding","balance":"%s"}
						""".formatted(investor.id(), amount, balance)))
			.andExpect(status().isCreated());
	}

	private ResultActions tasks(Account actor, String view, String batchId) throws Exception {
		return mvc.perform(get("/api/v1/admin/finance-tasks").param("view", view).param("batchId", batchId)
			.header(HttpHeaders.AUTHORIZATION, actor.bearer()));
	}

	private ResultActions search(String query) throws Exception {
		return mvc.perform(get("/api/v1/admin/finance-tasks").param("view", "OPEN").param("q", query)
			.header(HttpHeaders.AUTHORIZATION, finance.bearer()));
	}

	/** The investor's withdrawal (finance task) in the batch. */
	private String task(Account investor, String batchId) {
		return jdbc.queryForObject("SELECT id::text FROM withdrawals WHERE user_id = ?::uuid AND batch_id = ?::uuid",
				String.class, investor.id(), batchId);
	}

	private ResultActions updateTask(String withdrawalId, String outcome, String reference, String reason) throws Exception {
		String body = "{\"outcome\":\"" + outcome + "\"" + (reference == null ? "" : ",\"reference\":\"" + reference + "\"")
				+ (reason == null ? "" : ",\"reason\":\"" + reason + "\"") + "}";
		api.takeFinanceTask(withdrawalId, finance);
		return mvc.perform(post("/api/v1/admin/finance-tasks/{id}", withdrawalId).header(HttpHeaders.AUTHORIZATION, finance.bearer())
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	/** The investor's row of a sheet, found by their user code ("SL03500"). */
	private List<String> row(List<List<String>> rows, Account investor) {
		String code = code(investor);
		return rows.stream().filter(r -> r.get(1).equals(code)).findFirst().orElseThrow();
	}

	private String code(Account investor) {
		return "SL" + String.format("%05d", jdbc.queryForObject("SELECT user_number FROM users WHERE id = ?::uuid", Long.class,
				investor.id()));
	}

	private static List<List<String>> read(byte[] xlsx) throws Exception {
		List<List<String>> rows = new ArrayList<>();
		try (ReadableWorkbook workbook = new ReadableWorkbook(new ByteArrayInputStream(xlsx))) {
			for (Row row : workbook.getFirstSheet().read()) {
				List<String> cells = new ArrayList<>();
				for (int c = 0; c < row.getCellCount(); c++) {
					cells.add(row.getCell(c) == null ? "" : row.getCellText(c));
				}
				rows.add(cells);
			}
		}
		return rows;
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
