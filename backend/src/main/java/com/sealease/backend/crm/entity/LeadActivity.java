package com.sealease.backend.crm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/** One entry in a lead's history. Append-only. */
@Entity
@Immutable
@Table(name = "lead_activities")
public class LeadActivity {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.TIME)
	private UUID id;

	@Column(name = "lead_id", nullable = false)
	private UUID leadId;

	@Enumerated(EnumType.STRING)
	@Column(name = "activity_type", nullable = false, length = 20)
	private ActivityType type;

	@Column(name = "body", nullable = false, length = 4000)
	private String body;

	/** {@code null} for platform entries. */
	@Column(name = "actor_id")
	private UUID actorId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected LeadActivity() {
	}

	public LeadActivity(UUID leadId, ActivityType type, String body, UUID actorId, Instant createdAt) {
		this.leadId = leadId;
		this.type = type;
		this.body = body.length() > 4000 ? body.substring(0, 4000) : body;
		this.actorId = actorId;
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public UUID getLeadId() {
		return leadId;
	}

	public ActivityType getType() {
		return type;
	}

	public String getBody() {
		return body;
	}

	public UUID getActorId() {
		return actorId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
