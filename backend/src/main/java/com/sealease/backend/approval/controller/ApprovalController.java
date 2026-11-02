package com.sealease.backend.approval.controller;

import com.sealease.backend.approval.dto.ApprovalDtos.ApprovalResponse;
import com.sealease.backend.approval.dto.ApprovalDtos.CreateApprovalRequest;
import com.sealease.backend.approval.dto.ApprovalDtos.RejectApprovalRequest;
import com.sealease.backend.approval.entity.ApprovalStatus;
import com.sealease.backend.approval.service.ApprovalService;
import com.sealease.backend.common.api.PageResponse;
import com.sealease.backend.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/approvals")
public class ApprovalController {

	private final ApprovalService approvals;

	public ApprovalController(ApprovalService approvals) {
		this.approvals = approvals;
	}

	@GetMapping
	@PreAuthorize("hasAnyAuthority('APPROVAL_VIEW', 'APPROVAL_DECIDE')")
	public PageResponse<ApprovalResponse> list(@RequestParam(required = false) ApprovalStatus status,
			@PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return PageResponse.from(approvals.list(status, pageable));
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAnyAuthority('APPROVAL_VIEW', 'APPROVAL_DECIDE')")
	public ApprovalResponse get(@PathVariable UUID id) {
		return approvals.get(id);
	}

	/** The service checks the permission of the operation being requested. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAuthority('FINANCE_ADJUST')")
	public ApprovalResponse create(AuthenticatedUser maker, @Valid @RequestBody CreateApprovalRequest request) {
		return approvals.create(maker, request);
	}

	@PostMapping("/{id}/approve")
	@PreAuthorize("hasAuthority('APPROVAL_DECIDE')")
	public ApprovalResponse approve(AuthenticatedUser checker, @PathVariable UUID id) {
		return approvals.approve(checker, id);
	}

	@PostMapping("/{id}/reject")
	@PreAuthorize("hasAuthority('APPROVAL_DECIDE')")
	public ApprovalResponse reject(AuthenticatedUser checker, @PathVariable UUID id,
			@Valid @RequestBody RejectApprovalRequest request) {
		return approvals.reject(checker, id, request.reason());
	}

}
