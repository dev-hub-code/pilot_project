package com.sealease.backend.withdrawal.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Makes the month's rent and referral withdrawal files once their day has come. */
@Component
class ScheduledWithdrawalJob {

	private static final Logger log = LoggerFactory.getLogger(ScheduledWithdrawalJob.class);

	private final ScheduledWithdrawalService runs;

	ScheduledWithdrawalJob(ScheduledWithdrawalService runs) {
		this.runs = runs;
	}

	@Scheduled(fixedDelayString = "${app.withdrawals.schedule-interval:1h}",
			initialDelayString = "${app.withdrawals.schedule-initial-delay:2m}")
	void runDue() {
		runs.runDue().forEach(run -> log.info("{} withdrawals for {}: {} made ({}), {} skipped, batch {}", run.kind(),
				run.period(), run.withdrawalCount(), run.total().amount(), run.skipped().size(), run.batchReference()));
	}

}
