package com.sealease.backend.common.mail;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.InputStream;

/**
 * Sends through the primary SMTP server and, when it cannot deliver a message (server down, wrong credentials, or the
 * message refused, as Amazon SES does in its sandbox for unverified recipients), sends that message through the
 * fallback server instead. Only the messages the primary did not send are retried, so nothing is delivered twice.
 */
public class FailoverMailSender implements JavaMailSender {

	private static final Logger log = LoggerFactory.getLogger(FailoverMailSender.class);

	private final JavaMailSender primary;
	private final JavaMailSender fallback;

	public FailoverMailSender(JavaMailSender primary, JavaMailSender fallback) {
		this.primary = primary;
		this.fallback = fallback;
	}

	@Override
	public MimeMessage createMimeMessage() {
		return primary.createMimeMessage();
	}

	@Override
	public MimeMessage createMimeMessage(InputStream contentStream) throws MailException {
		return primary.createMimeMessage(contentStream);
	}

	@Override
	public void send(MimeMessage... messages) throws MailException {
		try {
			primary.send(messages);
		}
		catch (MailSendException ex) {
			MimeMessage[] failed = ex.getFailedMessages()
				.keySet()
				.stream()
				.filter(MimeMessage.class::isInstance)
				.map(MimeMessage.class::cast)
				.toArray(MimeMessage[]::new);
			MimeMessage[] retry = failed.length > 0 ? failed : messages;
			warn(ex, retry.length);
			fallback.send(retry);
		}
		catch (MailAuthenticationException ex) {
			warn(ex, messages.length);
			fallback.send(messages);
		}
	}

	@Override
	public void send(SimpleMailMessage... messages) throws MailException {
		try {
			primary.send(messages);
		}
		catch (MailSendException ex) {
			SimpleMailMessage[] failed = ex.getFailedMessages()
				.keySet()
				.stream()
				.filter(SimpleMailMessage.class::isInstance)
				.map(SimpleMailMessage.class::cast)
				.toArray(SimpleMailMessage[]::new);
			SimpleMailMessage[] retry = failed.length > 0 ? failed : messages;
			warn(ex, retry.length);
			fallback.send(retry);
		}
		catch (MailAuthenticationException ex) {
			warn(ex, messages.length);
			fallback.send(messages);
		}
	}

	// The exception's text can hold recipient addresses, so only its type is logged.
	private static void warn(MailException ex, int count) {
		log.warn("Primary SMTP server failed ({}); sending {} email(s) through the fallback server",
				ex.getClass().getSimpleName(), count);
	}

}
