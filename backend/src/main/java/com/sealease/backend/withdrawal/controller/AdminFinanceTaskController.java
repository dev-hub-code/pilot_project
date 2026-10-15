package com.sealease.backend.withdrawal.controller;

import com.sealease.backend.common.api.PageResponse;
import com.sealease.backend.security.AuthenticatedUser;
import com.sealease.backend.withdrawal.dto.FinanceTaskResponse;
import com.sealease.backend.withdrawal.dto.FinanceTaskUpdateRequest;
import com.sealease.backend.withdrawal.entity.WithdrawalKind;
import com.sealease.backend.withdrawal.entity.WithdrawalPayoutMethod;
import com.sealease.backend.withdrawal.service.FinanceTaskService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
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

/** Finance tasks: the withdrawals to pay (by bank or in cash at the office) and mark paid or failed. */
@RestController
@RequestMapping("/api/v1/admin/finance-tasks")
@PreAuthorize("hasAnyAuthority('WITHDRAWAL_PROCESS', 'FINANCE_TASK_PROCESS')")
public class AdminFinanceTaskController {

	private final FinanceTaskService tasks;

	public AdminFinanceTaskController(FinanceTaskService tasks) {
		this.tasks = tasks;
	}

	/** {@code q} searches the withdrawal or batch reference, or the investor's user code, name or email. */
	@GetMapping
	public PageResponse<FinanceTaskResponse> search(AuthenticatedUser actor,
			@RequestParam(defaultValue = "OPEN") FinanceTaskService.View view,
			@RequestParam(required = false) WithdrawalPayoutMethod method, @RequestParam(required = false) WithdrawalKind kind,
			@RequestParam(required = false) UUID batchId, @RequestParam(required = false) String q,
			@PageableDefault(size = 50) Pageable pageable) {
		return PageResponse.from(tasks.search(actor.userId(), view, method, kind, batchId, q, pageable));
	}

	/** Takes the task: only the finance member it is assigned to may mark it paid or failed. */
	@PostMapping("/{withdrawalId}/take")
	public FinanceTaskResponse take(AuthenticatedUser actor, @PathVariable UUID withdrawalId) {
		return tasks.take(actor.userId(), withdrawalId);
	}

	@PostMapping("/{withdrawalId}/release")
	public FinanceTaskResponse release(AuthenticatedUser actor, @PathVariable UUID withdrawalId) {
		return tasks.release(actor.userId(), withdrawalId);
	}

	/** Paid (with the bank reference or cash receipt) or failed (with the reason). */
	@PostMapping("/{withdrawalId}")
	public FinanceTaskResponse update(AuthenticatedUser actor, @PathVariable UUID withdrawalId,
			@Valid @RequestBody FinanceTaskUpdateRequest request) {
		return tasks.update(actor.userId(), withdrawalId, request);
	}

}
