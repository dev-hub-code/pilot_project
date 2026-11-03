package com.sealease.backend.reporting.export;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyFormat;
import com.sealease.backend.reporting.catalog.ColumnType;
import com.sealease.backend.reporting.catalog.ReportQueryEngine;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.Locale;

/** Formats report values for people (PDF) and for data (CSV): Indian grouping and ₹ for rupees. */
final class Cells {

	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
	private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);
	private static final DateTimeFormatter ISO_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ENGLISH);

	private Cells() {
	}

	/** For reading: "₹1,23,450.50", "3 Nov 2026", "Pending approval". */
	static String display(ColumnType type, Object value, String currency, ZoneId zone) {
		if (value == null) {
			return "";
		}
		String s = value.toString();
		return switch (type) {
			case MONEY -> s.equals("Restricted") ? s : MoneyFormat.format(Money.of(new BigDecimal(s), Currency.getInstance(currency)));
			case PERCENT -> s + "%";
			case NUMBER -> s;
			case DATE -> DAY.format(LocalDate.parse(s));
			case MONTH -> MONTH.format(LocalDate.parse(s));
			case DATETIME -> STAMP.format(Instant.parse(s).atZone(zone));
			case STATUS -> ReportQueryEngine.label(s);
			case TEXT -> s;
		};
	}

	/** For data files: plain decimals, ISO dates and times in the display time zone, labelled statuses. */
	static String data(ColumnType type, Object value, ZoneId zone) {
		if (value == null) {
			return "";
		}
		String s = value.toString();
		return switch (type) {
			case DATETIME -> ISO_STAMP.format(Instant.parse(s).atZone(zone));
			case MONTH -> s.substring(0, 7);
			case STATUS -> ReportQueryEngine.label(s);
			default -> s;
		};
	}

}
