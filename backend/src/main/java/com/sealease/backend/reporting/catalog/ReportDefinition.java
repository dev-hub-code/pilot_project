package com.sealease.backend.reporting.catalog;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A report in the catalogue: a read-only query over the modules' tables with its columns, filters and access rule.
 * Every piece of SQL here is fixed in code; requests only choose among the columns and filter values defined, and
 * their values are bound as parameters.
 *
 * <p>The query is {@code SELECT columns FROM from WHERE where AND filters [GROUP BY groupBy]}. {@code from} may use
 * the parameters {@code :periodFrom} and {@code :periodTo} (the report period, instants, end exclusive), always bound.
 *
 * @param requires     permissions of which the viewer needs any one (on top of REPORT_VIEW)
 * @param dateSql      the date the period filters on, or null when the report is as of now (or uses the period in
 *                     {@code from}); {@code dateIsDay}: it is a calendar day, not an instant
 * @param amountSql    the amount the amount-range filter applies to, or null
 * @param searchSql    expressions free-text search matches (case-insensitive substring)
 * @param currencySql  the row's currency, for money columns and totals
 * @param defaultSort  a column key
 */
public record ReportDefinition(String key, ReportCategory category, String title, String description,
		Set<String> requires, String from, String where, String groupBy, List<ReportColumn> columns, String dateSql,
		boolean dateIsDay, String dateLabel, List<ChoiceFilter> choices, List<String> searchSql, String amountSql,
		String currencySql, String defaultSort, boolean defaultDescending, String note) {

	public ReportDefinition {
		requires = Set.copyOf(requires);
		columns = List.copyOf(columns);
		choices = List.copyOf(choices);
		searchSql = List.copyOf(searchSql);
		String sortKey = defaultSort;
		if (columns.stream().noneMatch(c -> c.key().equals(sortKey))) {
			throw new IllegalArgumentException(key + ": default sort " + defaultSort + " is not a column");
		}
	}

	public Optional<ReportColumn> column(String columnKey) {
		return columns.stream().filter(c -> c.key().equals(columnKey)).findFirst();
	}

	public Optional<ChoiceFilter> choice(String choiceKey) {
		return choices.stream().filter(c -> c.key().equals(choiceKey)).findFirst();
	}

	public boolean grouped() {
		return groupBy != null;
	}

	public boolean usesPeriod() {
		return dateSql != null || from.contains(":period") || (where != null && where.contains(":period"));
	}

}
