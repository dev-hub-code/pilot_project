package com.sealease.backend.earning.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.time.Instant;

/**
 * How rent is credited, by default. Admins can change the interval in the admin panel
 * ({@link RentCreditSettingsService}); this applies until they do.
 *
 * @param creditInterval rent is credited in steps of this length, each once it has ended. An hour by default; shorter
 *                       (e.g. 5 minutes) to test crediting quickly. It must be whole minutes and divide a day evenly.
 */
@ConfigurationProperties(prefix = "app.payouts")
public record RentCreditProperties(@DefaultValue("1h") Duration creditInterval) {

	public RentCreditProperties {
		try {
			new CreditInterval(creditInterval);
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("app.payouts.credit-interval must be whole minutes dividing a day evenly, not "
					+ creditInterval, ex);
		}
	}

	public CreditInterval interval() {
		return new CreditInterval(creditInterval);
	}

	/** The start of the step {@code at} falls in (steps are counted from the epoch, so whole hours stay whole). */
	public Instant stepStart(Instant at) {
		return interval().stepStart(at);
	}

	/** {@code at} itself if it is on a step boundary, otherwise the next boundary. */
	public Instant nextStepFrom(Instant at) {
		return interval().nextStepFrom(at);
	}

}
