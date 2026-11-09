package com.sealease.backend.role.service;

import com.sealease.backend.common.mail.BrandedEmail;
import com.sealease.backend.role.dto.StaffCredentialsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Emails an employee the credentials an administrator issued: their sign-in email and temporary password, which
 * they must replace at first sign-in before it expires. Best effort: a failure is logged and reported back, and
 * the administrator still sees the password once to pass it on.
 */
@Component
public class CredentialsEmailSender {

	private static final Logger log = LoggerFactory.getLogger(CredentialsEmailSender.class);
	private static final DateTimeFormatter EXPIRY = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm 'IST'", Locale.ENGLISH)
		.withZone(ZoneId.of("Asia/Kolkata"));

	/** Staff sign in on their own page; investors on the public one. */
	private static final String STAFF_SIGN_IN = "/platform/login";
	private static final String INVESTOR_SIGN_IN = "/login";

	private final JavaMailSender mail;
	private final String from;
	private final String appUrl;

	public CredentialsEmailSender(JavaMailSender mail, @Value("${app.notifications.email.from}") String from,
			@Value("${app.notifications.email.app-url}") String appUrl) {
		this.mail = mail;
		this.from = from;
		this.appUrl = appUrl.replaceAll("/+$", "");
	}

	/**
	 * @param reset a new temporary password for an existing account, rather than a new account
	 * @return whether the email was handed to the mail server
	 */
	public boolean send(StaffCredentialsResponse credentials, boolean reset) {
		String paragraph = reset ? "An administrator issued you a new temporary password for SeaLease."
				: "An administrator created a SeaLease employee account for you"
						+ (credentials.roles().isEmpty() ? "" : " (" + String.join(", ", credentials.roles()) + ")") + ".";
		return send(credentials, reset ? "Your new SeaLease temporary password" : "Your SeaLease employee account",
				reset ? "An administrator issued you a new temporary password."
						: "An administrator created a SeaLease employee account for you.", paragraph, reset, STAFF_SIGN_IN);
	}

	/** A network investor's sign-in details, when an administrator makes their account. */
	public boolean sendNewInvestor(StaffCredentialsResponse credentials) {
		String text = "We created a SeaLease investor account for you.";
		return send(credentials, "Your SeaLease investor account", text, text, false, INVESTOR_SIGN_IN);
	}

	private boolean send(StaffCredentialsResponse credentials, String subject, String preheader, String paragraph,
			boolean reset, String signInPath) {
		Map<String, String> signIn = new LinkedHashMap<>();
		signIn.put("Email", credentials.email());
		signIn.put("Temporary password", credentials.temporaryPassword());
		BrandedEmail message = BrandedEmail.builder(subject, appUrl)
			.preheader(preheader)
			.greeting(credentials.firstName())
			.paragraph(paragraph)
			.details(signIn)
			.button("Sign in", appUrl + signInPath)
			.paragraph("You will be asked to choose your own password when you sign in. The temporary password stops"
					+ " working on " + EXPIRY.format(credentials.expiresAt()) + "; after that, ask an administrator for a"
					+ " new one.")
			.note("Never share this password: SeaLease will never ask you for it. If you were not expecting this email,"
					+ " tell your administrator.")
			.build();
		try {
			mail.send(message.message(from, credentials.email()));
			log.info("Emailed {} credentials to user {}", reset ? "new temporary" : "new account", credentials.userId());
			return true;
		}
		catch (MailException ex) {
			log.warn("Could not email credentials to user {}", credentials.userId(), ex);
			return false;
		}
	}

}
