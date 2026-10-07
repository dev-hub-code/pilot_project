package com.sealease.backend.withdrawal.controller;

import com.sealease.backend.common.api.PageResponse;
import com.sealease.backend.common.web.IdempotencyKey;
import com.sealease.backend.security.AuthenticatedUser;
import com.sealease.backend.withdrawal.dto.WithdrawalPolicy;
import com.sealease.backend.withdrawal.dto.WithdrawalRequest;
import com.sealease.backend.withdrawal.dto.WithdrawalResponse;
import com.sealease.backend.withdrawal.service.WithdrawalService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/withdrawals")
@PreAuthorize("hasAuthority('INVESTOR_PORTAL')")
public class WithdrawalController {

	private final WithdrawalService withdrawals;

	public WithdrawalController(WithdrawalService withdrawals) {
		this.withdrawals = withdrawals;
	}

	@GetMapping("/policy")
	public WithdrawalPolicy policy() {
		return withdrawals.policy();
	}

	@GetMapping
	public PageResponse<WithdrawalResponse> mine(AuthenticatedUser investor, @PageableDefault(size = 20) Pageable pageable) {
		return PageResponse.from(withdrawals.mine(investor.userId(), pageable));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public WithdrawalResponse request(AuthenticatedUser investor, @RequestHeader(IdempotencyKey.HEADER) String idempotencyKey,
			@Valid @RequestBody WithdrawalRequest request) {
		return withdrawals.request(investor.userId(), request, idempotencyKey);
	}

	@PostMapping("/{withdrawalId}/cancel")
	public WithdrawalResponse cancel(AuthenticatedUser investor, @PathVariable UUID withdrawalId) {
		return withdrawals.cancel(investor.userId(), withdrawalId);
	}

}
