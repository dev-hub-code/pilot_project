package com.sealease.backend.earning.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Credits investors' monthly payouts as they fall due. */
@Component
class PayoutJob {

	private static final Logger log = LoggerFactory.getLogger(PayoutJob.class);

	private final PayoutService payouts;

	PayoutJob(PayoutService payouts) {
		this.payouts = payouts;
	}

	@Scheduled(fixedDelayString = "${app.payouts.sweep-interval:1h}", initialDelayString = "${app.payouts.initial-delay:1m}")
	void payDue() {
		int paid = payouts.payDue();
		if (paid > 0) {
			log.info("Paid {} monthly payout(s) to investors", paid);
		}
	}

}
