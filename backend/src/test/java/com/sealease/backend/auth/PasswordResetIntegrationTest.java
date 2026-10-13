package com.sealease.backend.auth;

import com.jayway.jsonpath.JsonPath;
import com.sealease.backend.support.IntegrationTest;
import com.sealease.backend.support.TestApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Forgot password: a code emailed to the account's address sets a new password and ends every session. */
@IntegrationTest
class PasswordResetIntegrationTest {

	private static final Pattern CODE = Pattern.compile("^\\s+(\\d{6})$", Pattern.MULTILINE);
	private static final String NEW_PASSWORD = "Fresh-Start-42";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoBean
	private JavaMailSender mail;

	private TestApi api;

	@BeforeEach
	void setUp() {
		api = new TestApi(mvc, jdbc);
	}

	@Test
	void theEmailedCodeSetsANewPasswordAndSignsOutEverywhere() throws Exception {
		TestApi.Account investor = api.register();

		request(investor.email()).andExpect(status().isAccepted());
		String code = lastCodeSentTo(investor.email());

		// The new password must follow the password rules; a weak one leaves the code usable.
		reset(investor.email(), code, "weakpass").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("newPassword"));
		reset(investor.email(), code, NEW_PASSWORD).andExpect(status().isNoContent());

		mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, investor.bearer()))
			.andExpect(status().isUnauthorized());
		assertThat(api.loginResult(investor.email(), TestApi.PASSWORD).getResponse().getStatus()).isEqualTo(401);
		assertThat(api.loginResult(investor.email(), NEW_PASSWORD).getResponse().getStatus()).isEqualTo(200);
		assertThat(api.auditCount("PASSWORD_RESET", investor.id())).isEqualTo(1);

		// A code works once.
		reset(investor.email(), code, "Another-Pass-77").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_RESET_CODE"));
	}

	@Test
	void aResetLiftsALockoutAndReplacesATemporaryPassword() throws Exception {
		TestApi.Account investor = api.register();
		for (int i = 0; i < 5; i++) {
			api.loginResult(investor.email(), "Wrong-Pass-" + i);
		}
		assertThat(api.loginResult(investor.email(), TestApi.PASSWORD).getResponse().getStatus()).isEqualTo(423);

		request(investor.email()).andExpect(status().isAccepted());
		reset(investor.email(), lastCodeSentTo(investor.email()), NEW_PASSWORD).andExpect(status().isNoContent());
		String body = api.loginResult(investor.email(), NEW_PASSWORD).getResponse().getContentAsString();
		assertThat((String) JsonPath.read(body, "$.accessToken")).isNotBlank();
	}

	@Test
	void tooManyWrongCodesEndTheCodeAndANewRequestRetiresTheOldOne() throws Exception {
		TestApi.Account investor = api.register();
		request(investor.email()).andExpect(status().isAccepted());
		String first = lastCodeSentTo(investor.email());
		request(investor.email()).andExpect(status().isAccepted());
		String second = lastCodeSentTo(investor.email());
		// The earlier code no longer works; trying it is a wrong guess at the current one.
		int wrongGuesses = 0;
		if (!first.equals(second)) {
			reset(investor.email(), first, NEW_PASSWORD).andExpect(status().isBadRequest());
			wrongGuesses++;
		}

		String wrong = second.equals("000000") ? "111111" : "000000";
		for (; wrongGuesses < 4; wrongGuesses++) {
			reset(investor.email(), wrong, NEW_PASSWORD).andExpect(status().isBadRequest());
		}
		reset(investor.email(), wrong, NEW_PASSWORD).andExpect(jsonPath("$.message").value(
				org.hamcrest.Matchers.containsString("Too many wrong codes")));
		reset(investor.email(), second, NEW_PASSWORD).andExpect(status().isBadRequest());
	}

	@Test
	void unknownAddressesGetTheSameAnswerAndNoEmail() throws Exception {
		clearInvocations(mail);
		request("nobody-" + System.nanoTime() + "@example.com").andExpect(status().isAccepted());
		verify(mail, never()).send(any(SimpleMailMessage.class));
		reset("nobody@example.com", "123456", NEW_PASSWORD).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_RESET_CODE"));
	}

	private ResultActions request(String email) throws Exception {
		return mvc.perform(post("/api/v1/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"%s\"}".formatted(email)));
	}

	private ResultActions reset(String email, String code, String password) throws Exception {
		return mvc.perform(post("/api/v1/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"%s\",\"code\":\"%s\",\"newPassword\":\"%s\"}".formatted(email, code, password)));
	}

	private String lastCodeSentTo(String email) {
		ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(mail, atLeastOnce()).send(sent.capture());
		SimpleMailMessage last = sent.getAllValues().stream().filter(m -> List.of(m.getTo()).contains(email)).toList().getLast();
		assertThat(last.getSubject()).endsWith(" is your SeaLease password reset code");
		Matcher code = CODE.matcher(last.getText());
		assertThat(code.find()).as("code in: %s", last.getText()).isTrue();
		return code.group(1);
	}

}
