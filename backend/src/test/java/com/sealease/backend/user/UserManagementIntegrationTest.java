package com.sealease.backend.user;

import com.jayway.jsonpath.JsonPath;
import com.sealease.backend.support.IntegrationTest;
import com.sealease.backend.support.TestApi;
import com.sealease.backend.support.TestApi.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase 3: profiles, KYC, bank accounts and suspension against PostgreSQL. */
@IntegrationTest
class UserManagementIntegrationTest {

	private static final byte[] PNG_HEADER = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };
	private static final String SECRET_MARKER = "PASSPORT-SCAN-SECRET-MARKER";
	private static final String VALID_IBAN = "GB82 WEST 1234 5698 7654 32";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	private TestApi api;

	@BeforeEach
	void setUp() {
		api = new TestApi(mvc, jdbc);
	}

	// --------------------------------------------------------------------------- profile

	@Test
	void profileIsCreatedAtRegistrationAndEditable() throws Exception {
		Account investor = api.register();

		mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.kycStatus").value("NOT_SUBMITTED"))
			.andExpect(jsonPath("$.twoFactorEnabled").value(false));

		mvc.perform(put("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(profileBody("Ada", "+447700900123", "1990-04-01")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.firstName").value("Ada"))
			.andExpect(jsonPath("$.address.city").value("London"))
			.andExpect(jsonPath("$.emailNotifications").value(true))
			.andExpect(jsonPath("$.smsNotifications").value(false));

		// Notification preferences are settings of their own; saving the profile leaves them alone.
		mvc.perform(put("/api/v1/users/me/notification-preferences").header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"emailNotifications\":false,\"smsNotifications\":true}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.emailNotifications").value(false))
			.andExpect(jsonPath("$.smsNotifications").value(true));
		mvc.perform(put("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(profileBody("Ada", "+447700900123", "1990-04-01")))
			.andExpect(jsonPath("$.emailNotifications").value(false))
			.andExpect(jsonPath("$.smsNotifications").value(true));
		mvc.perform(put("/api/v1/users/me/notification-preferences").header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"emailNotifications\":true}"))
			.andExpect(status().isBadRequest());

		String changes = jdbc.queryForObject("""
				SELECT string_agg(new_value::text, ' ') FROM audit_logs WHERE action = 'PROFILE_UPDATED' AND entity_id = ?
				""", String.class, investor.id().toString());
		assertThat(changes).contains("address", "phone", "notificationPreferences").doesNotContain("+447700900123", "London");
	}

	@Test
	void profileValidationRejectsBadInput() throws Exception {
		Account investor = api.register();
		mvc.perform(put("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(profileBody("Ada", "07700 900123", "1990-04-01")
					.replace("\"GB\"", "\"XX\"")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[*].field", hasItem("phone")))
			.andExpect(jsonPath("$.fieldErrors[*].field", hasItem("address.country")));

		String minor = java.time.LocalDate.now().minusYears(17).toString();
		mvc.perform(put("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(profileBody("Ada", null, minor)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("You must be at least 18 years old"));
	}

	@Test
	void taxIdIsEncryptedAtRestAndReturnedMasked() throws Exception {
		Account investor = api.register();
		mvc.perform(put("/api/v1/users/me/tax").header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"taxResidencyCountry\":\"US\",\"taxId\":\"123-45-6789\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.taxIdMasked").value("•••• 6789"));

		String stored = jdbc.queryForObject("SELECT tax_id_encrypted FROM user_profiles WHERE user_id = ?",
				String.class, investor.id());
		assertThat(stored).startsWith("t2:").doesNotContain("6789");
	}

	// ------------------------------------------------------------------------------- KYC

	@Test
	void kycRequiresEvidenceAndRejectsDisguisedFiles() throws Exception {
		Account investor = api.register();

		submitKyc(investor, Map.of("identityFront", png()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("A selfie holding the document is required"));

		var html = new MockMultipartFile("selfie", "selfie.png", "image/png",
				"<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8));
		submitKyc(investor, Map.of("identityFront", png(), "selfie", html))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Document KYC_SELFIE must be a PDF, JPEG or PNG file"));

		mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.kycStatus").value("NOT_SUBMITTED"));
	}

	@Test
	void kycSubmissionReviewAndApproval() throws Exception {
		Account investor = api.register();
		String submissionId = JsonPath.read(submitValidKyc(investor)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("PENDING"))
			.andExpect(jsonPath("$.documentNumberMasked").value("•••• 4321"))
			.andExpect(jsonPath("$.reviewedBy").doesNotExist())
			.andExpect(jsonPath("$.documents.length()").value(2))
			.andReturn().getResponse().getContentAsString(), "$.id");

		// Only one submission under review at a time.
		submitValidKyc(investor).andExpect(status().isConflict());
		// Legal name is locked while under review.
		mvc.perform(put("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(profileBody("Someone", null, null)))
			.andExpect(status().isUnprocessableContent());

		// Evidence is encrypted at rest.
		byte[] stored = jdbc.queryForObject("""
				SELECT d.content FROM stored_documents d JOIN kyc_documents k ON k.document_id = d.id
				WHERE k.submission_id = ?::uuid AND k.purpose = 'KYC_IDENTITY_FRONT'
				""", byte[].class, submissionId);
		assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain(SECRET_MARKER);
		assertThat(Arrays.equals(stored, 0, PNG_HEADER.length, PNG_HEADER, 0, PNG_HEADER.length)).isFalse();

		Account reviewer = api.staff("ADMINISTRATION");
		mvc.perform(get("/api/v1/admin/kyc").header(HttpHeaders.AUTHORIZATION, reviewer.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[*].id", hasItem(submissionId)));
		String detail = mvc.perform(get("/api/v1/admin/kyc/{id}", submissionId)
				.header(HttpHeaders.AUTHORIZATION, reviewer.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.account.email").value(investor.email()))
			.andReturn().getResponse().getContentAsString();
		String frontId = ((List<String>) JsonPath.read(detail,
				"$.submission.documents[?(@.purpose == 'KYC_IDENTITY_FRONT')].id")).getFirst();

		byte[] downloaded = mvc.perform(get("/api/v1/admin/kyc/{id}/documents/{doc}", submissionId, frontId)
				.header(HttpHeaders.AUTHORIZATION, reviewer.bearer()))
			.andExpect(status().isOk())
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
				.string(HttpHeaders.CONTENT_TYPE, "image/png"))
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
				.string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")))
			.andReturn().getResponse().getContentAsByteArray();
		assertThat(downloaded).isEqualTo(png().getBytes());
		assertThat(api.auditCount("KYC_DOCUMENT_VIEWED", submissionId)).isEqualTo(1);

		// A document id from another submission cannot be fetched through this one.
		mvc.perform(get("/api/v1/admin/kyc/{id}/documents/{doc}", submissionId, UUID.randomUUID())
				.header(HttpHeaders.AUTHORIZATION, reviewer.bearer()))
			.andExpect(status().isNotFound());

		mvc.perform(post("/api/v1/admin/kyc/{id}/approve", submissionId).header(HttpHeaders.AUTHORIZATION, reviewer.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("APPROVED"));
		mvc.perform(post("/api/v1/admin/kyc/{id}/approve", submissionId).header(HttpHeaders.AUTHORIZATION, reviewer.bearer()))
			.andExpect(status().isConflict());
		mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.kycStatus").value("APPROVED"));
		submitValidKyc(investor).andExpect(status().isUnprocessableContent());
	}

	@Test
	void rejectedKycCanBeResubmitted() throws Exception {
		Account investor = api.register();
		String submissionId = JsonPath.read(submitValidKyc(investor).andReturn().getResponse().getContentAsString(), "$.id");
		Account reviewer = api.staff("ADMINISTRATION");

		mvc.perform(post("/api/v1/admin/kyc/{id}/reject", submissionId).header(HttpHeaders.AUTHORIZATION, reviewer.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Image is blurred\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.rejectionReason").value("Image is blurred"));
		mvc.perform(get("/api/v1/users/me/kyc").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.status").value("REJECTED"))
			.andExpect(jsonPath("$.rejectionReason").value("Image is blurred"));

		submitValidKyc(investor).andExpect(status().isCreated());
		Integer submissions = jdbc.queryForObject("SELECT count(*) FROM kyc_submissions WHERE user_id = ?",
				Integer.class, investor.id());
		assertThat(submissions).isEqualTo(2);
	}

	@Test
	void reviewersCannotApproveTheirOwnKyc() throws Exception {
		// An investor who submitted their own KYC and was later hired as a reviewer.
		Account investor = api.register();
		String submissionId = JsonPath.read(submitValidKyc(investor).andReturn().getResponse().getContentAsString(), "$.id");
		Account reviewer = api.becomeStaff(investor, "ADMINISTRATION");
		mvc.perform(post("/api/v1/admin/kyc/{id}/approve", submissionId).header(HttpHeaders.AUTHORIZATION, reviewer.bearer()))
			.andExpect(status().isForbidden());
	}

	@Test
	void supportCannotSeeKycOrBankDetails() throws Exception {
		Account investor = api.register();
		Account support = api.staff("SUPPORT");
		mvc.perform(get("/api/v1/admin/users/{id}", investor.id()).header(HttpHeaders.AUTHORIZATION, support.bearer()))
			.andExpect(status().isOk());
		mvc.perform(get("/api/v1/admin/users/{id}/kyc", investor.id()).header(HttpHeaders.AUTHORIZATION, support.bearer()))
			.andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/admin/users/{id}/bank-accounts", investor.id())
				.header(HttpHeaders.AUTHORIZATION, support.bearer()))
			.andExpect(status().isForbidden());
	}

	// --------------------------------------------------------------------- bank accounts

	@Test
	void bankAccountLifecycle() throws Exception {
		Account investor = api.register();
		String first = JsonPath.read(addBank(investor, VALID_IBAN, "NWBKGB2L")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.accountNumberMasked").value("•••• 5432"))
			.andExpect(jsonPath("$.primary").value(true))
			.andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"))
			.andReturn().getResponse().getContentAsString(), "$.id");

		addBank(investor, "GB82 WEST 1234 5698 7654 33", "NWBKGB2L")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("The IBAN checksum is invalid; please check the number"));
		addBank(investor, "gb82west12345698765432", "nwbk-gb2l").andExpect(status().isConflict());

		String second = JsonPath.read(addBank(investor, "12345678", "40-47-84")
			.andExpect(jsonPath("$.primary").value(false))
			.andReturn().getResponse().getContentAsString(), "$.id");
		mvc.perform(put("/api/v1/users/me/bank-accounts/{id}/primary", second).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.id == '" + second + "')].primary").value(hasItem(true)))
			.andExpect(jsonPath("$[?(@.id == '" + first + "')].primary").value(hasItem(false)));

		mvc.perform(delete("/api/v1/users/me/bank-accounts/{id}", first).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isNoContent());
		mvc.perform(get("/api/v1/users/me/bank-accounts").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.length()").value(1));

		// Another user cannot touch these accounts; ids are not even confirmed to exist.
		Account other = api.register();
		mvc.perform(delete("/api/v1/users/me/bank-accounts/{id}", second).header(HttpHeaders.AUTHORIZATION, other.bearer()))
			.andExpect(status().isNotFound());

		String stored = jdbc.queryForObject("SELECT account_number_encrypted FROM bank_accounts WHERE id = ?::uuid",
				String.class, first);
		assertThat(stored).doesNotContain("98765432");
	}

	@Test
	void bankVerificationRequiresApprovedKycAndFlagsSharedAccounts() throws Exception {
		Account investor = api.register();
		String accountId = JsonPath.read(addBank(investor, "DE89 3704 0044 0532 0130 00", "COBADEFFXXX")
			.andReturn().getResponse().getContentAsString(), "$.id");
		Account finance = api.staff("FINANCE");

		mvc.perform(post("/api/v1/admin/bank-accounts/{id}/verify", accountId).header(HttpHeaders.AUTHORIZATION, finance.bearer()))
			.andExpect(status().isUnprocessableContent());

		approveKyc(investor);
		Account sibling = api.register();
		addBank(sibling, "DE89 3704 0044 0532 0130 00", "COBADEFFXXX").andExpect(status().isCreated());

		mvc.perform(post("/api/v1/admin/bank-accounts/{id}/verify", accountId).header(HttpHeaders.AUTHORIZATION, finance.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.account.status").value("VERIFIED"))
			.andExpect(jsonPath("$.otherUsersWithSameAccount").value(1));
		assertThat(api.auditCount("BANK_ACCOUNT_VERIFIED", accountId)).isEqualTo(1);
	}

	@Test
	void staffWithoutInvestorPortalCannotRegisterPayoutAccounts() throws Exception {
		Account finance = api.staff("FINANCE");
		addBank(finance, VALID_IBAN, "NWBKGB2L").andExpect(status().isForbidden());
	}

	// --------------------------------------------------------------------------- suspension

	@Test
	void suspensionEndsSessionsImmediatelyAndBlocksSignIn() throws Exception {
		Account investor = api.register();
		Account admin = api.staff("ADMIN");

		mvc.perform(post("/api/v1/admin/users/{id}/suspend", investor.id()).header(HttpHeaders.AUTHORIZATION, admin.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Suspicious activity\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.summary.status").value("SUSPENDED"))
			.andExpect(jsonPath("$.statusReason").value("Suspicious activity"));

		mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
		assertThat(api.loginResult(investor.email(), TestApi.PASSWORD).getResponse().getStatus()).isEqualTo(403);

		mvc.perform(post("/api/v1/admin/users/{id}/reactivate", investor.id()).header(HttpHeaders.AUTHORIZATION, admin.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Cleared after review\"}"))
			.andExpect(status().isOk());
		assertThat(api.loginResult(investor.email(), TestApi.PASSWORD).getResponse().getStatus()).isEqualTo(200);
		assertThat(api.auditCount("USER_SUSPENDED", investor.id())).isEqualTo(1);
		assertThat(api.auditCount("USER_REACTIVATED", investor.id())).isEqualTo(1);
	}

	@Test
	void staffCannotSuspendMorePrivilegedAccountsOrThemselves() throws Exception {
		Account admin = api.staff("ADMIN");
		UUID superAdminId = api.userIdByEmail(TestApi.ADMIN_EMAIL);
		mvc.perform(post("/api/v1/admin/users/{id}/suspend", superAdminId).header(HttpHeaders.AUTHORIZATION, admin.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"coup\"}"))
			.andExpect(status().isForbidden());
		mvc.perform(post("/api/v1/admin/users/{id}/suspend", admin.id()).header(HttpHeaders.AUTHORIZATION, admin.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"oops\"}"))
			.andExpect(status().isForbidden());
	}

	@Test
	void userDirectorySearchAndFilters() throws Exception {
		Account investor = api.register();
		Account support = api.staff("SUPPORT");
		String fragment = investor.email().substring(5, 17);

		mvc.perform(get("/api/v1/admin/users").param("q", fragment.toUpperCase()).header(HttpHeaders.AUTHORIZATION, support.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].email").value(investor.email()));
		mvc.perform(get("/api/v1/admin/users").param("q", fragment).param("kycStatus", "APPROVED")
				.header(HttpHeaders.AUTHORIZATION, support.bearer()))
			.andExpect(jsonPath("$.totalElements").value(0));
		// LIKE wildcards in the search term are literal.
		mvc.perform(get("/api/v1/admin/users").param("q", "%").header(HttpHeaders.AUTHORIZATION, support.bearer()))
			.andExpect(jsonPath("$.totalElements").value(0));
		mvc.perform(get("/api/v1/admin/users").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isForbidden());
	}

	// -------------------------------------------------------------------------- helpers

	private void approveKyc(Account investor) throws Exception {
		String submissionId = JsonPath.read(submitValidKyc(investor).andReturn().getResponse().getContentAsString(), "$.id");
		mvc.perform(post("/api/v1/admin/kyc/{id}/approve", submissionId).header(HttpHeaders.AUTHORIZATION, api.admin().bearer()))
			.andExpect(status().isOk());
	}

	private ResultActions addBank(Account owner, String accountNumber, String routingCode) throws Exception {
		return mvc.perform(post("/api/v1/users/me/bank-accounts").header(HttpHeaders.AUTHORIZATION, owner.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"accountHolderName":"Test Investor","bankName":"Test Bank","country":"GB","currency":"INR",
					 "accountNumber":"%s","routingCode":"%s"}
					""".formatted(accountNumber, routingCode)));
	}

	private ResultActions submitValidKyc(Account investor) throws Exception {
		return submitKyc(investor, Map.of("identityFront", png(), "selfie", jpeg()));
	}

	private ResultActions submitKyc(Account investor, Map<String, MockMultipartFile> files) throws Exception {
		String expiry = java.time.LocalDate.now().plusYears(5).toString();
		MockMultipartHttpServletRequestBuilder request = multipart("/api/v1/users/me/kyc");
		request.file(new MockMultipartFile("submission", "", MediaType.APPLICATION_JSON_VALUE, """
				{"legalFirstName":"Test","legalLastName":"Investor","dateOfBirth":"1985-06-15","nationality":"GB",
				 "documentType":"PASSPORT","documentNumber":"P 987-654-321","documentIssuingCountry":"GB",
				 "documentExpiryDate":"%s"}
				""".formatted(expiry).getBytes(StandardCharsets.UTF_8)));
		files.forEach((part, file) -> request.file(new MockMultipartFile(part, file.getOriginalFilename(),
				file.getContentType(), getBytes(file))));
		return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, investor.bearer()));
	}

	private static MockMultipartFile png() {
		byte[] body = (new String(PNG_HEADER, StandardCharsets.ISO_8859_1) + SECRET_MARKER)
			.getBytes(StandardCharsets.ISO_8859_1);
		return new MockMultipartFile("identityFront", "front.png", "image/png", body);
	}

	private static MockMultipartFile jpeg() {
		byte[] body = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3, 4 };
		return new MockMultipartFile("selfie", "selfie.jpg", "image/jpeg", body);
	}

	private static byte[] getBytes(MockMultipartFile file) {
		try {
			return file.getBytes();
		}
		catch (java.io.IOException ex) {
			throw new java.io.UncheckedIOException(ex);
		}
	}

	private static String profileBody(String firstName, String phone, String dateOfBirth) {
		return """
				{"firstName":"%s","lastName":"Investor",%s"dateOfBirth":%s,"nationality":"GB",
				 "address":{"line1":"1 Dock Road","city":"London","postalCode":"E14 5AB","country":"GB"}}
				""".formatted(firstName, phone == null ? "" : "\"phone\":\"" + phone + "\",",
				dateOfBirth == null ? "null" : "\"" + dateOfBirth + "\"");
	}

}
