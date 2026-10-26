package com.sealease.backend.agreement.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A review task with the agreement's wording (lease dates filled in once accepted) and its comments, oldest first. */
public record AgreementReviewDetail(AgreementReviewTaskResponse task, String wording, List<Comment> comments) {

	public record Comment(UUID id, UUID authorId, String authorName, String body, Instant createdAt) {
	}

}
