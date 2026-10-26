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
import java.time.Duration;
import java.util.UUID;

/**
 * Emails the link that confirms a new account's address. Sending is best effort: a failure is logged
 * and the user can ask for another email from the sign-in or verification page.
 */
@Component
class VerificationEmailSender {

	private static final Logger log = LoggerFactory.getLogger(VerificationEmailSender.class);

	private final JavaMailSender mail;
	private final AuthProperties.EmailVerification properties;

	VerificationEmailSender(JavaMailSender mail, AuthProperties properties) {
		this.mail = mail;
		this.properties = properties.emailVerification();
	}

	void send(UUID userId, String email, String firstName, String token, String code) {
		String link = properties.appUrl().replaceAll("/+$", "") + "/verify-email?token="
				+ URLEncoder.encode(token, StandardCharsets.US_ASCII);
		String validity = describe(properties.linkTtl());
		BrandedEmail message = BrandedEmail.builder(code + " is your SeaLease verification code", properties.appUrl())
			.preheader("Your verification code is " + code + ". It works for " + validity + ".")
			.greeting(firstName)
			.paragraph("Thanks for signing up to SeaLease. Confirm your email address to activate your account: "
					+ "enter this code on the verification page.")
			.code(code)
			.paragraph("Or confirm with one click:")
			.button("Confirm my email", link)
			.paragraph("The code and the button work for " + validity + ". If they have expired, sign in and ask for a"
					+ " new email.")
			.note("Never share this code: SeaLease will never ask you for it. If you did not create a SeaLease"
					+ " account, ignore this email.")
			.build();
		try {
			mail.send(message.message(properties.from(), email));
			log.debug("Sent the email verification link to user {}", userId);
		}
		catch (MailException ex) {
			log.warn("Could not send the email verification link to user {}", userId, ex);
		}
	}

	private static String describe(Duration ttl) {
		long hours = ttl.toHours();
		if (hours >= 24 && hours % 24 == 0) {
			return hours == 24 ? "24 hours" : (hours / 24) + " days";
		}
		return hours >= 1 ? hours + (hours == 1 ? " hour" : " hours") : ttl.toMinutes() + " minutes";
	}

}
