package com.sealease.backend.announcement.dto;

import java.time.Instant;
import java.util.UUID;

/** An announcement as sent: to whom, how many accounts received it, and who sent it. */
public record AnnouncementResponse(
		UUID id,
		String title,
		String body,
		String link,
		AnnouncementAudience audience,
		int recipients,
		Instant createdAt,
		String createdByName) {
}
