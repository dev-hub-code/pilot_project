package com.sealease.backend.withdrawal;

import com.jayway.jsonpath.JsonPath;
import com.sealease.backend.support.BankAccountUploads;
import com.sealease.backend.support.IntegrationTest;
import com.sealease.backend.support.InvestorFixtures;
import com.sealease.backend.support.TestApi;
import com.sealease.backend.support.TestApi.Account;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The monthly rent and referral runs, their Excel files, and settling them by uploading the files back. */
@IntegrationTest
class ScheduledWithdrawalIntegrationTest {

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
		finance = api.staff("FINANCE");
	}

	@Test
	void theWithdrawalDaysAreSetByAnAdmin() throws Exception {
		putDays(finance, 10, 15).andExpect(status().isForbidden());
		putDays(api.admin(), 1, 15).andExpect(status().isBadRequest());
		putDays(api.admin(), 10, 29).andExpect(status().isBadRequest());
		putDays(api.admin(), 12, 20).andExpect(status().isOk())
			.andExpect(jsonPath("$.rentWithdrawalDay").value(12))
			.andExpect(jsonPath("$.referralWithdrawalDay").value(20));
		putDays(api.admin(), 10, 15).andExpect(status().isOk());
		mvc.perform(get("/api/v1/admin/settings/withdrawals").header(HttpHeaders.AUTHORIZATION, finance.bearer()))
			.andExpect(jsonPath("$.rentWithdrawalDay").value(10))
			.andExpect(jsonPath("$.referralWithdrawalDay").value(15))
			.andExpect(jsonPath("$.nextRentFileOn").exists())
			.andExpect(jsonPath("$.nextReferralFileOn").exists());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'WITHDRAWAL_DAY_CHANGED'", Integer.class))
			.isPositive();
	}

	@Test
	void theRentRunMakesAnExcelFileAndTheUploadedFileSettlesIt() throws Exception {
		// One run per month and kind: forget earlier runs so this one can happen.
		jdbc.update("DELETE FROM scheduled_withdrawal_runs");
		Account alice = funded("1000.50", "Alice Example", "RENT_BANK");
		Account bob = funded("300", "Bob Example", "RENT_BANK");
		Account erin = funded("200", "Erin Example", "RENT_BANK");
		Account carol = investors.approvedInvestor();
		addBankAccount(carol, "Carol Example");
		credit(carol, "500", "RENT_BANK");
		// Referral commission and cash rent are not in the rent file.
		Account dave = funded("800", "Dave Example", "REFERRAL");
		credit(dave, "50", "RENT_CASH");

		run(alice, "RENT").andExpect(status().isForbidden());
		String run = run(finance, "RENT").andExpect(status().isCreated())
			.andExpect(jsonPath("$.kind").value("RENT"))
			.andExpect(jsonPath("$.batchReference").exists())
			.andReturn().getResponse().getContentAsString();
		run(finance, "RENT").andExpect(status().isConflict());
		assertThat((List<String>) JsonPath.read(run, "$.skipped[?(@.userId == '" + carol.id() + "')].reason"))
			.containsExactly("No verified primary bank account");
		assertThat(withdrawals(dave)).isZero();
		String batch = JsonPath.read(run, "$.batchId");

		// Whole balances are withdrawn and approved by the schedule.
		String alices = withdrawal(alice);
		detail(alices).andExpect(jsonPath("$.origin").value("SCHEDULED"))
			.andExpect(jsonPath("$.status").value("BATCHED"))
			.andExpect(jsonPath("$.balance").value("RENT_BANK"))
			.andExpect(jsonPath("$.amount.amount").value("1000.50"))
			.andExpect(jsonPath("$.batchId").value(batch));
		balance(alice).andExpect(jsonPath("$.balances[0].amount").value("0.00"));
		mvc.perform(get("/api/v1/admin/withdrawal-batches/{id}", batch).header(HttpHeaders.AUTHORIZATION, finance.bearer()))
			.andExpect(jsonPath("$.batch.kind").value("RENT"));

		// The Excel file, named for the kind and month, was kept with the batch when it was made.
		LocalDate today = LocalDate.now(ZoneOffset.UTC);
		String filename = "Rent-" + today.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + "-" + today.getYear() + ".xlsx";
		mvc.perform(get("/api/v1/admin/withdrawal-batches/{id}/files", batch).header(HttpHeaders.AUTHORIZATION, finance.bearer()))
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].kind").value("PAYMENT"))
			.andExpect(jsonPath("$[0].filename").value(filename));
		byte[] file = mvc.perform(get("/api/v1/admin/withdrawal-batches/{id}/file", batch)
				.header(HttpHeaders.AUTHORIZATION, finance.bearer()))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_TYPE, XLSX))
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString(filename)))
			.andReturn().getResponse().getContentAsByteArray();
		List<List<String>> rows = read(file);
		assertThat(rows.getFirst()).containsExactly("Serial No", "User ID", "Name", "Account Number", "IFSC Code", "Amount",
				"Status", "Remarks");
		// Other tests' investors may be in the file too (the database is shared); ours are there, numbered in order.
		assertThat(rows.stream().skip(1).map(r -> r.get(1)))
			.contains(alice.id().toString(), bob.id().toString(), erin.id().toString())
			.doesNotContain(carol.id().toString(), dave.id().toString());
		List<String> aliceRow = rows.stream().filter(r -> r.get(1).equals(alice.id().toString())).findFirst().orElseThrow();
		assertThat(aliceRow.get(2)).isEqualTo("Alice Example");
		assertThat(aliceRow.get(3)).matches("\\d{12}");
		assertThat(aliceRow.get(4)).isEqualTo("HDFC0001234");
		assertThat(Double.parseDouble(aliceRow.get(5))).isEqualTo(1000.50);
		assertThat(rows.stream().skip(1).map(r -> r.get(0)))
			.containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, rows.size() - 1).mapToObj(String::valueOf).toList());

		// Unknown users or statuses refuse the whole file.
		upload(batch, statusFile(List.of(List.of(UUID.randomUUID().toString(), "Nobody", "PAID", ""))))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(containsString("Nothing was changed")));
		upload(batch, statusFile(List.of(List.of(alice.id().toString(), "Alice Example", "MAYBE", ""))))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(containsString("not PAID, FAILED or blank")));
		detail(alices).andExpect(jsonPath("$.status").value("BATCHED"));

		byte[] settled = statusFile(List.of(
				List.of(alice.id().toString(), "Alice Example", "PAID", "UTR-1"),
				List.of(bob.id().toString(), "Bob Example", "FAILED", "Account closed"),
				List.of(erin.id().toString(), "Erin Example", "Failed", "")));
		upload(batch, settled).andExpect(status().isOk())
			.andExpect(jsonPath("$.paid").value(1))
			.andExpect(jsonPath("$.failed").value(2));
		detail(alices).andExpect(jsonPath("$.status").value("PAID")).andExpect(jsonPath("$.payoutReference").value("UTR-1"));

		// A failed payment is held, not returned, with a ticket for the investor assigned to the finance user.
		String bobs = withdrawal(bob);
		String ticket = JsonPath.read(detail(bobs).andExpect(jsonPath("$.status").value("FAILED"))
			.andExpect(jsonPath("$.failureReason").value("Account closed"))
			.andExpect(jsonPath("$.ticketId").exists())
			.andReturn().getResponse().getContentAsString(), "$.ticketId");
		balance(bob).andExpect(jsonPath("$.balances[0].amount").value("0.00"));
		assertThat(jdbc.queryForMap("SELECT user_id, assignee_id, category, status FROM support_tickets WHERE id = ?::uuid", ticket))
			.containsEntry("user_id", bob.id()).containsEntry("assignee_id", finance.id())
			.containsEntry("category", "WITHDRAWAL").containsEntry("status", "OPEN");
		mvc.perform(get("/api/v1/support/tickets").header(HttpHeaders.AUTHORIZATION, bob.bearer()))
			.andExpect(jsonPath("$.content[0].subject").value(containsString("could not be paid")));
		detail(withdrawal(erin)).andExpect(jsonPath("$.failureReason").value("The bank did not pay it"));

		// Uploading the same file again changes nothing; every upload is kept.
		upload(batch, settled).andExpect(status().isOk()).andExpect(jsonPath("$.paid").value(0)).andExpect(jsonPath("$.failed").value(0));
		mvc.perform(get("/api/v1/admin/withdrawal-batches/{id}/files", batch).header(HttpHeaders.AUTHORIZATION, finance.bearer()))
			.andExpect(jsonPath("$.length()").value(3))
			.andExpect(jsonPath("$[1].kind").value("RECONCILIATION"))
			.andExpect(jsonPath("$[1].paidCount").value(1));

		// Finance resolves the failures: Bob's money goes back to his balance, Erin's is paid by hand.
		resolve(alices, "RETURNED", null).andExpect(status().isConflict());
		resolve(bobs, "RETURNED", "Please update your bank details.").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("RETURNED"));
		balance(bob).andExpect(jsonPath("$.balances[0].amount").value("300.00"));
		assertThat(jdbc.queryForObject("SELECT status FROM support_tickets WHERE id = ?::uuid", String.class, ticket))
			.isEqualTo("RESOLVED");
		resolve(withdrawal(erin), "PAID", "UTR-9").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PAID")).andExpect(jsonPath("$.payoutReference").value("UTR-9"));
		balance(erin).andExpect(jsonPath("$.balances[0].amount").value("0.00"));
		assertLedgerBalanced();
	}

	@Test
	void theReferralRunPaysThoseWhoChoseBank() throws Exception {
		jdbc.update("DELETE FROM scheduled_withdrawal_runs");
		Account grace = funded("400", "Grace Example", "REFERRAL");
		Account henry = funded("400", "Henry Example", "REFERRAL");
		mvc.perform(put("/api/v1/withdrawals/referral-mode").header(HttpHeaders.AUTHORIZATION, henry.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"OFFICE_CASH\"}"))
			.andExpect(status().isOk());

		String run = run(finance, "REFERRAL").andExpect(status().isCreated())
			.andExpect(jsonPath("$.kind").value("REFERRAL"))
			.andReturn().getResponse().getContentAsString();
		run(finance, "REFERRAL").andExpect(status().isConflict());
		detail(withdrawal(grace)).andExpect(jsonPath("$.balance").value("REFERRAL"))
			.andExpect(jsonPath("$.amount.amount").value("400.00"));
		// Henry collects his commission in cash at the office instead.
		assertThat(withdrawals(henry)).isZero();
		LocalDate today = LocalDate.now(ZoneOffset.UTC);
		mvc.perform(get("/api/v1/admin/withdrawal-batches/{id}/files", (String) JsonPath.read(run, "$.batchId"))
				.header(HttpHeaders.AUTHORIZATION, finance.bearer()))
			.andExpect(jsonPath("$[0].filename").value("Referral-"
					+ today.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + "-" + today.getYear() + ".xlsx"));
	}

	@Test
	void theReferralPayoutModeIsChosenOnceAYear() throws Exception {
		Account investor = funded("0.01", "Ivy Example", "REFERRAL");
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

	/** A status file as finance uploads it: User ID, Name, Status and Remarks per row. */
	private static byte[] statusFile(List<List<String>> rows) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (Workbook workbook = new Workbook(out, "Test", "1.0")) {
			Worksheet sheet = workbook.newWorksheet("Withdrawals");
			List<String> header = List.of("User ID", "Name", "Status", "Remarks");
			for (int c = 0; c < header.size(); c++) {
				sheet.value(0, c, header.get(c));
			}
			for (int r = 0; r < rows.size(); r++) {
				for (int c = 0; c < rows.get(r).size(); c++) {
					sheet.value(r + 1, c, rows.get(r).get(c));
				}
			}
		}
		return out.toByteArray();
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

	private ResultActions upload(String batchId, byte[] xlsx) throws Exception {
		return mvc.perform(multipart("/api/v1/admin/withdrawal-batches/{id}/reconciliation", batchId)
			.file(new MockMultipartFile("file", "paid.xlsx", XLSX, xlsx))
			.header(HttpHeaders.AUTHORIZATION, finance.bearer()));
	}

	private ResultActions run(Account actor, String kind) throws Exception {
		return mvc.perform(post("/api/v1/admin/withdrawal-runs").param("kind", kind).header(HttpHeaders.AUTHORIZATION, actor.bearer()));
	}

	private ResultActions resolve(String withdrawalId, String outcome, String note) throws Exception {
		return mvc.perform(post("/api/v1/admin/withdrawals/{id}/resolve", withdrawalId).header(HttpHeaders.AUTHORIZATION, finance.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content(note == null ? "{\"outcome\":\"" + outcome + "\"}" : "{\"outcome\":\"%s\",\"note\":\"%s\"}".formatted(outcome, note)));
	}

	private ResultActions chooseMode(Account investor, String mode) throws Exception {
		return mvc.perform(put("/api/v1/withdrawals/referral-mode").header(HttpHeaders.AUTHORIZATION, investor.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"" + mode + "\"}"));
	}

	private String withdrawal(Account investor) {
		return jdbc.queryForObject("SELECT id::text FROM withdrawals WHERE user_id = ?::uuid AND origin = 'SCHEDULED'",
				String.class, investor.id());
	}

	private int withdrawals(Account investor) {
		Integer count = jdbc.queryForObject("SELECT count(*) FROM withdrawals WHERE user_id = ?::uuid", Integer.class, investor.id());
		return count == null ? 0 : count;
	}

	private ResultActions detail(String withdrawalId) throws Exception {
		return mvc.perform(get("/api/v1/admin/withdrawals/{id}", withdrawalId).header(HttpHeaders.AUTHORIZATION, finance.bearer()));
	}

	private ResultActions balance(Account investor) throws Exception {
		return mvc.perform(get("/api/v1/earnings/summary").header(HttpHeaders.AUTHORIZATION, investor.bearer()));
	}

	private ResultActions putDays(Account actor, int rent, int referral) throws Exception {
		return mvc.perform(put("/api/v1/admin/settings/withdrawals").header(HttpHeaders.AUTHORIZATION, actor.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"rentWithdrawalDay\":%d,\"referralWithdrawalDay\":%d}".formatted(rent, referral)));
	}

	/** An approved investor with a verified primary bank account and a credited balance. */
	private Account funded(String amount, String holder, String balance) throws Exception {
		Account investor = investors.approvedInvestor();
		named(investor, holder);
		String bankAccountId = addBankAccount(investor, holder);
		mvc.perform(post("/api/v1/admin/bank-accounts/{id}/verify", bankAccountId).header(HttpHeaders.AUTHORIZATION, finance.bearer()))
			.andExpect(status().isOk());
		credit(investor, amount, balance);
		return investor;
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
				.header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content("""
						{"userId":"%s","currency":"INR","direction":"CREDIT","amount":"%s","reason":"Test funding","balance":"%s"}
						""".formatted(investor.id(), amount, balance)))
			.andExpect(status().isCreated());
	}

	private void assertLedgerBalanced() {
		List<Boolean> balanced = jdbc.queryForList("""
				SELECT sum(CASE direction WHEN 'DEBIT' THEN amount ELSE -amount END) = 0
				FROM ledger_entries e JOIN ledger_transactions t ON t.id = e.transaction_id GROUP BY t.currency
				""", Boolean.class);
		assertThat(balanced).containsOnly(true);
	}

	/** Bank accounts are always in the investor's registered name, so the payout file shows {@code name}. */
	private void named(Account investor, String name) {
		jdbc.update("UPDATE users SET first_name = ?, last_name = '' WHERE id = ?", name, investor.id());
	}

}
