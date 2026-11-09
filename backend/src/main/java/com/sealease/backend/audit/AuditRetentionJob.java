package com.sealease.backend.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Once a day, deletes sign-in events older than the retention period (app.audit.sign-in-retention). */
@Component
class AuditRetentionJob {

	private static final Logger log = LoggerFactory.getLogger(AuditRetentionJob.class);

	private final AuditRetention retention;

	AuditRetentionJob(AuditRetention retention) {
		this.retention = retention;
	}

	@Scheduled(cron = "${app.audit.purge-cron:0 30 3 * * *}", zone = "${app.display.time-zone:Asia/Kolkata}")
	void purge() {
		int deleted = retention.purgeExpired();
		if (deleted > 0) {
			log.info("Deleted {} sign-in audit record(s) past their retention period", deleted);
		}
	}

}
