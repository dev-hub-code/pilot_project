package com.sealease.backend.common.mail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class SendGridMailSenderTest {

	private MockRestServiceServer server;
	private SendGridMailSender sender;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		sender = new SendGridMailSender(
				new SendGridProperties("SG.test-key", URI.create("https://api.sendgrid.test"), Duration.ofSeconds(5)),
				builder);
	}

	private static BrandedEmail email() {
		return BrandedEmail.builder("123456 is your SeaLease verification code", "https://www.sealease.in")
			.greeting("Asha")
			.code("123456")
			.button("Confirm my email", "https://www.sealease.in/verify?token=abc")
			.build();
	}

	@Test
	void sendsTheBrandedEmailThroughTheWebApi() {
		server.expect(requestTo("https://api.sendgrid.test/v3/mail/send"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header("Authorization", "Bearer SG.test-key"))
			.andExpect(jsonPath("$.personalizations[0].to[0].email").value("asha@example.com"))
			.andExpect(jsonPath("$.from.email").value("no-reply@sealease.in"))
			.andExpect(jsonPath("$.from.name").value("SeaLease"))
			.andExpect(jsonPath("$.subject").value("123456 is your SeaLease verification code"))
			.andExpect(jsonPath("$.content[0].type").value("text/plain"))
			.andExpect(jsonPath("$.content[0].value").value(containsString("123456")))
			.andExpect(jsonPath("$.content[1].type").value("text/html"))
			.andExpect(jsonPath("$.content[1].value").value(containsString("cid:sealease-logo")))
			.andExpect(jsonPath("$.attachments[0].content_id").value("sealease-logo"))
			.andExpect(jsonPath("$.attachments[0].disposition").value("inline"))
			.andExpect(jsonPath("$.attachments[0].type").value("image/png"))
			.andExpect(jsonPath("$.attachments[0].content").value(startsWith("iVBOR")))
			.andExpect(jsonPath("$.tracking_settings.click_tracking.enable").value(false))
			.andRespond(withStatus(HttpStatus.ACCEPTED));

		sender.send(email().message("SeaLease <no-reply@sealease.in>", "asha@example.com"));

		server.verify();
	}

	@Test
	void reportsARefusalAsAMailException() {
		server.expect(requestTo("https://api.sendgrid.test/v3/mail/send"))
			.andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
				.body("{\"errors\":[{\"message\":\"The from address does not match a verified Sender Identity.\"}]}"));

		assertThatThrownBy(() -> sender.send(email().message("SeaLease <no-reply@sealease.in>", "asha@example.com")))
			.isInstanceOf(MailSendException.class)
			.hasMessageContaining("verified Sender Identity");
	}

}
