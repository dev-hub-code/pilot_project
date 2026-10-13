package com.sealease.backend.auth;

import com.sealease.backend.support.IntegrationTest;
import com.sealease.backend.support.TestApi;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sign-up with email verification switched on: no session until the emailed link is followed. */
@IntegrationTest
@TestPropertySource(properties = {
		"app.auth.email-verification.required=true",
		"app.auth.email-verification.app-url=https://app.sealease.test" })
class EmailVerificationIntegrationTest {

	private static final String PASSWORD = "Correct-Horse-9";
	private static final Pattern CODE = Pattern.compile("^\\s+(\\d{6})$", Pattern.MULTILINE);
	private static final Pattern LINK = Pattern.compile("https://app\\.sealease\\.test/verify-email\\?token=([A-Za-z0-9_-]+)");

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoBean
	private JavaMailSender mail;

	@Test
	void signUpEmailsALinkAndSignInWaitsForIt() throws Exception {
		String email = uniqueEmail();
		mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(registerBody(email)))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.emailVerificationRequired").value(true))
			.andExpect(jsonPath("$.email").value(email))
			.andExpect(jsonPath("$.accessToken").doesNotExist());
		String token = lastLinkTokenSentTo(email);

		login(email).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

		verifyEmail(token).andExpect(status().isNoContent());
		login(email).andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty());

		// Opening the link again (a double click, a mail scanner) is not an error.
		verifyEmail(token).andExpect(status().isNoContent());
	}

	@Test
	void resendingRetiresTheEarlierLink() throws Exception {
		String email = uniqueEmail();
		mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(registerBody(email)))
			.andExpect(status().isAccepted());
		String first = lastLinkTokenSentTo(email);

		resend(email).andExpect(status().isAccepted());
		String second = lastLinkTokenSentTo(email);
		assertThat(second).isNotEqualTo(first);

		verifyEmail(first).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_TOKEN"));
		verifyEmail(second).andExpect(status().isNoContent());
	}

	@Test
	void expiredAndUnknownLinksAreRejected() throws Exception {
		String email = uniqueEmail();
		mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(registerBody(email)))
			.andExpect(status().isAccepted());
		String token = lastLinkTokenSentTo(email);
		jdbc.update("""
				UPDATE email_verification_tokens SET created_at = now() - interval '2 days', expires_at = now() - interval '1 day'
				WHERE user_id = (SELECT id FROM users WHERE email = ?)""", email);

		verifyEmail(token).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_TOKEN"));
		verifyEmail("not-a-real-token").andExpect(status().isBadRequest());
		login(email).andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
	}

	@Test
	void theCodeInTheEmailConfirmsTheAddressToo() throws Exception {
		String email = uniqueEmail();
		mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(registerBody(email)))
			.andExpect(status().isAccepted());
		String code = lastCodeSentTo(email);
		String wrong = code.equals("000000") ? "111111" : "000000";

		verifyCode(email, wrong).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_CODE"));
		verifyCode(email, "12ab").andExpect(status().isBadRequest());
		verifyCode(email, code).andExpect(status().isNoContent());
		login(email).andExpect(status().isOk());
		// Already confirmed: entering it again does no harm.
		verifyCode(email, code).andExpect(status().isNoContent());
	}

	@Test
	void tooManyWrongCodesEndTheEmail() throws Exception {
		String email = uniqueEmail();
		mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(registerBody(email)))
			.andExpect(status().isAccepted());
		String code = lastCodeSentTo(email);
		String token = lastLinkTokenSentTo(email);
		String wrong = code.equals("000000") ? "111111" : "000000";
		for (int i = 0; i < 5; i++) {
			verifyCode(email, wrong).andExpect(status().isBadRequest());
		}
		// The right code no longer works, and neither does the link from that email.
		verifyCode(email, code).andExpect(status().isBadRequest());
		verifyEmail(token).andExpect(status().isBadRequest());

		resend(email).andExpect(status().isAccepted());
		verifyCode(email, lastCodeSentTo(email)).andExpect(status().isNoContent());
	}

	@Test
	void codesForUnknownAddressesAreRejected() throws Exception {
		verifyCode(uniqueEmail(), "123456").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_CODE"));
	}

	@Test
	void resendSaysNothingAboutUnknownOrVerifiedAddresses() throws Exception {
		clearInvocations(mail);
		resend(uniqueEmail()).andExpect(status().isAccepted());
		verify(mail, never()).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));
	}

	private org.springframework.test.web.servlet.ResultActions login(String email) throws Exception {
		return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)));
	}

	private org.springframework.test.web.servlet.ResultActions verifyEmail(String token) throws Exception {
		return mvc.perform(post("/api/v1/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
			.content("{\"token\":\"%s\"}".formatted(token)));
	}

	private org.springframework.test.web.servlet.ResultActions resend(String email) throws Exception {
		return mvc.perform(post("/api/v1/auth/verify-email/resend").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"%s\"}".formatted(email)));
	}

	private String lastCodeSentTo(String email) {
		ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(mail, atLeastOnce()).send(sent.capture());
		SimpleMailMessage last = sent.getAllValues().stream()
			.filter(m -> m.getTo() != null && List.of(m.getTo()).contains(email))
			.toList().getLast();
		Matcher code = CODE.matcher(last.getText());
		assertThat(code.find()).as("verification code in: %s", last.getText()).isTrue();
		assertThat(last.getSubject()).startsWith(code.group(1));
		return code.group(1);
	}

	private org.springframework.test.web.servlet.ResultActions verifyCode(String email, String code) throws Exception {
		return mvc.perform(post("/api/v1/auth/verify-email/code").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"%s\",\"code\":\"%s\"}".formatted(email, code)));
	}

	private String lastLinkTokenSentTo(String email) {
		ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(mail, atLeastOnce()).send(sent.capture());
		List<SimpleMailMessage> toUser = sent.getAllValues().stream()
			.filter(m -> m.getTo() != null && List.of(m.getTo()).contains(email))
			.toList();
		assertThat(toUser).isNotEmpty();
		SimpleMailMessage last = toUser.getLast();
		assertThat(last.getSubject()).endsWith(" is your SeaLease verification code");
		Matcher link = LINK.matcher(last.getText());
		assertThat(link.find()).as("verification link in: %s", last.getText()).isTrue();
		return link.group(1);
	}

	private static String registerBody(String email) {
		return """
				{"email":"%s","password":"%s","firstName":"Vera","lastName":"Fied","phone":"%s"}""".formatted(email, PASSWORD,
				TestApi.mobile());
	}

	private static String uniqueEmail() {
		return "verify-" + UUID.randomUUID() + "@example.com";
	}

}
