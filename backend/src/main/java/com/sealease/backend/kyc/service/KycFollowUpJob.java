package com.sealease.backend.kyc.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Opens support tickets for investors who have not verified their identity a day after registering. */
@Component
class KycFollowUpJob {

	private static final Logger log = LoggerFactory.getLogger(KycFollowUpJob.class);

	private final KycFollowUpService followUps;

	KycFollowUpJob(KycFollowUpService followUps) {
		this.followUps = followUps;
	}

	@Scheduled(fixedDelayString = "${app.kyc.follow-up.interval:15m}", initialDelayString = "${app.kyc.follow-up.initial-delay:2m}")
	void openDue() {
		int opened = followUps.openDue();
		if (opened > 0) {
			log.info("Opened {} identity verification ticket(s)", opened);
		}
	}

}
