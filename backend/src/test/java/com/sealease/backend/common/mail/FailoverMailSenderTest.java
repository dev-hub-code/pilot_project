package com.sealease.backend.common.mail;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class FailoverMailSenderTest {

	private final JavaMailSender primary = mock(JavaMailSender.class);
	private final JavaMailSender fallback = mock(JavaMailSender.class);
	private final FailoverMailSender sender = new FailoverMailSender(primary, fallback);

	private static MimeMessage message() {
		return new MimeMessage(Session.getInstance(new Properties()));
	}

	@Test
	void sendsThroughThePrimaryWhenItWorks() {
		MimeMessage message = message();

		sender.send(message);

		verify(primary).send(new MimeMessage[] { message });
		verify(fallback, never()).send(any(MimeMessage[].class));
	}

	@Test
	void resendsOnlyTheMessagesThePrimaryRefused() {
		MimeMessage sent = message();
		MimeMessage refused = message();
		doThrow(new MailSendException(Map.of(refused, new RuntimeException("554 Email address is not verified"))))
			.when(primary)
			.send(new MimeMessage[] { sent, refused });

		sender.send(sent, refused);

		verify(fallback).send(new MimeMessage[] { refused });
	}

	@Test
	void resendsEverythingWhenThePrimaryRejectsItsCredentials() {
		MimeMessage message = message();
		doThrow(new MailAuthenticationException("535 Authentication failed")).when(primary)
			.send(new MimeMessage[] { message });

		sender.send(message);

		verify(fallback).send(new MimeMessage[] { message });
	}

	@Test
	void reportsAFailureWhenBothServersFail() {
		MimeMessage message = message();
		doThrow(new MailSendException("primary down")).when(primary).send(new MimeMessage[] { message });
		doThrow(new MailSendException("fallback down")).when(fallback).send(new MimeMessage[] { message });

		assertThatThrownBy(() -> sender.send(message)).isInstanceOf(MailSendException.class)
			.hasMessageContaining("fallback down");
	}

}
