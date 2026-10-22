package com.sealease.backend.user;

import com.jayway.jsonpath.JsonPath;
import com.sealease.backend.kyc.service.KycFollowUpService;
import com.sealease.backend.support.BankAccountUploads;
import com.sealease.backend.support.IndianIdFixtures;
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
import static org.hamcrest.Matchers.not;
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
	private static final String SECRET_MARKER = "AADHAAR-SCAN-SECRET-MARKER";
	private static final String VALID_ACCOUNT = "100098765432";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private KycFollowUpService kycFollowUps;

	private TestApi api;

	@BeforeEach
	void setUp() {
		api = new TestApi(mvc, jdbc);
	}

	// --------------------------------------------------------------------------- profile

	@Test
	void profileIsCreatedAtRegistrationAndEditable() throws Exception {
		Account investor = api.register();
		String phone = mobile();

		mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.kycStatus").value("NOT_SUBMITTED"))
			.andExpect(jsonPath("$.twoFactorEnabled").value(false));

		mvc.perform(put("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(profileBody("Test", phone, "1990-04-01")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.firstName").value("Test"))
			.andExpect(jsonPath("$.address.city").value("London"))
			.andExpect(jsonPath("$.emailNotifications").value(true))
			.andExpect(jsonPath("$.smsNotifications").value(false));

		// The name given at registration cannot be changed.
		mvc.perform(put("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(profileBody("Ada", phone, "1990-04-01")))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("cannot be changed after registration")));

		// Notification preferences are settings of their own; saving the profile leaves them alone.
		mvc.perform(put("/api/v1/users/me/notification-preferences").header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"emailNotifications\":false,\"smsNotifications\":true}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.emailNotifications").value(false))
			.andExpect(jsonPath("$.smsNotifications").value(true));
		mvc.perform(put("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(profileBody("Test", phone, "1990-04-01")))
			.andExpect(jsonPath("$.emailNotifications").value(false))
			.andExpect(jsonPath("$.smsNotifications").value(true));
		mvc.perform(put("/api/v1/users/me/notification-preferences").header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"emailNotifications\":true}"))
			.andExpect(status().isBadRequest());

		String changes = jdbc.queryForObject("""
				SELECT string_agg(new_value::text, ' ') FROM audit_logs WHERE action = 'PROFILE_UPDATED' AND entity_id = ?
				""", String.class, investor.id().toString());
		assertThat(changes).contains("address", "phone", "notificationPreferences").doesNotContain(phone, "London");
	}

	private static final String PROFILE_BODY = """
			{"firstName":"Test","lastName":"Investor",
			 "address":{"line1":"12 Marine Drive","city":"Mumbai","stateRegion":"Maharashtra","postalCode":"400020"}}
			""";

	@Test
	void addressIsRequiredAndCountriesDefaultToIndia() throws Exception {
		Account investor = api.register();
		mvc.perform(put("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"firstName":"Test","lastName":"Investor","address":{"line1":" ","line2":"Flat 4"}}"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[*].field",
					org.hamcrest.Matchers.hasItems("address.line1", "address.city", "address.stateRegion", "address.postalCode")))
			.andExpect(jsonPath("$.fieldErrors[*].field", org.hamcrest.Matchers.not(hasItem("address.line2"))));
		mvc.perform(put("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"firstName\":\"Test\",\"lastName\":\"Investor\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[*].field", hasItem("address")));

		mvc.perform(put("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(PROFILE_BODY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.nationality").value("IN"))
			.andExpect(jsonPath("$.address.country").value("IN"));
	}

	@Test
	void aMobileNumberBelongsToOneAccount() throws Exception {
		Account first = api.register();
		Account second = api.register();
		String number = mobile();
		mvc.perform(put("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, first.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(profileBody("Test", number, "1990-04-01")))
			.andExpect(status().isOk());
		// Saving again keeps your own number.
		mvc.perform(put("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, first.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(profileBody("Test", number, "1990-04-01")))
			.andExpect(status().isOk());

		// The same number written differently is still the same number.
		String spaced = number.substring(0, 5) + " " + number.substring(5);
		for (String same : List.of(number, "+91 " + spaced, "0" + number, "+91-" + number)) {
			mvc.perform(put("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, second.bearer())
					.contentType(MediaType.APPLICATION_JSON).content(profileBody("Test", same, "1990-04-01")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("already registered with another account")));
		}

		// Once the first account changes its number, the old one is free.
		mvc.perform(put("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, first.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(profileBody("Test", mobile(), "1990-04-01")))
			.andExpect(status().isOk());
		mvc.perform(put("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, second.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(profileBody("Test", "+91 " + spaced, "1990-04-01")))
			.andExpect(status().isOk());
	}

	@Test
	void theMobileNumberIsStoredInternationallyWithItsCountry() throws Exception {
		Account investor = api.register();
		mvc.perform(put("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(profileBody("Test", "020 7946 0000", "1990-04-01")
					.replace("\"phone\":", "\"phoneCountry\":\"GB\",\"phone\":")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[?(@.field == 'phone')].message",
					hasItem("Enter a valid mobile number for the selected country")));

		String uk = "7400" + String.format("%06d", java.util.concurrent.ThreadLocalRandom.current().nextInt(1_000_000));
		mvc.perform(put("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(profileBody("Test", "0" + uk, "1990-04-01")
					.replace("\"phone\":", "\"phoneCountry\":\"GB\",\"phone\":")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.phone").value("+44" + uk))
			.andExpect(jsonPath("$.phoneCountry").value("GB"));
	}

	/** A random Indian mobile number: tests share a database, and a number may belong to one account only. */
	private static String mobile() {
		return "9" + String.format("%09d", java.util.concurrent.ThreadLocalRandom.current().nextInt(1_000_000_000));
	}

	@Test
	void profileValidationRejectsBadInput() throws Exception {
		Account investor = api.register();
		mvc.perform(put("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(profileBody("Test", "call me maybe", "1990-04-01")
					.replace("\"GB\"", "\"XX\"")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[*].field", hasItem("phone")))
			.andExpect(jsonPath("$.fieldErrors[*].field", hasItem("address.country")));

		// A postal code is exactly 6 digits.
		for (String postalCode : List.of("40002", "4000201", "40 0020", "E14 5AB")) {
			mvc.perform(put("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, investor.bearer())
					.contentType(MediaType.APPLICATION_JSON).content(profileBody("Test", null, "1990-04-01")
						.replace("\"560001\"", "\"" + postalCode + "\"")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'address.postalCode')].message",
						hasItem("Enter a 6-digit postal code, numbers only")));
		}

		String minor = java.time.LocalDate.now().minusYears(17).toString();
		mvc.perform(put("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(profileBody("Test", null, minor)))
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
	void onlyAdminsSeeTheFullMobileNumberOnUserDetails() throws Exception {
		Account investor = api.register();
		String phone = jdbc.queryForObject("SELECT phone FROM user_profiles WHERE user_id = ?", String.class, investor.id());
		String digits = phone.replaceAll("\\D", "");
		String masked = "•••••• " + digits.substring(digits.length() - 4);

		for (Account admin : List.of(api.admin(), api.staff("ADMIN"))) {
			mvc.perform(get("/api/v1/admin/users/{id}", investor.id()).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
				.andExpect(jsonPath("$.profile.phone").value(phone));
		}
		for (String role : List.of("SUPPORT", "FINANCE", "VERIFICATION", "ADMINISTRATION")) {
			mvc.perform(get("/api/v1/admin/users/{id}", investor.id()).header(HttpHeaders.AUTHORIZATION, api.staff(role).bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.profile.phone").value(masked));
		}
		// The investor still sees their own number in full.
		mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.phone").value(phone));
	}

	@Test
	void onlyAdminsSeeEmployeesInTheUserDirectory() throws Exception {
		Account investor = api.register();
		Account employee = api.staff("SALES");

		for (Account admin : List.of(api.admin(), api.staff("ADMIN"))) {
			for (Account who : List.of(investor, employee)) {
				mvc.perform(get("/api/v1/admin/users").param("q", who.email()).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
					.andExpect(jsonPath("$.content.length()").value(1));
			}
			mvc.perform(get("/api/v1/admin/users/{id}", employee.id()).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.summary.kind").value("EMPLOYEE"));
			// Each account is marked investor or employee, and the directory filters by it.
			mvc.perform(get("/api/v1/admin/users").param("q", investor.email()).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
				.andExpect(jsonPath("$.content[0].kind").value("INVESTOR"));
			for (String kind : List.of("INVESTOR", "EMPLOYEE")) {
				for (Account who : List.of(investor, employee)) {
					mvc.perform(get("/api/v1/admin/users").param("q", who.email()).param("kind", kind)
							.header(HttpHeaders.AUTHORIZATION, admin.bearer()))
						.andExpect(jsonPath("$.content.length()").value((who == employee) == kind.equals("EMPLOYEE") ? 1 : 0));
				}
			}
		}
		for (String role : List.of("SUPPORT", "FINANCE", "VERIFICATION", "ADMINISTRATION")) {
			Account viewer = api.staff(role);
			mvc.perform(get("/api/v1/admin/users").param("q", investor.email()).header(HttpHeaders.AUTHORIZATION, viewer.bearer()))
				.andExpect(jsonPath("$.content.length()").value(1));
			mvc.perform(get("/api/v1/admin/users").param("q", employee.email()).header(HttpHeaders.AUTHORIZATION, viewer.bearer()))
				.andExpect(jsonPath("$.content.length()").value(0))
				.andExpect(jsonPath("$.totalElements").value(0));
			mvc.perform(get("/api/v1/admin/users").param("size", "200").header(HttpHeaders.AUTHORIZATION, viewer.bearer()))
				.andExpect(jsonPath("$.content[*].id", not(hasItem(viewer.id().toString()))));
			mvc.perform(get("/api/v1/admin/users/{id}", investor.id()).header(HttpHeaders.AUTHORIZATION, viewer.bearer()))
				.andExpect(status().isOk());
			mvc.perform(get("/api/v1/admin/users/{id}", employee.id()).header(HttpHeaders.AUTHORIZATION, viewer.bearer()))
				.andExpect(status().isNotFound());
		}
	}

	@Test
	void investorsWhoHaveNotVerifiedTheirIdentityADayAfterRegisteringGetOneTicket() throws Exception {
		Account late = api.register();
		Account recent = api.register();
		Account submitted = api.register();
		submitValidKyc(submitted).andExpect(status().isCreated());
		Account staff = api.staff("SUPPORT");
		for (Account a : List.of(late, submitted, staff)) {
			jdbc.update("UPDATE users SET created_at = now() - interval '25 hours' WHERE id = ?", a.id());
		}

		kycFollowUps.openDue();
		kycFollowUps.openDue();

		String ticketId = JsonPath.read(mvc.perform(get("/api/v1/support/tickets").header(HttpHeaders.AUTHORIZATION, late.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.content[0].subject").value("Verify your identity to start investing"))
			.andExpect(jsonPath("$.content[0].status").value("OPEN"))
			.andReturn().getResponse().getContentAsString(), "$.content[0].id");
		mvc.perform(get("/api/v1/support/tickets/{id}", ticketId).header(HttpHeaders.AUTHORIZATION, late.bearer()))
			.andExpect(jsonPath("$.messages[0].authorName").value("SeaLease support"))
			.andExpect(jsonPath("$.messages[0].fromStaff").value(true));
		for (Account a : List.of(recent, submitted, staff)) {
			assertThat(jdbc.queryForObject("SELECT count(*) FROM support_tickets WHERE user_id = ?", Integer.class, a.id()))
				.as(a.email()).isZero();
		}

		// Submitting the documents resolves it.
		submitValidKyc(late).andExpect(status().isCreated());
		mvc.perform(get("/api/v1/support/tickets/{id}", ticketId).header(HttpHeaders.AUTHORIZATION, late.bearer()))
			.andExpect(jsonPath("$.ticket.status").value("RESOLVED"));
	}

	@Test
	void kycNeedsCompletePersonalDetailsFirst() throws Exception {
		// The mobile number was given at sign-up; the rest is still to fill in.
		Account investor = api.register();
		submitValidKyc(investor, false)
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.message").value(
					"Complete your personal details first (Profile, Personal details): date of birth, nationality, address"));
		mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.kycStatus").value("NOT_SUBMITTED"));

		completePersonalDetails(investor);
		submitValidKyc(investor, false).andExpect(status().isCreated());
	}

	@Test
	void kycRequiresEvidenceAndRejectsDisguisedFiles() throws Exception {
		Account investor = api.register();
		completePersonalDetails(investor);

		// Each number comes with its card.
		submitKyc(investor, Map.of("panCard", jpeg()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Upload your Aadhaar card with the Aadhaar number"));
		submitKyc(investor, null, null, Map.of())
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Upload your Aadhaar card or your PAN card"));

		var html = new MockMultipartFile("panCard", "pan.png", "image/png",
				"<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8));
		submitKyc(investor, Map.of("aadhaarCard", png(), "panCard", html))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Document KYC_PAN_CARD must be a PDF, JPEG or PNG file"));

		// Numbers are checked: 12 digits for Aadhaar, and the PAN format.
		submitKyc(investor, "2345 6789 012", IndianIdFixtures.pan(), validKycFiles())
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("An Aadhaar number has exactly 12 digits"));
		submitKyc(investor, IndianIdFixtures.aadhaar(), "ABCD1234EF", validKycFiles())
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("PAN is not valid")));

		mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.kycStatus").value("NOT_SUBMITTED"));
	}

	@Test
	void aadhaarAndPanCanBeUploadedAtDifferentTimesAndAreReviewedOnceBothAreIn() throws Exception {
		Account investor = api.register();
		completePersonalDetails(investor);
		Account reviewer = api.staff("ADMINISTRATION");

		// PAN first: a draft, not under review yet.
		String draft = JsonPath.read(submitKyc(investor, null, IndianIdFixtures.pan(), Map.of("panCard", jpeg()))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.panNumber").exists())
			.andExpect(jsonPath("$.aadhaarNumber").doesNotExist())
			.andReturn().getResponse().getContentAsString(), "$.id");
		mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.kycStatus").value("NOT_SUBMITTED"));
		mvc.perform(get("/api/v1/users/me/kyc").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.id").value(draft)).andExpect(jsonPath("$.status").value("DRAFT"));
		mvc.perform(get("/api/v1/admin/kyc").param("size", "200").header(HttpHeaders.AUTHORIZATION, reviewer.bearer()))
			.andExpect(jsonPath("$.content[*].id", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(draft))));
		mvc.perform(post("/api/v1/admin/kyc/{id}/approve", draft).header(HttpHeaders.AUTHORIZATION, reviewer.bearer()))
			.andExpect(status().isConflict());

		// Sent again while a draft, the PAN replaces the earlier one.
		String pan = IndianIdFixtures.pan();
		submitKyc(investor, null, pan, Map.of("panCard", jpeg()))
			.andExpect(jsonPath("$.id").value(draft))
			.andExpect(jsonPath("$.panNumber").value(pan))
			.andExpect(jsonPath("$.documents.length()").value(1));

		// The Aadhaar later: now it holds both and goes for review, as one submission.
		submitKyc(investor, IndianIdFixtures.aadhaar(), null, Map.of("aadhaarCard", png()))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").value(draft))
			.andExpect(jsonPath("$.status").value("PENDING"))
			.andExpect(jsonPath("$.documents[*].purpose",
					org.hamcrest.Matchers.containsInAnyOrder("KYC_AADHAAR_CARD", "KYC_PAN_CARD")));
		mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.kycStatus").value("PENDING"));
		mvc.perform(get("/api/v1/admin/kyc").param("size", "200").header(HttpHeaders.AUTHORIZATION, reviewer.bearer()))
			.andExpect(jsonPath("$.content[*].id", hasItem(draft)));
		submitKyc(investor, null, IndianIdFixtures.pan(), Map.of("panCard", jpeg())).andExpect(status().isConflict());

		// After a rejection, the next upload starts a new draft.
		api.assignReview("kyc", draft, reviewer);
		mvc.perform(post("/api/v1/admin/kyc/{id}/reject", draft).header(HttpHeaders.AUTHORIZATION, reviewer.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"The PAN card photo is blurred\"}"))
			.andExpect(status().isOk());
		submitKyc(investor, null, IndianIdFixtures.pan(), Map.of("panCard", jpeg()))
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.id", org.hamcrest.Matchers.not(draft)));
	}

	@Test
	void kycSubmissionReviewAndApproval() throws Exception {
		Account investor = api.register();
		completePersonalDetails(investor);
		String aadhaar = IndianIdFixtures.aadhaar();
		String pan = IndianIdFixtures.pan();
		// Typed with spaces and in lower case: stored normalised.
		String submissionId = JsonPath.read(submitKyc(investor,
				aadhaar.substring(0, 4) + " " + aadhaar.substring(4, 8) + " " + aadhaar.substring(8), pan.toLowerCase(),
				validKycFiles())
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("PENDING"))
			// Full numbers, normalised: digits only, PAN in capitals.
			.andExpect(jsonPath("$.aadhaarNumber").value(aadhaar))
			.andExpect(jsonPath("$.panNumber").value(pan))
			.andExpect(jsonPath("$.documentType").doesNotExist())
			.andExpect(jsonPath("$.reviewedBy").doesNotExist())
			.andExpect(jsonPath("$.documents[*].purpose",
					org.hamcrest.Matchers.containsInAnyOrder("KYC_AADHAAR_CARD", "KYC_PAN_CARD")))
			.andReturn().getResponse().getContentAsString(), "$.id");

		// Only one submission under review at a time.
		submitValidKyc(investor).andExpect(status().isConflict());
		// The name cannot be changed (at any time).
		mvc.perform(put("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(profileBody("Someone", null, null)))
			.andExpect(status().isUnprocessableContent());

		// Numbers and evidence are encrypted at rest.
		Map<String, Object> numbers = jdbc.queryForMap(
				"SELECT aadhaar_number_encrypted, pan_number_encrypted FROM kyc_submissions WHERE id = ?::uuid", submissionId);
		assertThat(numbers.values()).allSatisfy(v -> assertThat((String) v).doesNotContain(aadhaar).doesNotContain(pan));
		byte[] stored = jdbc.queryForObject("""
				SELECT d.content FROM stored_documents d JOIN kyc_documents k ON k.document_id = d.id
				WHERE k.submission_id = ?::uuid AND k.purpose = 'KYC_AADHAAR_CARD'
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
			.andExpect(jsonPath("$.submission.aadhaarNumber").value(aadhaar))
			.andExpect(jsonPath("$.submission.panNumber").value(pan))
			.andReturn().getResponse().getContentAsString();
		String frontId = ((List<String>) JsonPath.read(detail,
				"$.submission.documents[?(@.purpose == 'KYC_AADHAAR_CARD')].id")).getFirst();

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
		assertThat(api.auditCount("KYC_SUBMISSION_VIEWED", submissionId)).isEqualTo(1);

		// A document id from another submission cannot be fetched through this one.
		mvc.perform(get("/api/v1/admin/kyc/{id}/documents/{doc}", submissionId, UUID.randomUUID())
				.header(HttpHeaders.AUTHORIZATION, reviewer.bearer()))
			.andExpect(status().isNotFound());

		api.assignReview("kyc", submissionId, reviewer);
		mvc.perform(post("/api/v1/admin/kyc/{id}/approve", submissionId).header(HttpHeaders.AUTHORIZATION, reviewer.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("APPROVED"));
		mvc.perform(post("/api/v1/admin/kyc/{id}/approve", submissionId).header(HttpHeaders.AUTHORIZATION, reviewer.bearer()))
			.andExpect(status().isConflict());
		mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.kycStatus").value("APPROVED"));
		// The investor is told (in the app, and by email to those who keep email notifications on).
		assertThat(notifications(investor, "KYC_APPROVED")).containsExactly("Your identity is verified");
		submitValidKyc(investor).andExpect(status().isUnprocessableContent());
	}

	@Test
	void anAadhaarNumberOrPanBelongsToOneAccount() throws Exception {
		String aadhaar = IndianIdFixtures.aadhaar();
		String pan = IndianIdFixtures.pan();
		Account first = api.register();
		completePersonalDetails(first);
		String firstSubmission = JsonPath.read(submitKyc(first, aadhaar, pan, validKycFiles())
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

		// Another account cannot use either number while the first is under review (or approved).
		Account second = api.register();
		completePersonalDetails(second);
		submitKyc(second, aadhaar, IndianIdFixtures.pan(), validKycFiles())
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Aadhaar number is already registered")));
		submitKyc(second, IndianIdFixtures.aadhaar(), pan.toLowerCase(), validKycFiles())
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("PAN is already registered")));
		mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, second.bearer()))
			.andExpect(jsonPath("$.kycStatus").value("NOT_SUBMITTED"));

		// Rejecting the first submission frees the numbers.
		api.assignReview("kyc", firstSubmission, api.admin());
		mvc.perform(post("/api/v1/admin/kyc/{id}/reject", firstSubmission).header(HttpHeaders.AUTHORIZATION, api.admin().bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Not the account holder\"}"))
			.andExpect(status().isOk());
		assertThat(notificationBodies(first, "KYC_REJECTED")).singleElement().asString()
			.contains("Reason: Not the account holder.");
		submitKyc(second, aadhaar, pan, validKycFiles()).andExpect(status().isCreated());
	}

	@Test
	void rejectedKycCanBeResubmitted() throws Exception {
		Account investor = api.register();
		String submissionId = JsonPath.read(submitValidKyc(investor).andReturn().getResponse().getContentAsString(), "$.id");
		Account reviewer = api.staff("ADMINISTRATION");

		api.assignReview("kyc", submissionId, reviewer);
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
	void verifiersAssignAReviewToThemselvesBeforeDecidingIt() throws Exception {
		Account investor = api.register();
		String submissionId = JsonPath.read(submitValidKyc(investor).andReturn().getResponse().getContentAsString(), "$.id");
		Account verifier = api.staff("VERIFICATION");
		Account colleague = api.staff("VERIFICATION");

		// The verification team sees users and the review queue, but not money.
		mvc.perform(get("/api/v1/admin/users/{id}", investor.id()).header(HttpHeaders.AUTHORIZATION, verifier.bearer()))
			.andExpect(status().isOk());
		mvc.perform(get("/api/v1/admin/kyc").param("size", "200").header(HttpHeaders.AUTHORIZATION, verifier.bearer()))
			.andExpect(jsonPath("$.content[*].id", hasItem(submissionId)));
		mvc.perform(get("/api/v1/admin/withdrawals").header(HttpHeaders.AUTHORIZATION, verifier.bearer()))
			.andExpect(status().isForbidden());

		// Nobody may decide a review that is not assigned to them.
		kycDecision(verifier, submissionId, "approve").andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Assign this review to yourself")));
		kycDecision(verifier, submissionId, "assign").andExpect(status().isOk())
			.andExpect(jsonPath("$.assignee.id").value(verifier.id().toString()));
		kycDecision(verifier, submissionId, "assign").andExpect(status().isOk());
		kycDecision(colleague, submissionId, "assign").andExpect(status().isConflict());
		kycDecision(colleague, submissionId, "approve").andExpect(status().isUnprocessableContent());
		kycDecision(colleague, submissionId, "unassign").andExpect(status().isUnprocessableContent());
		// The investor does not see who is reviewing them.
		mvc.perform(get("/api/v1/users/me/kyc").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$.assignee").doesNotExist());

		// Given back to the queue, a colleague takes it and decides it.
		kycDecision(verifier, submissionId, "unassign").andExpect(status().isOk())
			.andExpect(jsonPath("$.assignee").doesNotExist());
		kycDecision(colleague, submissionId, "assign").andExpect(status().isOk());
		kycDecision(colleague, submissionId, "approve").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("APPROVED"))
			.andExpect(jsonPath("$.assignee.id").value(colleague.id().toString()));
		kycDecision(colleague, submissionId, "assign").andExpect(status().isConflict());
		assertThat(api.auditCount("KYC_ASSIGNED", submissionId)).isEqualTo(2);
		assertThat(api.auditCount("KYC_UNASSIGNED", submissionId)).isEqualTo(1);

		// Bank accounts the same way.
		String accountId = JsonPath.read(addBank(investor, "50100099887766", "HDFC0001234")
			.andReturn().getResponse().getContentAsString(), "$.id");
		verifyBank(verifier, accountId).andExpect(status().isUnprocessableContent());
		mvc.perform(post("/api/v1/admin/bank-accounts/{id}/assign", accountId).header(HttpHeaders.AUTHORIZATION, verifier.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.assignee.id").value(verifier.id().toString()));
		mvc.perform(post("/api/v1/admin/bank-accounts/{id}/assign", accountId).header(HttpHeaders.AUTHORIZATION, colleague.bearer()))
			.andExpect(status().isConflict());
		verifyBank(verifier, accountId).andExpect(status().isOk())
			.andExpect(jsonPath("$.account.status").value("VERIFIED"));
		assertThat(api.auditCount("BANK_ACCOUNT_ASSIGNED", accountId)).isEqualTo(1);
	}

	private ResultActions kycDecision(Account reviewer, String submissionId, String decision) throws Exception {
		return mvc.perform(post("/api/v1/admin/kyc/{id}/" + decision, submissionId)
			.header(HttpHeaders.AUTHORIZATION, reviewer.bearer()));
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
		String first = JsonPath.read(addBank(investor, VALID_ACCOUNT, "HDFC0000001")
			.andExpect(status().isCreated())
			// The holder is the investor's registered name; a different name in the request is ignored.
			.andExpect(jsonPath("$.accountHolderName").value("Test Investor"))
			.andExpect(jsonPath("$.accountNumberMasked").value("•••• 5432"))
			.andExpect(jsonPath("$.primary").value(true))
			.andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"))
			.andReturn().getResponse().getContentAsString(), "$.id");

		// Account numbers are 8 to 15 digits and nothing else.
		for (String invalid : List.of("1234567", "1234567890123456", "GB82WEST12345698", "1000-9876-5432", "12345678a")) {
			addBank(investor, invalid, "HDFC0000001")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("accountNumber"));
		}
		// The routing code is an IFSC code: 4 letters, a zero, then 6 letters or digits (any case).
		for (String invalid : List.of("HDFC000001", "HDFC1000001", "HDF00001234", "HDFC-000001", "026009593", "NWBKGB2L")) {
			addBank(investor, "200098765432", invalid)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("routingCode"));
		}
		addBank(investor, VALID_ACCOUNT, "hdfc0000001").andExpect(status().isConflict());

		String second = JsonPath.read(addBank(investor, "12345678", "SBIN0000001")
			.andExpect(jsonPath("$.primary").value(false))
			.andReturn().getResponse().getContentAsString(), "$.id");
		// Only a verified account can be primary.
		makePrimary(investor, second).andExpect(status().isUnprocessableContent());
		approveKyc(investor);
		Account verifier = api.staff("FINANCE");
		api.assignReview("bank-accounts", second, verifier);
		verifyBank(verifier, second).andExpect(status().isOk());
		makePrimary(investor, second)
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
	void bankAccountsNeedABankStatementOrCancelledCheque() throws Exception {
		Account investor = api.register();
		approveKyc(investor);
		Account finance = api.staff("FINANCE");

		// No proof, or no proof type: refused.
		var withoutProof = multipart("/api/v1/users/me/bank-accounts").file(new MockMultipartFile("account", "",
				MediaType.APPLICATION_JSON_VALUE, """
						{"accountHolderName":"Test Investor","bankName":"Test Bank","country":"IN","currency":"INR",
						 "accountNumber":"50100012345678","routingCode":"HDFC0001234"}""".getBytes(StandardCharsets.UTF_8)));
		mvc.perform(withoutProof.param("proofType", "CANCELLED_CHEQUE").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Upload a bank statement or a cancelled cheque for this account"));

		String accountId = JsonPath.read(addBank(investor, "50100012345678", "HDFC0001234")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.proofType").value("BANK_STATEMENT"))
			.andReturn().getResponse().getContentAsString(), "$.id");

		// The verifier opens the statement; each view is audited. Investors cannot open it through this route.
		mvc.perform(get("/api/v1/admin/bank-accounts/{id}/proof", accountId).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isForbidden());
		byte[] proof = mvc.perform(get("/api/v1/admin/bank-accounts/{id}/proof", accountId)
				.header(HttpHeaders.AUTHORIZATION, finance.bearer()))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsByteArray();
		assertThat(proof).isEqualTo(BankAccountUploads.STATEMENT_PDF);
		assertThat(api.auditCount("BANK_ACCOUNT_PROOF_VIEWED", accountId)).isEqualTo(1);

		// An account without proof (added before proofs were required) cannot be verified.
		String older = JsonPath.read(addBank(investor, "50100087654321", "HDFC0001234").andReturn().getResponse()
			.getContentAsString(), "$.id");
		jdbc.update("UPDATE bank_accounts SET proof_type = NULL, proof_document_id = NULL WHERE id = ?::uuid", older);
		api.assignReview("bank-accounts", older, finance);
		mvc.perform(post("/api/v1/admin/bank-accounts/{id}/verify", older).header(HttpHeaders.AUTHORIZATION, finance.bearer()))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("no bank statement or cancelled cheque")));
		api.assignReview("bank-accounts", accountId, finance);
		mvc.perform(post("/api/v1/admin/bank-accounts/{id}/verify", accountId).header(HttpHeaders.AUTHORIZATION, finance.bearer()))
			.andExpect(status().isOk());
	}

	@Test
	void bankVerificationRequiresApprovedKycAndFlagsSharedAccounts() throws Exception {
		Account investor = api.register();
		String accountId = JsonPath.read(addBank(investor, "370400440532", "COBA0000001")
			.andReturn().getResponse().getContentAsString(), "$.id");
		Account finance = api.staff("FINANCE");

		api.assignReview("bank-accounts", accountId, finance);
		mvc.perform(post("/api/v1/admin/bank-accounts/{id}/verify", accountId).header(HttpHeaders.AUTHORIZATION, finance.bearer()))
			.andExpect(status().isUnprocessableContent());

		approveKyc(investor);
		Account sibling = api.register();
		addBank(sibling, "370400440532", "COBA0000001").andExpect(status().isCreated());

		mvc.perform(post("/api/v1/admin/bank-accounts/{id}/verify", accountId).header(HttpHeaders.AUTHORIZATION, finance.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.account.status").value("VERIFIED"))
			.andExpect(jsonPath("$.otherUsersWithSameAccount").value(1));
		assertThat(api.auditCount("BANK_ACCOUNT_VERIFIED", accountId)).isEqualTo(1);
		assertThat(notifications(investor, "BANK_ACCOUNT_VERIFIED")).containsExactly("Your bank account is verified");
	}

	@Test
	void verifiedAccountsCannotBeChangedSoTheInvestorAddsAnotherAndSwitchesPrimary() throws Exception {
		Account investor = api.register();
		approveKyc(investor);
		Account finance = api.staff("FINANCE");
		String old = JsonPath.read(addBank(investor, "11112222", "HDFC0000001").andReturn().getResponse().getContentAsString(), "$.id");
		api.assignReview("bank-accounts", old, finance);
		verifyBank(finance, old).andExpect(status().isOk());

		// There is no way to edit a verified account's details.
		mvc.perform(post("/api/v1/users/me/bank-accounts/{id}/change", old).header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().is4xxClientError());

		// Instead the investor adds a new account. It is not primary, and cannot be until it is verified.
		String added = JsonPath.read(addBank(investor, "33334444", "ICIC0000001")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.primary").value(false))
			.andExpect(jsonPath("$.replacesAccountId").doesNotExist())
			.andReturn().getResponse().getContentAsString(), "$.id");
		makePrimary(investor, added).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Only a verified account")));

		// A rejected account is never primary either; the investor is told why.
		String rejected = JsonPath.read(addBank(investor, "55556666", "SBIN0000001").andReturn().getResponse()
			.getContentAsString(), "$.id");
		api.assignReview("bank-accounts", rejected, finance);
		mvc.perform(post("/api/v1/admin/bank-accounts/{id}/reject", rejected).header(HttpHeaders.AUTHORIZATION, finance.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Name does not match\"}"))
			.andExpect(status().isOk());
		assertThat(notificationBodies(investor, "BANK_ACCOUNT_REJECTED")).singleElement().asString()
			.contains("account ending 6666").contains("Reason: Name does not match.").doesNotContain("55556666");
		makePrimary(investor, rejected).andExpect(status().isUnprocessableContent());
		// The investor no longer sees it, and can add the same account again with the right details.
		mvc.perform(get("/api/v1/users/me/bank-accounts").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(jsonPath("$[?(@.id == '" + rejected + "')]").isEmpty())
			.andExpect(jsonPath("$.length()").value(2));
		String readded = JsonPath.read(addBank(investor, "55556666", "SBIN0000001").andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"))
			.andReturn().getResponse().getContentAsString(), "$.id");
		addBank(investor, "55556666", "SBIN0000001").andExpect(status().isConflict());
		// Finance still sees the rejected one.
		mvc.perform(get("/api/v1/admin/users/{id}/bank-accounts", investor.id()).header(HttpHeaders.AUTHORIZATION, finance.bearer()))
			.andExpect(jsonPath("$[?(@.account.id == '" + rejected + "')].account.status").value(hasItem("REJECTED")));

		// Once verified, the investor switches primary to it; the old account stays verified and can be switched back to.
		api.assignReview("bank-accounts", added, finance);
		verifyBank(finance, added).andExpect(status().isOk()).andExpect(jsonPath("$.account.primary").value(false));
		makePrimary(investor, added).andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.id == '" + added + "')].primary").value(hasItem(true)))
			.andExpect(jsonPath("$[?(@.id == '" + old + "')].primary").value(hasItem(false)))
			.andExpect(jsonPath("$[?(@.id == '" + old + "')].status").value(hasItem("VERIFIED")));
		assertThat(api.auditCount("BANK_ACCOUNT_PRIMARY_CHANGED", added)).isEqualTo(1);
		makePrimary(investor, old).andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.id == '" + old + "')].primary").value(hasItem(true)));

		// The investor always keeps a verified account: not the primary one, and not the last verified one.
		removeBank(investor, old).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("primary account")));
		removeBank(investor, added).andExpect(status().isNoContent());
		removeBank(investor, old).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("at least one verified bank account")));
		// An account still waiting for verification can always be removed.
		removeBank(investor, readded).andExpect(status().isNoContent());
	}

	private ResultActions removeBank(Account owner, String accountId) throws Exception {
		return mvc.perform(delete("/api/v1/users/me/bank-accounts/{id}", accountId)
			.header(HttpHeaders.AUTHORIZATION, owner.bearer()));
	}

	private ResultActions verifyBank(Account finance, String accountId) throws Exception {
		return mvc.perform(post("/api/v1/admin/bank-accounts/{id}/verify", accountId)
			.header(HttpHeaders.AUTHORIZATION, finance.bearer()));
	}

	private ResultActions makePrimary(Account owner, String accountId) throws Exception {
		return mvc.perform(put("/api/v1/users/me/bank-accounts/{id}/primary", accountId)
			.header(HttpHeaders.AUTHORIZATION, owner.bearer()));
	}

	@Test
	void staffWithoutInvestorPortalCannotRegisterPayoutAccounts() throws Exception {
		Account finance = api.staff("FINANCE");
		addBank(finance, VALID_ACCOUNT, "HDFC0000001").andExpect(status().isForbidden());
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

	@Test
	void userDirectoryShowsAndFiltersUnverifiedEmails() throws Exception {
		Account verified = api.register();
		Account unverified = api.register();
		jdbc.update("UPDATE users SET email_verified_at = now() WHERE id = ?", verified.id());
		jdbc.update("UPDATE users SET email_verified_at = NULL WHERE id = ?", unverified.id());
		Account support = api.staff("SUPPORT");

		mvc.perform(get("/api/v1/admin/users").param("q", unverified.email()).header(HttpHeaders.AUTHORIZATION, support.bearer()))
			.andExpect(jsonPath("$.content[0].emailVerifiedAt").doesNotExist());
		mvc.perform(get("/api/v1/admin/users").param("q", verified.email()).header(HttpHeaders.AUTHORIZATION, support.bearer()))
			.andExpect(jsonPath("$.content[0].emailVerifiedAt").isNotEmpty());
		mvc.perform(get("/api/v1/admin/users").param("q", unverified.email()).param("emailVerified", "false")
				.header(HttpHeaders.AUTHORIZATION, support.bearer()))
			.andExpect(jsonPath("$.totalElements").value(1));
		mvc.perform(get("/api/v1/admin/users").param("q", unverified.email()).param("emailVerified", "true")
				.header(HttpHeaders.AUTHORIZATION, support.bearer()))
			.andExpect(jsonPath("$.totalElements").value(0));
		mvc.perform(get("/api/v1/admin/users").param("q", verified.email()).param("emailVerified", "false")
				.header(HttpHeaders.AUTHORIZATION, support.bearer()))
			.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void everyUserHasAReadableIdFromTheSequence() throws Exception {
		Account first = api.register();
		Account second = api.register();
		Account support = api.staff("SUPPORT");
		String code = JsonPath.read(mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, first.bearer()))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.userCode");
		String next = JsonPath.read(mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, second.bearer()))
			.andReturn().getResponse().getContentAsString(), "$.userCode");
		// "SL" and at least five digits, from 3500 up, one after another.
		assertThat(code).matches("SL\\d{5,}");
		long number = Long.parseLong(code.substring(2));
		assertThat(number).isGreaterThanOrEqualTo(3500);
		assertThat(Long.parseLong(next.substring(2))).isGreaterThan(number);

		// Staff find the account by it, in any case.
		mvc.perform(get("/api/v1/admin/users").param("q", code.toLowerCase()).header(HttpHeaders.AUTHORIZATION, support.bearer()))
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].email").value(first.email()))
			.andExpect(jsonPath("$.content[0].userCode").value(code));
		mvc.perform(get("/api/v1/admin/users/{id}", first.id()).header(HttpHeaders.AUTHORIZATION, support.bearer()))
			.andExpect(jsonPath("$.summary.userCode").value(code));
	}

	// -------------------------------------------------------------------------- helpers

	private void approveKyc(Account investor) throws Exception {
		String submissionId = JsonPath.read(submitValidKyc(investor).andReturn().getResponse().getContentAsString(), "$.id");
		api.assignReview("kyc", submissionId, api.admin());
		mvc.perform(post("/api/v1/admin/kyc/{id}/approve", submissionId).header(HttpHeaders.AUTHORIZATION, api.admin().bearer()))
			.andExpect(status().isOk());
	}

	private ResultActions addBank(Account owner, String accountNumber, String routingCode) throws Exception {
		return mvc.perform(BankAccountUploads.addAccount("""
					{"accountHolderName":"Someone Else","bankName":"Test Bank","country":"GB","currency":"INR",
					 "accountNumber":"%s","routingCode":"%s"}
					""".formatted(accountNumber, routingCode)).header(HttpHeaders.AUTHORIZATION, owner.bearer()));
	}

	private ResultActions submitValidKyc(Account investor) throws Exception {
		return submitValidKyc(investor, true);
	}

	/** Submits valid KYC; first fills in the personal details it requires, unless told not to. */
	private ResultActions submitValidKyc(Account investor, boolean completeProfileFirst) throws Exception {
		if (completeProfileFirst) {
			completePersonalDetails(investor);
		}
		return submitKyc(investor, validKycFiles());
	}

	private void completePersonalDetails(Account investor) throws Exception {
		mvc.perform(put("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, investor.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"firstName":"Test","lastName":"Investor","phone":"%s","dateOfBirth":"1985-06-15","nationality":"IN",
						 "address":{"line1":"12 Marine Drive","city":"Mumbai","stateRegion":"Maharashtra","postalCode":"400020",
						 "country":"IN"}}""".formatted(mobile())))
			.andExpect(status().isOk());
	}

	private static Map<String, MockMultipartFile> validKycFiles() {
		return Map.of("aadhaarCard", png(), "panCard", jpeg());
	}

	private ResultActions submitKyc(Account investor, Map<String, MockMultipartFile> files) throws Exception {
		return submitKyc(investor, IndianIdFixtures.aadhaar(), IndianIdFixtures.pan(), files);
	}

	private ResultActions submitKyc(Account investor, String aadhaar, String pan, Map<String, MockMultipartFile> files)
			throws Exception {
		MockMultipartHttpServletRequestBuilder request = multipart("/api/v1/users/me/kyc");
		request.file(new MockMultipartFile("submission", "", MediaType.APPLICATION_JSON_VALUE, """
				{"aadhaarNumber":"%s","panNumber":"%s"}
				""".formatted(aadhaar == null ? "" : aadhaar, pan == null ? "" : pan).getBytes(StandardCharsets.UTF_8)));
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
				 "address":{"line1":"1 Dock Road","city":"London","stateRegion":"Greater London","postalCode":"560001","country":"GB"}}
				""".formatted(firstName, phone == null ? "" : "\"phone\":\"" + phone + "\",",
				dateOfBirth == null ? "null" : "\"" + dateOfBirth + "\"");
	}

	private List<String> notifications(Account user, String type) {
		return jdbc.queryForList("SELECT title FROM notifications WHERE user_id = ? AND type = ?", String.class,
				user.id(), type);
	}

	private List<String> notificationBodies(Account user, String type) {
		return jdbc.queryForList("SELECT body FROM notifications WHERE user_id = ? AND type = ?", String.class,
				user.id(), type);
	}

}
