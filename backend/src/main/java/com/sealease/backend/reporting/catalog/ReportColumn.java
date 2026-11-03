package com.sealease.backend.reporting.catalog;

import java.util.Set;

/**
 * One column of a report. {@code sql} is a fixed expression from the catalogue, never user input.
 *
 * @param link         where a value drills down to ("user", "order"), with {@code linkKey} the hidden column holding
 *                     the id; null when it does not
 * @param hidden       selected for links (ids) but not shown or exported
 * @param visibleWith  permissions that reveal the value (any one); without them it is masked. Empty: always shown
 * @param total        money and number columns summed in the totals row
 */
public record ReportColumn(String key, String label, ColumnType type, String sql, boolean sortable, String link,
		String linkKey, boolean hidden, Set<String> visibleWith, boolean total) {

	public static ReportColumn of(String key, String label, ColumnType type, String sql) {
		return new ReportColumn(key, label, type, sql, true, null, null, false, Set.of(), type == ColumnType.MONEY);
	}

	public static ReportColumn text(String key, String label, String sql) {
		return of(key, label, ColumnType.TEXT, sql);
	}

	public static ReportColumn status(String key, String label, String sql) {
		return of(key, label, ColumnType.STATUS, sql);
	}

	public static ReportColumn money(String key, String label, String sql) {
		return of(key, label, ColumnType.MONEY, sql);
	}

	public static ReportColumn number(String key, String label, String sql) {
		return of(key, label, ColumnType.NUMBER, sql);
	}

	public static ReportColumn percent(String key, String label, String sql) {
		return of(key, label, ColumnType.PERCENT, sql);
	}

	public static ReportColumn month(String key, String label, String sql) {
		return of(key, label, ColumnType.MONTH, sql);
	}

	public static ReportColumn date(String key, String label, String sql) {
		return of(key, label, ColumnType.DATE, sql);
	}

	public static ReportColumn dateTime(String key, String label, String sql) {
		return of(key, label, ColumnType.DATETIME, sql);
	}

	/** An id for drill-down links; not shown. */
	public static ReportColumn id(String key, String sql) {
		return new ReportColumn(key, key, ColumnType.TEXT, sql, false, null, null, true, Set.of(), false);
	}

	public ReportColumn linkTo(String target, String idColumnKey) {
		return new ReportColumn(key, label, type, sql, sortable, target, idColumnKey, hidden, visibleWith, total);
	}

	public ReportColumn maskedWithout(String... permissions) {
		return new ReportColumn(key, label, type, sql, sortable, link, linkKey, hidden, Set.of(permissions), total);
	}

	public ReportColumn summed() {
		return new ReportColumn(key, label, type, sql, sortable, link, linkKey, hidden, visibleWith, true);
	}

	public ReportColumn notSummed() {
		return new ReportColumn(key, label, type, sql, sortable, link, linkKey, hidden, visibleWith, false);
	}

	public ReportColumn unsortable() {
		return new ReportColumn(key, label, type, sql, false, link, linkKey, hidden, visibleWith, total);
	}

}
