package com.sealease.backend.common.time;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.ZoneId;
import java.util.Locale;
import java.util.TimeZone;

/**
 * The time zone dates and times are shown to people in (statements, reports): the business's own, India Standard Time
 * by default. Data is still kept in UTC.
 *
 * @param timeZone an IANA zone id, e.g. "Asia/Kolkata"
 */
@ConfigurationProperties(prefix = "app.display")
public record DisplayTimeZone(@DefaultValue("Asia/Kolkata") String timeZone) {

	public DisplayTimeZone {
		ZoneId.of(timeZone);
	}

	public ZoneId zone() {
		return ZoneId.of(timeZone);
	}

	/** "India Standard Time". */
	public String longName() {
		return TimeZone.getTimeZone(zone()).getDisplayName(false, TimeZone.LONG, Locale.ENGLISH);
	}

}
