package com.sealease.backend.earning.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Credits investors' rent for every hour that has ended, shortly after it ends. */
@Component
class PayoutJob {

	private static final Logger log = LoggerFactory.getLogger(PayoutJob.class);

	private final PayoutService payouts;

	PayoutJob(PayoutService payouts) {
		this.payouts = payouts;
	}

	@Scheduled(fixedDelayString = "${app.payouts.sweep-interval:1h}", initialDelayString = "${app.payouts.initial-delay:1m}")
	void creditDue() {
		int hours = payouts.creditDue();
		if (hours > 0) {
			log.info("Credited {} step(s) of rent to investors (each lease from where it was last credited)", hours);
		}
	}

}
