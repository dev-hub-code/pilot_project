package com.sealease.backend.notification.service;

import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.kafka.KafkaTopics;
import com.sealease.backend.notification.dto.NotificationResponse;
import com.sealease.backend.notification.entity.Notification;
import com.sealease.backend.notification.repository.NotificationRepository;
import com.sealease.backend.outbox.DomainEvent;
import com.sealease.backend.outbox.OutboxPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * In-app notifications. Each one is also published to {@code notification.created}, so an email or
 * SMS sender (honouring the user's notification preferences) can be added without touching callers.
 */
@Service
public class NotificationService {

	private final NotificationRepository notifications;
	private final OutboxPublisher outbox;
	private final Clock clock;

	public NotificationService(NotificationRepository notifications, OutboxPublisher outbox, Clock clock) {
		this.notifications = notifications;
		this.outbox = outbox;
		this.clock = clock;
	}

	/** Notifies a user, in the caller's transaction: the notification exists only if the change committed. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void notify(UUID userId, String type, String title, String body, String link) {
		Notification n = notifications.save(new Notification(userId, type, truncate(title, 200), truncate(body, 1000),
				link, clock.instant()));
		Map<String, Object> payload = new HashMap<>();
		payload.put("notificationId", n.getId().toString());
		payload.put("userId", userId.toString());
		payload.put("type", type);
		payload.put("title", n.getTitle());
		if (link != null) {
			payload.put("link", link);
		}
		outbox.publish(DomainEvent.of(KafkaTopics.NOTIFICATION_CREATED, "NotificationCreated", "USER", userId, payload));
	}

	@Transactional(readOnly = true)
	public Page<NotificationResponse> mine(UUID userId, Pageable pageable) {
		return notifications.findByUserIdOrderByCreatedAtDescIdDesc(userId, pageable).map(NotificationResponse::from);
	}

	@Transactional(readOnly = true)
	public long unread(UUID userId) {
		return notifications.countByUserIdAndReadAtIsNull(userId);
	}

	@Transactional
	public NotificationResponse markRead(UUID userId, UUID notificationId) {
		Notification n = notifications.findById(notificationId)
			.filter(x -> x.getUserId().equals(userId))
			.orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId));
		n.markRead(clock.instant());
		return NotificationResponse.from(n);
	}

	@Transactional
	public int markAllRead(UUID userId) {
		return notifications.markAllRead(userId, clock.instant());
	}

	private static String truncate(String value, int max) {
		return value == null || value.length() <= max ? value : value.substring(0, max - 1) + "…";
	}

}
