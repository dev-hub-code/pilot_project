package com.sealease.backend.auth.service;

import com.sealease.backend.auth.config.AuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Emails a "forgot password" code. Best effort: a failure is logged, and the user can ask for another code. */
@Component
class PasswordResetEmailSender {

	private static final Logger log = LoggerFactory.getLogger(PasswordResetEmailSender.class);

	private final JavaMailSender mail;
	private final AuthProperties properties;

	PasswordResetEmailSender(JavaMailSender mail, AuthProperties properties) {
		this.mail = mail;
		this.properties = properties;
	}

	void send(UUID userId, String email, String firstName, String code) {
		String appUrl = properties.emailVerification().appUrl().replaceAll("/+$", "");
		long minutes = properties.passwordReset().codeTtl().toMinutes();
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(properties.emailVerification().from());
		message.setTo(email);
		message.setSubject(code + " is your SeaLease password reset code");
		message.setText((firstName == null || firstName.isBlank() ? "Hello," : "Hello " + firstName + ",")
				+ "\n\nSomeone asked to reset the password of your SeaLease account. Enter this code to choose a new password:\n\n"
				+ "    " + code + "\n\n"
				+ "The code works for " + minutes + " minutes: " + appUrl + "/reset-password?email=" + java.net.URLEncoder.encode(
						email, java.nio.charset.StandardCharsets.UTF_8) + "\n\n"
				+ "Never share this code. SeaLease will never ask you for it.\n"
				+ "If you did not ask to reset your password, ignore this email: your password stays as it is.\n\n-- \nSeaLease\n");
		try {
			mail.send(message);
			log.debug("Sent a password reset code to user {}", userId);
		}
		catch (MailException ex) {
			log.warn("Could not send a password reset code to user {}", userId, ex);
		}
	}

}
