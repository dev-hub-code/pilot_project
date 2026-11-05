package com.sealease.backend.reconciliation;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Finance's reconciliation: what needs attention now, and the daily runs. Read-only. */
@RestController
public class AdminReconciliationController {

	private final ReconciliationService reconciliation;

	public AdminReconciliationController(ReconciliationService reconciliation) {
		this.reconciliation = reconciliation;
	}

	@GetMapping("/api/v1/admin/reconciliation")
	@PreAuthorize("hasAuthority('FINANCE_VIEW')")
	public ReconciliationReport report() {
		return reconciliation.report();
	}

	@GetMapping("/api/v1/admin/reconciliation/runs")
	@PreAuthorize("hasAuthority('FINANCE_VIEW')")
	public List<Map<String, Object>> runs(@RequestParam(defaultValue = "30") int limit) {
		return reconciliation.runs(Math.max(limit, 1));
	}

}
