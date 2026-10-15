package com.sealease.backend.common.time;

import com.sealease.backend.reporting.service.ReportService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DisplayTimeZoneTest {

	@Test
	void indiaStandardTimeByDefault() {
		DisplayTimeZone display = new DisplayTimeZone("Asia/Kolkata");
		assertThat(display.zone()).isEqualTo(ZoneId.of("Asia/Kolkata"));
		assertThat(display.longName()).isEqualTo("India Standard Time");
		assertThatThrownBy(() -> new DisplayTimeZone("Mars/Base")).isInstanceOf(RuntimeException.class);
	}

	@Test
	void aStatementDayRunsFromMidnightToMidnightInTheDisplayZone() {
		ReportService.Period day = new ReportService.Period(LocalDate.parse("2026-10-14"), LocalDate.parse("2026-10-14"));
		assertThat(day.start(ZoneId.of("Asia/Kolkata"))).isEqualTo(Instant.parse("2026-10-13T18:30:00Z"));
		assertThat(day.end(ZoneId.of("Asia/Kolkata"))).isEqualTo(Instant.parse("2026-10-14T18:30:00Z"));
	}

}
