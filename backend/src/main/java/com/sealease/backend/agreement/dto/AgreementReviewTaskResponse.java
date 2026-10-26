package com.sealease.backend.agreement.dto;

import com.sealease.backend.agreement.entity.AgreementReviewTask;
import com.sealease.backend.common.api.Assignee;

import java.time.Instant;
import java.util.UUID;

/**
 * An agreement review task, with the agreement it is about and its investor.
 *
 * @param completedBy who marked it done, with when
 */
public record AgreementReviewTaskResponse(UUID id, AgreementReviewTask.Status status, LeaseAgreementResponse agreement,
		Investor investor, Assignee assignee, Assignee completedBy, long comments, Instant createdAt) {

	public record Investor(UUID id, String userCode, String name, String email) {
	}

}
