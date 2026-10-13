package com.sealease.backend.notification.email;

import com.sealease.backend.notification.entity.Notification;
import com.sealease.backend.notification.repository.NotificationRepository;
import com.sealease.backend.user.dto.UserAccount;
import com.sealease.backend.user.entity.UserStatus;
import com.sealease.backend.user.service.UserAccountService;
import com.sealease.backend.user.service.UserProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.UUID;

/**
 * Emails one in-app notification to its user, unless they turned email notifications off or their
 * account is no longer active. A failure to send propagates, so the event is retried.
 */
public class NotificationEmailSender {

	private static final Logger log = LoggerFactory.getLogger(NotificationEmailSender.class);

	private final NotificationRepository notifications;
	private final UserAccountService accounts;
	private final UserProfileService profiles;
	private final JavaMailSender mail;
	private final NotificationEmailProperties properties;

	public NotificationEmailSender(NotificationRepository notifications, UserAccountService accounts,
			UserProfileService profiles, JavaMailSender mail, NotificationEmailProperties properties) {
		this.notifications = notifications;
		this.accounts = accounts;
		this.profiles = profiles;
		this.mail = mail;
		this.properties = properties;
	}

	/** @return {@code true} when an email was sent */
	public boolean send(UUID notificationId) {
		Notification n = notifications.findById(notificationId).orElse(null);
		if (n == null) {
			log.warn("Notification {} not found; no email sent", notificationId);
			return false;
		}
		UserAccount user = accounts.getAccount(n.getUserId());
		if (user.status() != UserStatus.ACTIVE || !profiles.wantsEmailNotifications(user.id())) {
			return false;
		}
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(properties.from());
		message.setTo(user.email());
		message.setSubject(n.getTitle());
		message.setText(text(user, n));
		mail.send(message);
		log.debug("Emailed notification {} ({}) to user {}", n.getId(), n.getType(), user.id());
		return true;
	}

	private String text(UserAccount user, Notification n) {
		String appUrl = properties.appUrl().replaceAll("/+$", "");
		StringBuilder text = new StringBuilder();
		text.append(user.firstName() == null || user.firstName().isBlank() ? "Hello," : "Hello " + user.firstName() + ",")
			.append("\n\n").append(n.getTitle()).append('\n');
		if (n.getBody() != null && !n.getBody().isBlank()) {
			text.append('\n').append(n.getBody()).append('\n');
		}
		if (n.getLink() != null && !n.getLink().isBlank()) {
			text.append("\nView it here: ").append(appUrl).append(n.getLink()).append('\n');
		}
		text.append("\n-- \nSeaLease\n")
			.append("You receive these emails because email notifications are on. Turn them off in Settings: ")
			.append(appUrl).append("/settings\n");
		return text.toString();
	}

}
