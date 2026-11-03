package com.sealease.backend.reporting.controller;

import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.time.DisplayTimeZone;
import com.sealease.backend.reporting.catalog.ReportOverviewService;
import com.sealease.backend.reporting.catalog.ReportQuery;
import com.sealease.backend.reporting.catalog.ReportResult;
import com.sealease.backend.reporting.catalog.ReportingService;
import com.sealease.backend.reporting.export.ExportFormat;
import com.sealease.backend.security.AuthenticatedUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reports &amp; analytics: the catalogue, the overview, any catalogue report as a page of rows or a file, and the
 * history of downloads. The area needs REPORT_VIEW (or REPORT_GENERATE), as the existing reports do; each report adds
 * its own checks (see {@link ReportingService}). Filters on a report are passed as {@code f.<choice>=<value>},
 * repeated for several values.
 */
@RestController
@PreAuthorize("hasAnyAuthority('REPORT_VIEW', 'REPORT_GENERATE')")
public class AdminAnalyticsController {

	private final ReportingService reporting;
	private final ReportOverviewService overview;
	private final DisplayTimeZone display;
	private final Clock clock;

	public AdminAnalyticsController(ReportingService reporting, ReportOverviewService overview, DisplayTimeZone display,
			Clock clock) {
		this.reporting = reporting;
		this.overview = overview;
		this.display = display;
		this.clock = clock;
	}

	@GetMapping("/api/v1/admin/analytics/catalog")
	public List<ReportingService.CatalogCategory> catalog(AuthenticatedUser viewer) {
		return reporting.catalog(viewer);
	}

	@GetMapping("/api/v1/admin/analytics/overview")
	public ReportOverviewService.Overview overview(AuthenticatedUser viewer,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		LocalDate today = clock.instant().atZone(display.zone()).toLocalDate();
		LocalDate end = to == null ? today : to;
		LocalDate start = from == null ? end.withDayOfMonth(1) : from;
		return overview.overview(viewer, start, end);
	}

	@GetMapping("/api/v1/admin/analytics/reports/{key}")
	public ResponseEntity<ReportResult> report(AuthenticatedUser viewer, @PathVariable String key,
			@RequestParam MultiValueMap<String, String> params) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reporting.run(viewer, key, query(params, true)));
	}

	@GetMapping("/api/v1/admin/analytics/reports/{key}/export")
	public ResponseEntity<byte[]> export(AuthenticatedUser viewer, @PathVariable String key,
			@RequestParam MultiValueMap<String, String> params) {
		ExportFormat format = ExportFormat.parse(params.getFirst("format"));
		ReportingService.Export file = reporting.export(viewer, key, query(params, false), format);
		return ResponseEntity.ok()
			.contentType(format.mediaType())
			.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.filename()).build().toString())
			.header("X-Report-Rows", String.valueOf(file.rows()))
			.cacheControl(CacheControl.noStore())
			.body(file.body());
	}

	@GetMapping("/api/v1/admin/analytics/history")
	public ReportingService.HistoryPage history(AuthenticatedUser viewer, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size) {
		return reporting.history(viewer, page, size);
	}

	static ReportQuery query(MultiValueMap<String, String> params, boolean paged) {
		Map<String, List<String>> choices = new LinkedHashMap<>();
		params.forEach((name, values) -> {
			if (name.startsWith("f.") && name.length() > 2) {
				List<String> nonBlank = values.stream().filter(v -> v != null && !v.isBlank()).map(String::strip).toList();
				if (!nonBlank.isEmpty()) {
					choices.put(name.substring(2), nonBlank);
				}
			}
		});
		int size = paged ? integer(params.getFirst("size"), 50) : 1;
		return new ReportQuery(date(params.getFirst("from")), date(params.getFirst("to")), choices,
				blankToNull(params.getFirst("search")), amount(params.getFirst("min")), amount(params.getFirst("max")),
				blankToNull(params.getFirst("sort")), bool(params.getFirst("desc")), paged ? integer(params.getFirst("page"), 0) : 0,
				size);
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

	private static LocalDate date(String value) {
		try {
			return blankToNull(value) == null ? null : LocalDate.parse(value.strip());
		}
		catch (java.time.format.DateTimeParseException ex) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Dates are written as YYYY-MM-DD");
		}
	}

	private static BigDecimal amount(String value) {
		try {
			return blankToNull(value) == null ? null : new BigDecimal(value.strip());
		}
		catch (NumberFormatException ex) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Amounts are plain numbers, such as 25000 or 2500.50");
		}
	}

	private static int integer(String value, int fallback) {
		try {
			return blankToNull(value) == null ? fallback : Integer.parseInt(value.strip());
		}
		catch (NumberFormatException ex) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "page and size are whole numbers");
		}
	}

	private static Boolean bool(String value) {
		return blankToNull(value) == null ? null : Boolean.parseBoolean(value.strip());
	}

}
