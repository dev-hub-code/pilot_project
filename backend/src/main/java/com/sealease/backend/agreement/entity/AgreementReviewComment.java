package com.sealease.backend.agreement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/** A reviewer's comment on an agreement review task. Immutable. */
@Entity
@Immutable
@Table(name = "agreement_review_comments")
public class AgreementReviewComment {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.TIME)
	private UUID id;

	@Column(name = "task_id", nullable = false)
	private UUID taskId;

	@Column(name = "author_id", nullable = false)
	private UUID authorId;

	@Column(name = "body", nullable = false, length = 2000)
	private String body;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected AgreementReviewComment() {
	}

	public AgreementReviewComment(UUID taskId, UUID authorId, String body, Instant createdAt) {
		this.taskId = taskId;
		this.authorId = authorId;
		this.body = body;
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public UUID getAuthorId() {
		return authorId;
	}

	public String getBody() {
		return body;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
