package com.sealease.backend.auth.service;

import com.sealease.backend.auth.config.AuthProperties;
import com.sealease.backend.common.mail.BrandedEmail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
		String resetPage = appUrl + "/reset-password?email=" + URLEncoder.encode(email, StandardCharsets.UTF_8);
		BrandedEmail message = BrandedEmail.builder(code + " is your SeaLease password reset code", appUrl)
			.preheader("Your password reset code is " + code + ". It works for " + minutes + " minutes.")
			.greeting(firstName)
			.paragraph("Someone asked to reset the password of your SeaLease account. Enter this code to choose a new"
					+ " password:")
			.code(code)
			.button("Set a new password", resetPage)
			.paragraph("The code works for " + minutes + " minutes.")
			.note("Never share this code: SeaLease will never ask you for it. If you did not ask to reset your"
					+ " password, ignore this email: your password stays as it is.")
			.build();
		try {
			mail.send(message.message(properties.emailVerification().from(), email));
			log.debug("Sent a password reset code to user {}", userId);
		}
		catch (MailException ex) {
			log.warn("Could not send a password reset code to user {}", userId, ex);
		}
	}

}
