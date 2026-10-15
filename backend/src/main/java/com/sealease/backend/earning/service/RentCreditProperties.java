package com.sealease.backend.earning.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.time.Instant;

/**
 * How rent is credited.
 *
 * @param creditInterval rent is credited in steps of this length, each once it has ended; leases start on a step
 *                       boundary. An hour in production; shorter (e.g. 5 minutes) only to test crediting quickly. It
 *                       must be whole minutes and divide a day evenly.
 */
@ConfigurationProperties(prefix = "app.payouts")
public record RentCreditProperties(@DefaultValue("1h") Duration creditInterval) {

	public RentCreditProperties {
		long seconds = creditInterval.toSeconds();
		if (seconds < 60 || seconds % 60 != 0 || Duration.ofDays(1).toSeconds() % seconds != 0) {
			throw new IllegalArgumentException("app.payouts.credit-interval must be whole minutes dividing a day evenly, not "
					+ creditInterval);
		}
	}

	/** The start of the step {@code at} falls in (steps are counted from the epoch, so whole hours stay whole). */
	public Instant stepStart(Instant at) {
		long step = creditInterval.toSeconds();
		long seconds = at.getEpochSecond();
		return Instant.ofEpochSecond(seconds - Math.floorMod(seconds, step));
	}

	/** {@code at} itself if it is on a step boundary, otherwise the next boundary: when a lease made at {@code at} starts. */
	public Instant nextStepFrom(Instant at) {
		Instant start = stepStart(at);
		return start.equals(at) ? at : start.plus(creditInterval);
	}

}
