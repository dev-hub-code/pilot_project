package com.sealease.backend.outbox;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class OutboxRelayJob {

	private final OutboxRelay relay;
	private final OutboxProperties properties;

	OutboxRelayJob(OutboxRelay relay, OutboxProperties properties) {
		this.relay = relay;
		this.properties = properties;
	}

	@Scheduled(fixedDelayString = "${app.outbox.poll-interval:1s}")
	void run() {
		// Keep draining while batches come back full, so a burst does not wait one interval per batch.
		while (relay.publishPending() == properties.batchSize()) {
			// next batch
		}
	}

}
