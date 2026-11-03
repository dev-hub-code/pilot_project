package com.sealease.backend.auth.service;

import com.sealease.backend.auth.service.InvestorInvitationService.IssuedInvitation;
import com.sealease.backend.common.mail.BrandedEmail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Emails a client the link to open the network investor account an employee invited them to. Best effort: a failure
 * is logged and reported back, so the employee can send the invitation again.
 */
@Component
public class InvitationEmailSender {

	private static final Logger log = LoggerFactory.getLogger(InvitationEmailSender.class);
	private static final DateTimeFormatter EXPIRY = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)
		.withZone(ZoneId.of("Asia/Kolkata"));

	private final JavaMailSender mail;
	private final String from;
	private final String appUrl;

	public InvitationEmailSender(JavaMailSender mail, @Value("${app.notifications.email.from}") String from,
			@Value("${app.notifications.email.app-url}") String appUrl) {
		this.mail = mail;
		this.from = from;
		this.appUrl = appUrl.replaceAll("/+$", "");
	}

	/** @return whether the email was handed to the mail server */
	public boolean send(IssuedInvitation invitation) {
		String link = appUrl + "/invitation?token=" + URLEncoder.encode(invitation.token(), StandardCharsets.US_ASCII);
		BrandedEmail message = BrandedEmail.builder("You're invited to open a SeaLease investor account", appUrl)
			.preheader(invitation.invitedBy() + " at SeaLease invited you to open an investor account.")
			.greeting(invitation.firstName())
			.paragraph(invitation.invitedBy() + " at SeaLease invited you to open a network investor account. As a"
					+ " network investor you earn returns every month on your investments, and referral income on four"
					+ " levels when people you introduce invest.")
			.button("Open my account", link)
			.paragraph("You will choose your own password. The link works once, until "
					+ EXPIRY.format(invitation.expiresAt()) + "; after that, ask SeaLease for a new invitation.")
			.note("If you were not expecting this invitation, ignore this email: no account is opened unless you follow"
					+ " the link.")
			.build();
		try {
			mail.send(message.message(from, invitation.email()));
			log.info("Emailed investor invitation {}", invitation.id());
			return true;
		}
		catch (MailException ex) {
			log.warn("Could not email investor invitation {}", invitation.id(), ex);
			return false;
		}
	}

}
