package com.sealease.backend.order.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class OrderExpiryJob {

	private static final Logger log = LoggerFactory.getLogger(OrderExpiryJob.class);

	private final OrderService orders;

	OrderExpiryJob(OrderService orders) {
		this.orders = orders;
	}

	@Scheduled(fixedDelayString = "${app.orders.expiry-sweep-interval:60s}")
	void run() {
		int expired = orders.expireDue();
		if (expired > 0) {
			log.info("Expired {} unpaid order(s) and released their capacity", expired);
		}
	}

}
