package com.sealease.backend.helpdesk;

import com.sealease.backend.helpdesk.sla.BusinessCalendar;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Business-time arithmetic for SLA targets: working hours, weekends, holidays and time zones. */
class BusinessCalendarTest {

	private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");

	private static BusinessCalendar weekdays(Set<LocalDate> holidays) {
		Map<DayOfWeek, BusinessCalendar.Hours> hours = new EnumMap<>(DayOfWeek.class);
		for (DayOfWeek d : new DayOfWeek[] { DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY }) {
			hours.put(d, new BusinessCalendar.Hours(LocalTime.of(9, 0), LocalTime.of(18, 0)));
		}
		return new BusinessCalendar(KOLKATA, hours, holidays);
	}

	private static Instant at(String local) {
		return ZonedDateTime.of(java.time.LocalDateTime.parse(local), KOLKATA).toInstant();
	}

	@Test
	void businessTimeSkipsNightsAndWeekends() {
		BusinessCalendar calendar = weekdays(Set.of());
		// Friday 17:00 + 2 business hours: 1 hour on Friday, then Monday 09:00-10:00.
		assertThat(calendar.plus(at("2026-11-06T17:00"), 120, true)).isEqualTo(at("2026-11-09T10:00"));
		// Saturday counts nothing: work starts Monday at 09:00.
		assertThat(calendar.plus(at("2026-11-07T12:00"), 30, true)).isEqualTo(at("2026-11-09T09:30"));
		// Before opening, the clock starts at opening time.
		assertThat(calendar.plus(at("2026-11-04T07:00"), 60, true)).isEqualTo(at("2026-11-04T10:00"));
		// Calendar time ignores working hours.
		assertThat(calendar.plus(at("2026-11-06T17:00"), 120, false)).isEqualTo(at("2026-11-06T19:00"));
	}

	@Test
	void holidaysAreNotWorkingDays() {
		BusinessCalendar calendar = weekdays(Set.of(LocalDate.of(2026, 11, 9)));
		assertThat(calendar.plus(at("2026-11-06T17:00"), 120, true)).isEqualTo(at("2026-11-10T10:00"));
	}

	@Test
	void minutesBetweenCountsOnlyWorkingTime() {
		BusinessCalendar calendar = weekdays(Set.of());
		assertThat(calendar.minutesBetween(at("2026-11-06T17:00"), at("2026-11-09T10:00"), true)).isEqualTo(120);
		assertThat(calendar.minutesBetween(at("2026-11-07T08:00"), at("2026-11-08T20:00"), true)).isZero();
		assertThat(calendar.minutesBetween(at("2026-11-09T10:00"), at("2026-11-06T17:00"), true)).isZero();
		assertThat(calendar.minutesBetween(at("2026-11-06T17:00"), at("2026-11-06T19:00"), false)).isEqualTo(120);
	}

	@Test
	void withoutWorkingHoursBusinessTimeIsCalendarTime() {
		BusinessCalendar calendar = new BusinessCalendar(KOLKATA, Map.of(), Set.of());
		assertThat(calendar.plus(at("2026-11-07T12:00"), 90, true)).isEqualTo(at("2026-11-07T13:30"));
	}

}
