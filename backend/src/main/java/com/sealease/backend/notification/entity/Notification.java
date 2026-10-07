package com.sealease.backend.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/** Something a user should know about, shown in their notification centre. Only {@code readAt} ever changes. */
@Entity
@Table(name = "notifications")
public class Notification {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.TIME)
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "type", nullable = false, updatable = false, length = 40)
	private String type;

	@Column(name = "title", nullable = false, updatable = false, length = 200)
	private String title;

	@Column(name = "body", updatable = false, length = 1000)
	private String body;

	/** A path in the web app, e.g. {@code /support/{id}}. */
	@Column(name = "link", updatable = false, length = 300)
	private String link;

	@Column(name = "read_at")
	private Instant readAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Notification() {
	}

	public Notification(UUID userId, String type, String title, String body, String link, Instant createdAt) {
		this.userId = userId;
		this.type = type;
		this.title = title;
		this.body = body;
		this.link = link;
		this.createdAt = createdAt;
	}

	public void markRead(Instant now) {
		if (readAt == null) {
			readAt = now;
		}
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public String getType() {
		return type;
	}

	public String getTitle() {
		return title;
	}

	public String getBody() {
		return body;
	}

	public String getLink() {
		return link;
	}

	public Instant getReadAt() {
		return readAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
