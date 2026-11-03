package com.sealease.backend.helpdesk.sla;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Working time for SLA targets: opening hours per day of the week, holidays, in one time zone. Immutable. Without any
 * working day configured, business time falls back to calendar time (so a target can always be reached).
 */
public final class BusinessCalendar {

	public record Hours(LocalTime opens, LocalTime closes) {
		public Hours {
			if (!closes.isAfter(opens)) {
				throw new IllegalArgumentException("Closing time must be after opening time");
			}
		}

		long minutes() {
			return Duration.between(opens, closes).toMinutes();
		}
	}

	/** Searching further than this for working time means the configuration has none. */
	private static final int MAX_DAYS = 3 * 366;

	private final ZoneId zone;
	private final Map<DayOfWeek, Hours> hours;
	private final Set<LocalDate> holidays;

	public BusinessCalendar(ZoneId zone, Map<DayOfWeek, Hours> hours, Set<LocalDate> holidays) {
		this.zone = zone;
		this.hours = hours.isEmpty() ? Map.of() : new EnumMap<>(hours);
		this.holidays = Set.copyOf(holidays);
	}

	public ZoneId zone() {
		return zone;
	}

	public boolean hasWorkingTime() {
		return !hours.isEmpty();
	}

	/** Minutes in a working day (the longest configured day); 24 hours without working days. */
	public long minutesPerWorkingDay() {
		return hours.values().stream().mapToLong(Hours::minutes).max().orElse(24 * 60);
	}

	/** The instant {@code minutes} of (business or calendar) time after {@code start}. */
	public Instant plus(Instant start, long minutes, boolean business) {
		if (minutes < 0) {
			throw new IllegalArgumentException("minutes must not be negative");
		}
		if (!business || !hasWorkingTime()) {
			return start.plus(Duration.ofMinutes(minutes));
		}
		long remaining = minutes;
		ZonedDateTime cursor = start.atZone(zone);
		for (int day = 0; day < MAX_DAYS; day++) {
			LocalDate date = cursor.toLocalDate();
			Hours open = workingHours(date);
			if (open != null) {
				ZonedDateTime from = later(cursor, date.atTime(open.opens()).atZone(zone));
				ZonedDateTime until = date.atTime(open.closes()).atZone(zone);
				if (from.isBefore(until)) {
					long available = Duration.between(from, until).toMinutes();
					if (remaining <= available) {
						return from.plusMinutes(remaining).toInstant();
					}
					remaining -= available;
				}
			}
			cursor = date.plusDays(1).atStartOfDay(zone);
		}
		return start.plus(Duration.ofMinutes(minutes));
	}

	/** Business (or calendar) minutes from {@code from} to {@code to}; zero when {@code to} is not after {@code from}. */
	public long minutesBetween(Instant from, Instant to, boolean business) {
		if (!to.isAfter(from)) {
			return 0;
		}
		if (!business || !hasWorkingTime()) {
			return Duration.between(from, to).toMinutes();
		}
		long total = 0;
		ZonedDateTime cursor = from.atZone(zone);
		ZonedDateTime end = to.atZone(zone);
		for (int day = 0; day < 100 * 366 && cursor.isBefore(end); day++) {
			LocalDate date = cursor.toLocalDate();
			Hours open = workingHours(date);
			if (open != null) {
				ZonedDateTime start = later(cursor, date.atTime(open.opens()).atZone(zone));
				ZonedDateTime stop = earlier(end, date.atTime(open.closes()).atZone(zone));
				if (start.isBefore(stop)) {
					total += Duration.between(start, stop).toMinutes();
				}
			}
			cursor = date.plusDays(1).atStartOfDay(zone);
		}
		return total;
	}

	private Hours workingHours(LocalDate date) {
		return holidays.contains(date) ? null : hours.get(date.getDayOfWeek());
	}

	private static ZonedDateTime later(ZonedDateTime a, ZonedDateTime b) {
		return a.isAfter(b) ? a : b;
	}

	private static ZonedDateTime earlier(ZonedDateTime a, ZonedDateTime b) {
		return a.isBefore(b) ? a : b;
	}

}
