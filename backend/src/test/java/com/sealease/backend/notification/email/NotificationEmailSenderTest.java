package com.sealease.backend.notification.email;

import com.sealease.backend.notification.entity.Notification;
import com.sealease.backend.notification.repository.NotificationRepository;
import com.sealease.backend.user.dto.UserAccount;
import com.sealease.backend.user.entity.UserStatus;
import com.sealease.backend.user.service.UserAccountService;
import com.sealease.backend.user.service.UserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationEmailSenderTest {

	private final NotificationRepository notifications = mock(NotificationRepository.class);
	private final UserAccountService accounts = mock(UserAccountService.class);
	private final UserProfileService profiles = mock(UserProfileService.class);
	private final JavaMailSender mail = mock(JavaMailSender.class);
	private final NotificationEmailSender sender = new NotificationEmailSender(notifications, accounts, profiles, mail,
			new NotificationEmailProperties(true, "SeaLease <no-reply@sealease.test>", "https://app.sealease.test/"));

	private final UUID userId = UUID.randomUUID();
	private Notification notification;

	@BeforeEach
	void setUp() {
		notification = new Notification(userId, "SUPPORT_REPLY", "Your ticket was answered",
				"Support replied to ticket #42.", "/support/42", Instant.parse("2026-10-12T10:00:00Z"));
		ReflectionTestUtils.setField(notification, "id", UUID.randomUUID());
		when(notifications.findById(notification.getId())).thenReturn(Optional.of(notification));
		when(accounts.getAccount(userId)).thenReturn(account(UserStatus.ACTIVE));
		when(profiles.wantsEmailNotifications(userId)).thenReturn(true);
	}

	@Test
	void emailsTheNotificationWithAnAbsoluteLink() {
		assertThat(sender.send(notification.getId())).isTrue();

		ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(mail).send(sent.capture());
		SimpleMailMessage message = sent.getValue();
		assertThat(message.getTo()).containsExactly("asha@example.com");
		assertThat(message.getFrom()).isEqualTo("SeaLease <no-reply@sealease.test>");
		assertThat(message.getSubject()).isEqualTo("Your ticket was answered");
		assertThat(message.getText())
			.startsWith("Hello Asha,")
			.contains("Support replied to ticket #42.")
			.contains("View it here: https://app.sealease.test/support/42")
			.contains("https://app.sealease.test/settings");
	}

	@Test
	void respectsTheUsersEmailPreference() {
		when(profiles.wantsEmailNotifications(userId)).thenReturn(false);

		assertThat(sender.send(notification.getId())).isFalse();
		verify(mail, never()).send(any(SimpleMailMessage.class));
	}

	@Test
	void skipsInactiveAccounts() {
		when(accounts.getAccount(userId)).thenReturn(account(UserStatus.SUSPENDED));

		assertThat(sender.send(notification.getId())).isFalse();
		verify(mail, never()).send(any(SimpleMailMessage.class));
	}

	private UserAccount account(UserStatus status) {
		return new UserAccount(userId, "SL03500", "asha@example.com", "Asha", "Rao", status, false);
	}

}
