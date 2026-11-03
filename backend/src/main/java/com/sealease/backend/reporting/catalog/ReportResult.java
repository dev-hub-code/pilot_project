package com.sealease.backend.reporting.catalog;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * One page of a report: its visible columns, the rows (values in column order, money as plain decimal strings), the
 * drill-down ids per row, the totals over every matching row (not only this page), per currency.
 */
public record ReportResult(String key, String title, List<Column> columns, List<List<Object>> rows,
		List<Map<String, String>> links, long totalRows, int page, int size, List<Totals> totals, Instant generatedAt,
		String note) {

	public record Column(String key, String label, ColumnType type, boolean sortable, String link, boolean masked) {
	}

	/** @param values column key → sum, for the summed columns */
	public record Totals(String currency, Map<String, Object> values) {
	}

}
