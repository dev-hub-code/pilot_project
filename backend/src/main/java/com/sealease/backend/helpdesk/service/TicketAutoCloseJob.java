package com.sealease.backend.helpdesk.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Closes resolved tickets their customers have not reopened in time. */
@Component
class TicketAutoCloseJob {

	private static final Logger log = LoggerFactory.getLogger(TicketAutoCloseJob.class);

	private final SupportService support;

	TicketAutoCloseJob(SupportService support) {
		this.support = support;
	}

	@Scheduled(fixedDelayString = "${app.support.auto-close-interval:15m}", initialDelayString = "${app.support.auto-close-initial-delay:1m}")
	void closeResolved() {
		int closed = support.closeResolved();
		if (closed > 0) {
			log.info("Closed {} resolved support ticket(s)", closed);
		}
	}

}
