package com.sealease.backend.notification.email;

import com.sealease.backend.kafka.KafkaTopics;
import com.sealease.backend.outbox.ProcessedEvents;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

/**
 * Emails each new notification ({@code notification.created}). The event is marked processed in the
 * same transaction, so a redelivery after a committed send does not email twice; a failed send rolls
 * back and is retried, then dead-lettered.
 */
public class NotificationEmailListener {

	static final String CONSUMER = "notification-email";

	private final NotificationEmailSender sender;
	private final ProcessedEvents processedEvents;
	private final JsonMapper jsonMapper;

	public NotificationEmailListener(NotificationEmailSender sender, ProcessedEvents processedEvents,
			JsonMapper jsonMapper) {
		this.sender = sender;
		this.processedEvents = processedEvents;
		this.jsonMapper = jsonMapper;
	}

	/** Starts from the newest event when first deployed, so existing notifications are not emailed as a backlog. */
	@KafkaListener(topics = KafkaTopics.NOTIFICATION_CREATED, groupId = CONSUMER,
			properties = "auto.offset.reset=latest")
	@Transactional
	public void onNotificationCreated(String message) {
		JsonNode envelope = jsonMapper.readTree(message);
		UUID eventId = UUID.fromString(envelope.path("eventId").asString());
		if (!processedEvents.markProcessed(CONSUMER, eventId)) {
			return;
		}
		sender.send(UUID.fromString(envelope.path("data").path("notificationId").asString()));
	}

}
