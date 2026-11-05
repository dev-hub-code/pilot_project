package com.sealease.backend.reconciliation;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Reconciles once a day (on one instance) and keeps the result; anything needing attention is logged as a warning. */
@Component
class ReconciliationJob {

	private final ReconciliationService reconciliation;

	ReconciliationJob(ReconciliationService reconciliation) {
		this.reconciliation = reconciliation;
	}

	@Scheduled(cron = "${app.reconciliation.cron:0 0 6 * * *}", zone = "${app.display.time-zone:Asia/Kolkata}")
	void daily() {
		reconciliation.run(Duration.ofHours(12));
	}

}
