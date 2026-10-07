package com.sealease.backend.kyc.controller;

import com.sealease.backend.common.api.PageResponse;
import com.sealease.backend.common.api.ReasonRequest;
import com.sealease.backend.document.web.DocumentResponses;
import com.sealease.backend.kyc.dto.KycReviewDetail;
import com.sealease.backend.kyc.dto.KycSubmissionResponse;
import com.sealease.backend.kyc.entity.KycSubmissionStatus;
import com.sealease.backend.kyc.service.KycService;
import com.sealease.backend.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@PreAuthorize("hasAuthority('KYC_REVIEW')")
public class AdminKycController {

	private final KycService kyc;

	public AdminKycController(KycService kyc) {
		this.kyc = kyc;
	}

	/** Review queue, oldest first so nothing waits indefinitely. */
	@GetMapping("/api/v1/admin/kyc")
	public PageResponse<KycSubmissionResponse> queue(
			@RequestParam(defaultValue = "PENDING") KycSubmissionStatus status,
			@PageableDefault(sort = "submittedAt", direction = Sort.Direction.ASC) Pageable pageable) {
		return PageResponse.from(kyc.queue(status, pageable));
	}

	@GetMapping("/api/v1/admin/kyc/{submissionId}")
	public KycReviewDetail detail(@PathVariable UUID submissionId) {
		return kyc.detail(submissionId);
	}

	@GetMapping("/api/v1/admin/users/{userId}/kyc")
	public List<KycSubmissionResponse> history(@PathVariable UUID userId) {
		return kyc.historyFor(userId);
	}

	@GetMapping("/api/v1/admin/kyc/{submissionId}/documents/{documentId}")
	public ResponseEntity<byte[]> document(AuthenticatedUser reviewer, @PathVariable UUID submissionId,
			@PathVariable UUID documentId) {
		return DocumentResponses.inline(kyc.viewDocument(reviewer.userId(), submissionId, documentId));
	}

	@PostMapping("/api/v1/admin/kyc/{submissionId}/approve")
	public KycSubmissionResponse approve(AuthenticatedUser reviewer, @PathVariable UUID submissionId) {
		return kyc.approve(reviewer.userId(), submissionId);
	}

	@PostMapping("/api/v1/admin/kyc/{submissionId}/reject")
	public KycSubmissionResponse reject(AuthenticatedUser reviewer, @PathVariable UUID submissionId,
			@Valid @RequestBody ReasonRequest request) {
		return kyc.reject(reviewer.userId(), submissionId, request.reason());
	}

}
