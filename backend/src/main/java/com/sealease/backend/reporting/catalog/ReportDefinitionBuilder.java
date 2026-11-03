package com.sealease.backend.reporting.catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Fluent construction of catalogue entries; see {@link ReportDefinition} for what each part means. */
final class ReportDefinitionBuilder {

	private final String key;
	private final ReportCategory category;
	private final String title;
	private String description = "";
	private Set<String> requires = Set.of();
	private String from;
	private String where;
	private String groupBy;
	private final List<ReportColumn> columns = new ArrayList<>();
	private String dateSql;
	private boolean dateIsDay;
	private String dateLabel;
	private final List<ChoiceFilter> choices = new ArrayList<>();
	private List<String> search = List.of();
	private String amountSql;
	private String currencySql = "'INR'";
	private String sort;
	private boolean descending = true;
	private String note;

	ReportDefinitionBuilder(String key, ReportCategory category, String title) {
		this.key = key;
		this.category = category;
		this.title = title;
	}

	ReportDefinitionBuilder describe(String text) {
		this.description = text;
		return this;
	}

	ReportDefinitionBuilder requires(String... permissions) {
		this.requires = Set.of(permissions);
		return this;
	}

	ReportDefinitionBuilder from(String sql) {
		this.from = sql;
		return this;
	}

	ReportDefinitionBuilder where(String sql) {
		this.where = sql;
		return this;
	}

	ReportDefinitionBuilder groupBy(String sql) {
		this.groupBy = sql;
		return this;
	}

	ReportDefinitionBuilder columns(ReportColumn... add) {
		columns.addAll(List.of(add));
		return this;
	}

	ReportDefinitionBuilder period(String sql, String label) {
		this.dateSql = sql;
		this.dateLabel = label;
		return this;
	}

	ReportDefinitionBuilder periodByDay(String sql, String label) {
		period(sql, label);
		this.dateIsDay = true;
		return this;
	}

	ReportDefinitionBuilder choice(String choiceKey, String label, String sql, List<String> values) {
		choices.add(new ChoiceFilter(choiceKey, label, sql, values));
		return this;
	}

	ReportDefinitionBuilder search(String... sql) {
		this.search = List.of(sql);
		return this;
	}

	ReportDefinitionBuilder amount(String sql) {
		this.amountSql = sql;
		return this;
	}

	ReportDefinitionBuilder currency(String sql) {
		this.currencySql = sql;
		return this;
	}

	ReportDefinitionBuilder sort(String column, boolean desc) {
		this.sort = column;
		this.descending = desc;
		return this;
	}

	ReportDefinitionBuilder note(String text) {
		this.note = text;
		return this;
	}

	ReportDefinition build() {
		return new ReportDefinition(key, category, title, description, requires, from, where, groupBy, columns, dateSql,
				dateIsDay, dateLabel, choices, search, amountSql, currencySql, sort == null ? columns.getFirst().key() : sort,
				descending, note);
	}

}
