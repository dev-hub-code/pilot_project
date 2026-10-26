package com.sealease.backend.agreement.controller;

import com.sealease.backend.agreement.dto.AgreementReviewCommentRequest;
import com.sealease.backend.agreement.dto.AgreementReviewCompleteRequest;
import com.sealease.backend.agreement.dto.AgreementReviewDetail;
import com.sealease.backend.agreement.dto.AgreementReviewTaskResponse;
import com.sealease.backend.agreement.entity.AgreementReviewTask;
import com.sealease.backend.agreement.service.AgreementReviewService;
import com.sealease.backend.common.api.PageResponse;
import com.sealease.backend.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Review tasks for issued lease agreements, one per agreement, for reviewers (report viewers, super admins). */
@RestController
@RequestMapping("/api/v1/admin/agreement-reviews")
@PreAuthorize("hasAuthority('AGREEMENT_REVIEW')")
public class AdminAgreementReviewController {

	private final AgreementReviewService reviews;

	public AdminAgreementReviewController(AgreementReviewService reviews) {
		this.reviews = reviews;
	}

	/** {@code mine} lists only the tasks assigned to the caller. */
	@GetMapping
	public PageResponse<AgreementReviewTaskResponse> search(AuthenticatedUser viewer,
			@RequestParam(defaultValue = "OPEN") AgreementReviewTask.Status status,
			@RequestParam(defaultValue = "false") boolean mine, @PageableDefault(size = 50) Pageable pageable) {
		return PageResponse.from(reviews.search(viewer.userId(), status, mine, pageable));
	}

	@GetMapping("/{taskId}")
	public AgreementReviewDetail detail(@PathVariable UUID taskId) {
		return reviews.detail(taskId);
	}

	@GetMapping("/{taskId}/pdf")
	public ResponseEntity<byte[]> pdf(@PathVariable UUID taskId) {
		return reviews.pdf(taskId).toResponse();
	}

	/** Takes the task: only the reviewer it is assigned to may complete it. */
	@PostMapping("/{taskId}/assign")
	public AgreementReviewDetail assign(AuthenticatedUser reviewer, @PathVariable UUID taskId) {
		return reviews.assign(reviewer.userId(), taskId);
	}

	@PostMapping("/{taskId}/unassign")
	public AgreementReviewDetail unassign(AuthenticatedUser reviewer, @PathVariable UUID taskId) {
		return reviews.unassign(reviewer.userId(), taskId);
	}

	@PostMapping("/{taskId}/comments")
	public AgreementReviewDetail comment(AuthenticatedUser author, @PathVariable UUID taskId,
			@Valid @RequestBody AgreementReviewCommentRequest request) {
		return reviews.comment(author.userId(), taskId, request.body());
	}

	@PostMapping("/{taskId}/complete")
	public AgreementReviewDetail complete(AuthenticatedUser reviewer, @PathVariable UUID taskId,
			@Valid @RequestBody(required = false) AgreementReviewCompleteRequest request) {
		return reviews.complete(reviewer.userId(), taskId, request == null ? null : request.comment());
	}

}
