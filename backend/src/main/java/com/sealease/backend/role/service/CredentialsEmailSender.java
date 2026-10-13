package com.sealease.backend.role.service;

import com.sealease.backend.role.dto.StaffCredentialsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

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
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(credentials.email());
		message.setSubject(reset ? "Your new SeaLease temporary password" : "Your SeaLease employee account");
		String name = credentials.firstName() == null || credentials.firstName().isBlank() ? "" : " " + credentials.firstName();
		message.setText("Hello" + name + ",\n\n"
				+ (reset ? "An administrator issued you a new temporary password for SeaLease.\n\n"
						: "An administrator created a SeaLease employee account for you"
								+ (credentials.roles().isEmpty() ? "" : " (" + String.join(", ", credentials.roles()) + ")")
								+ ".\n\n")
				+ "Sign in at: " + appUrl + "/login\n"
				+ "Email: " + credentials.email() + "\n"
				+ "Temporary password: " + credentials.temporaryPassword() + "\n\n"
				+ "You will be asked to choose your own password when you sign in. The temporary password stops working on "
				+ EXPIRY.format(credentials.expiresAt()) + "; after that, ask an administrator for a new one.\n\n"
				+ "Never share this password. SeaLease will never ask you for it.\n"
				+ "If you were not expecting this email, tell your administrator.\n\n-- \nSeaLease\n");
		try {
			mail.send(message);
			log.info("Emailed {} credentials to user {}", reset ? "new temporary" : "new account", credentials.userId());
			return true;
		}
		catch (MailException ex) {
			log.warn("Could not email credentials to user {}", credentials.userId(), ex);
			return false;
		}
	}

}
