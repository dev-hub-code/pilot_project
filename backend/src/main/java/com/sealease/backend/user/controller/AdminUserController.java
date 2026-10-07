package com.sealease.backend.user.controller;

import com.sealease.backend.common.api.PageResponse;
import com.sealease.backend.common.api.ReasonRequest;
import com.sealease.backend.security.AuthenticatedUser;
import com.sealease.backend.user.dto.AdminUserDetail;
import com.sealease.backend.user.dto.AdminUserSummary;
import com.sealease.backend.user.dto.ClassifyInvestorRequest;
import com.sealease.backend.user.dto.UserSearchCriteria;
import com.sealease.backend.user.entity.InvestorType;
import com.sealease.backend.user.entity.KycStatus;
import com.sealease.backend.user.entity.UserStatus;
import com.sealease.backend.user.service.UserAdministrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

	private final UserAdministrationService administration;

	public AdminUserController(UserAdministrationService administration) {
		this.administration = administration;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('USER_VIEW')")
	public PageResponse<AdminUserSummary> search(
			@RequestParam(required = false) @Size(max = 100) String q,
			@RequestParam(required = false) UserStatus status,
			@RequestParam(required = false) KycStatus kycStatus,
			@RequestParam(required = false) InvestorType investorType,
			@PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return PageResponse.from(administration.search(new UserSearchCriteria(q, status, kycStatus, investorType), pageable));
	}

	@GetMapping("/{userId}")
	@PreAuthorize("hasAuthority('USER_VIEW')")
	public AdminUserDetail detail(@PathVariable UUID userId) {
		return administration.detail(userId);
	}

	@PostMapping("/{userId}/suspend")
	@PreAuthorize("hasAuthority('USER_SUSPEND')")
	public AdminUserDetail suspend(AuthenticatedUser actor, @PathVariable UUID userId,
			@Valid @RequestBody ReasonRequest request) {
		return administration.suspend(actor.userId(), userId, request.reason());
	}

	@PostMapping("/{userId}/reactivate")
	@PreAuthorize("hasAuthority('USER_SUSPEND')")
	public AdminUserDetail reactivate(AuthenticatedUser actor, @PathVariable UUID userId,
			@Valid @RequestBody ReasonRequest request) {
		return administration.reactivate(actor.userId(), userId, request.reason());
	}

	@PostMapping("/{userId}/classification")
	@PreAuthorize("hasAuthority('INVESTOR_CLASSIFY')")
	public AdminUserDetail classify(AuthenticatedUser actor, @PathVariable UUID userId,
			@Valid @RequestBody ClassifyInvestorRequest request) {
		return administration.classify(actor.userId(), userId, request.investorType(), request.reason());
	}

}
