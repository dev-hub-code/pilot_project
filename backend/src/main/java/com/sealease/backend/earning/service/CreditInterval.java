package com.sealease.backend.earning.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * The length of a rent credit step: rent is credited one step at a time, each once it has ended. Whole minutes
 * dividing a day evenly; steps are counted from the epoch (UTC), so hours stay whole hours and days start at midnight UTC.
 */
public record CreditInterval(Duration length) {

	public CreditInterval {
		Objects.requireNonNull(length, "length");
		long seconds = length.toSeconds();
		if (seconds < 60 || seconds % 60 != 0 || Duration.ofDays(1).toSeconds() % seconds != 0) {
			throw new IllegalArgumentException("A credit interval must be whole minutes dividing a day evenly, not " + length);
		}
	}

	public static CreditInterval ofMinutes(long minutes) {
		return new CreditInterval(Duration.ofMinutes(minutes));
	}

	public long minutes() {
		return length.toMinutes();
	}

	/** The start of the step {@code at} falls in. */
	public Instant stepStart(Instant at) {
		long step = length.toSeconds();
		long seconds = at.getEpochSecond();
		return Instant.ofEpochSecond(seconds - Math.floorMod(seconds, step));
	}

	/** {@code at} itself if it is on a step boundary, otherwise the next boundary. */
	public Instant nextStepFrom(Instant at) {
		Instant start = stepStart(at);
		return start.equals(at) ? at : start.plus(length);
	}

	/** The first step boundary after {@code at}: when what has accrued since {@code at} is next credited. */
	public Instant nextBoundaryAfter(Instant at) {
		return stepStart(at).plus(length);
	}

	/** For people: "every hour", "every 6 hours", "every day", "every 15 minutes". */
	public String label() {
		long minutes = minutes();
		if (minutes % (24 * 60) == 0) {
			return "every day";
		}
		if (minutes % 60 == 0) {
			return minutes == 60 ? "every hour" : "every " + (minutes / 60) + " hours";
		}
		return "every " + minutes + " minutes";
	}

}
