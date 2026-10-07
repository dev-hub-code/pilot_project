package com.sealease.backend.notification.dto;

import com.sealease.backend.notification.entity.Notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(UUID id, String type, String title, String body, String link, boolean read,
		Instant createdAt) {

	public static NotificationResponse from(Notification n) {
		return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getLink(),
				n.getReadAt() != null, n.getCreatedAt());
	}

}
