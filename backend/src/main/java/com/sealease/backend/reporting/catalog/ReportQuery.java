package com.sealease.backend.reporting.catalog;

import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * What a viewer asks of a report: the period (inclusive days in the display time zone), choices, search, amount
 * range, sort and page. Checked against the report's definition by {@link ReportQueryEngine}.
 */
public record ReportQuery(LocalDate from, LocalDate to, Map<String, List<String>> choices, String search, BigDecimal min,
		BigDecimal max, String sort, Boolean descending, int page, int size) {

	public static final int MAX_PAGE_SIZE = 200;
	/** Longest period a report covers: five years. */
	public static final long MAX_DAYS = 5 * 366;

	public ReportQuery {
		choices = choices == null ? Map.of() : Map.copyOf(choices);
		if ((from == null) != (to == null)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Give both the start and the end of the period");
		}
		if (from != null && to.isBefore(from)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "The period cannot end before it starts");
		}
		if (from != null && ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "A report covers at most five years");
		}
		if (min != null && max != null && max.compareTo(min) < 0) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "The maximum amount cannot be below the minimum");
		}
		if (search != null && search.length() > 100) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Search for at most 100 characters");
		}
		if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Pages hold 1 to " + MAX_PAGE_SIZE + " rows");
		}
	}

	public ReportQuery withPage(int newPage, int newSize) {
		return new ReportQuery(from, to, choices, search, min, max, sort, descending, newPage, newSize);
	}

	/** The same filters over the equally long period just before; for comparisons. */
	public ReportQuery previousPeriod() {
		if (from == null) {
			return this;
		}
		long days = ChronoUnit.DAYS.between(from, to) + 1;
		return new ReportQuery(from.minusDays(days), from.minusDays(1), choices, search, min, max, sort, descending, page,
				size);
	}

}
