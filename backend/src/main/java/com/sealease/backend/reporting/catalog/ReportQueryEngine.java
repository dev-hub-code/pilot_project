package com.sealease.backend.reporting.catalog;

import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.time.DisplayTimeZone;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Predicate;

/**
 * Runs catalogue reports: builds the SQL from the definition (fixed expressions only), binds every value the viewer
 * gave as a parameter, and reads in a read-only transaction with a statement timeout. Sorting and filtering are
 * limited to the definition's columns and choices; pages hold at most {@link ReportQuery#MAX_PAGE_SIZE} rows and
 * exports at most {@link #MAX_EXPORT_ROWS}.
 */
@Component
public class ReportQueryEngine {

	public static final int MAX_EXPORT_ROWS = 50_000;
	static final String MASK = "Restricted";
	private static final Instant BEGINNING = Instant.parse("2000-01-01T00:00:00Z");
	private static final Instant END_OF_TIME = Instant.parse("2200-01-01T00:00:00Z");

	private final NamedParameterJdbcTemplate jdbc;
	private final TransactionTemplate readOnly;
	private final DisplayTimeZone display;
	private final Clock clock;

	public ReportQueryEngine(NamedParameterJdbcTemplate jdbc, PlatformTransactionManager transactions,
			DisplayTimeZone display, Clock clock) {
		this.jdbc = jdbc;
		this.readOnly = new TransactionTemplate(transactions);
		this.readOnly.setReadOnly(true);
		this.display = display;
		this.clock = clock;
	}

	/** One page, with the row count and the totals over every matching row. */
	public ReportResult page(ReportDefinition report, ReportQuery query, Predicate<String> viewerHas) {
		return run(report, query, viewerHas, false);
	}

	/** Every matching row (for exports), refused beyond {@link #MAX_EXPORT_ROWS}. */
	public ReportResult all(ReportDefinition report, ReportQuery query, Predicate<String> viewerHas) {
		return run(report, query, viewerHas, true);
	}

	private ReportResult run(ReportDefinition report, ReportQuery query, Predicate<String> viewerHas, boolean everything) {
		Built built = build(report, query, viewerHas);
		return readOnly.execute(status -> {
			jdbc.getJdbcTemplate().execute("SET LOCAL statement_timeout = '20s'");
			long count = count(report, built);
			if (everything && count > MAX_EXPORT_ROWS) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED, "This export would hold " + count
						+ " rows; at most " + MAX_EXPORT_ROWS + " are exported at once. Narrow the period or filters.");
			}
			int limit = everything ? MAX_EXPORT_ROWS : query.size();
			long offset = everything ? 0 : (long) query.page() * query.size();
			List<List<Object>> rows = new ArrayList<>();
			List<Map<String, String>> links = new ArrayList<>();
			if (offset < count) {
				MapSqlParameterSource params = new MapSqlParameterSource(built.params.getValues());
				params.addValue("rowLimit", limit).addValue("rowOffset", offset);
				jdbc.query(built.select + " LIMIT :rowLimit OFFSET :rowOffset", params, rs -> {
					rows.add(row(report, built, rs));
					links.add(links(report, rs));
				});
			}
			return new ReportResult(report.key(), report.title(), built.columns, rows, links, count, query.page(),
					everything ? rows.size() : query.size(), totals(report, built), clock.instant(), report.note());
		});
	}

	private long count(ReportDefinition report, Built built) {
		String sql = report.grouped()
				? "SELECT count(*) FROM (SELECT 1 FROM " + report.from() + built.where + " GROUP BY " + report.groupBy() + ") counted"
				: "SELECT count(*) FROM " + report.from() + built.where;
		Long count = jdbc.queryForObject(sql, built.params, Long.class);
		return count == null ? 0 : count;
	}

	private List<ReportResult.Totals> totals(ReportDefinition report, Built built) {
		List<ReportColumn> summed = report.columns().stream().filter(c -> c.total() && !c.hidden() && !built.masked(c)).toList();
		if (summed.isEmpty()) {
			return List.of();
		}
		StringJoiner sums = new StringJoiner(", ");
		String sql;
		if (report.grouped()) {
			StringJoiner inner = new StringJoiner(", ");
			inner.add(report.currencySql() + " AS currency");
			for (int i = 0; i < summed.size(); i++) {
				inner.add(summed.get(i).sql() + " AS t" + i);
				sums.add("coalesce(sum(t" + i + "), 0)");
			}
			sql = "SELECT currency, " + sums + " FROM (SELECT " + inner + " FROM " + report.from() + built.where
					+ " GROUP BY " + report.groupBy() + ") grouped GROUP BY currency ORDER BY currency";
		}
		else {
			for (ReportColumn c : summed) {
				sums.add("coalesce(sum(" + c.sql() + "), 0)");
			}
			sql = "SELECT " + report.currencySql() + " AS currency, " + sums + " FROM " + report.from() + built.where
					+ " GROUP BY 1 ORDER BY 1";
		}
		return jdbc.query(sql, built.params, (rs, n) -> {
			Map<String, Object> values = new LinkedHashMap<>();
			for (int i = 0; i < summed.size(); i++) {
				values.put(summed.get(i).key(), value(summed.get(i).type(), rs, i + 2));
			}
			return new ReportResult.Totals(rs.getString(1), values);
		});
	}

	// ------------------------------------------------------------------------- building

	private record Built(String select, String where, MapSqlParameterSource params, List<ReportResult.Column> columns,
			List<ReportColumn> selected, java.util.Set<String> maskedKeys) {

		boolean masked(ReportColumn c) {
			return maskedKeys.contains(c.key());
		}

	}

	Built buildForTest(ReportDefinition report, ReportQuery query, Predicate<String> viewerHas) {
		return build(report, query, viewerHas);
	}

	private Built build(ReportDefinition report, ReportQuery query, Predicate<String> viewerHas) {
		ZoneId zone = display.zone();
		MapSqlParameterSource params = new MapSqlParameterSource();
		Instant periodFrom = query.from() == null ? BEGINNING : query.from().atStartOfDay(zone).toInstant();
		Instant periodTo = query.to() == null ? END_OF_TIME : query.to().plusDays(1).atStartOfDay(zone).toInstant();
		params.addValue("periodFrom", Timestamp.from(periodFrom)).addValue("periodTo", Timestamp.from(periodTo));
		params.addValue("now", Timestamp.from(clock.instant()));

		List<String> conditions = new ArrayList<>();
		if (report.where() != null) {
			conditions.add("(" + report.where() + ")");
		}
		if (report.dateSql() != null && query.from() != null) {
			if (report.dateIsDay()) {
				conditions.add(report.dateSql() + " >= :dayFrom AND " + report.dateSql() + " <= :dayTo");
				params.addValue("dayFrom", Date.valueOf(query.from())).addValue("dayTo", Date.valueOf(query.to()));
			}
			else {
				conditions.add(report.dateSql() + " >= :periodFrom AND " + report.dateSql() + " < :periodTo");
			}
		}
		int n = 0;
		for (Map.Entry<String, List<String>> chosen : query.choices().entrySet()) {
			if (chosen.getValue() == null || chosen.getValue().isEmpty()) {
				continue;
			}
			ChoiceFilter filter = report.choice(chosen.getKey())
				.orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED,
						"This report cannot be filtered by " + chosen.getKey()));
			for (String value : chosen.getValue()) {
				if (!filter.values().contains(value)) {
					throw new BusinessException(ErrorCode.VALIDATION_FAILED, filter.label() + " cannot be " + value);
				}
			}
			String name = "choice" + (n++);
			conditions.add(filter.sql() + " IN (:" + name + ")");
			params.addValue(name, chosen.getValue());
		}
		if (query.search() != null && !query.search().isBlank() && !report.searchSql().isEmpty()) {
			StringJoiner any = new StringJoiner(" OR ", "(", ")");
			for (String expression : report.searchSql()) {
				any.add("CAST(" + expression + " AS text) ILIKE :search ESCAPE '\\'");
			}
			conditions.add(any.toString());
			params.addValue("search", "%" + escapeLike(query.search().strip()) + "%");
		}
		if (report.amountSql() != null && query.min() != null) {
			conditions.add(report.amountSql() + " >= :minAmount");
			params.addValue("minAmount", query.min());
		}
		if (report.amountSql() != null && query.max() != null) {
			conditions.add(report.amountSql() + " <= :maxAmount");
			params.addValue("maxAmount", query.max());
		}
		String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);

		java.util.Set<String> masked = new java.util.HashSet<>();
		List<ReportResult.Column> visible = new ArrayList<>();
		for (ReportColumn c : report.columns()) {
			boolean hide = !c.visibleWith().isEmpty() && c.visibleWith().stream().noneMatch(viewerHas);
			if (hide) {
				masked.add(c.key());
			}
			if (!c.hidden()) {
				visible.add(new ReportResult.Column(c.key(), c.label(), c.type(), c.sortable() && !hide,
						hide ? null : c.link(), hide));
			}
		}

		String sortKey = query.sort() == null ? report.defaultSort() : query.sort();
		ReportColumn sortColumn = report.column(sortKey)
			.filter(c -> c.sortable() && !c.hidden() && !masked.contains(c.key()))
			.orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED, "This report cannot be sorted by " + sortKey));
		boolean descending = query.descending() == null ? report.defaultDescending() : query.descending();

		StringJoiner columns = new StringJoiner(", ");
		for (int i = 0; i < report.columns().size(); i++) {
			ReportColumn c = report.columns().get(i);
			columns.add((masked.contains(c.key()) ? "NULL" : c.sql()) + " AS c" + i);
		}
		columns.add(report.currencySql() + " AS row_currency");
		String select = "SELECT " + columns + " FROM " + report.from() + where
				+ (report.grouped() ? " GROUP BY " + report.groupBy() : "")
				+ " ORDER BY " + sortColumn.sql() + (descending ? " DESC NULLS LAST" : " ASC NULLS LAST") + ", 1";
		return new Built(select, where, params, visible, report.columns(), masked);
	}

	private List<Object> row(ReportDefinition report, Built built, ResultSet rs) throws SQLException {
		List<Object> values = new ArrayList<>();
		for (int i = 0; i < report.columns().size(); i++) {
			ReportColumn c = report.columns().get(i);
			if (c.hidden()) {
				continue;
			}
			values.add(built.masked(c) ? MASK : value(c.type(), rs, i + 1));
		}
		return values;
	}

	private static Map<String, String> links(ReportDefinition report, ResultSet rs) throws SQLException {
		Map<String, String> ids = new LinkedHashMap<>();
		for (int i = 0; i < report.columns().size(); i++) {
			ReportColumn c = report.columns().get(i);
			if (c.hidden()) {
				Object id = rs.getObject(i + 1);
				if (id != null) {
					ids.put(c.key(), id.toString());
				}
			}
		}
		return ids;
	}

	private Object value(ColumnType type, ResultSet rs, int index) throws SQLException {
		Object raw = rs.getObject(index);
		if (raw == null) {
			return null;
		}
		return switch (type) {
			case MONEY -> new BigDecimal(raw.toString()).setScale(2, RoundingMode.HALF_UP).toPlainString();
			case PERCENT -> new BigDecimal(raw.toString()).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
			case NUMBER -> raw instanceof Number number ? number : new BigDecimal(raw.toString());
			case DATE, MONTH -> raw instanceof Date d ? d.toLocalDate().toString()
					: raw instanceof Timestamp t ? LocalDate.ofInstant(t.toInstant(), display.zone()).toString() : raw.toString();
			case DATETIME -> raw instanceof Timestamp t ? t.toInstant().toString()
					: raw instanceof java.time.OffsetDateTime o ? o.toInstant().toString() : raw.toString();
			case STATUS, TEXT -> raw.toString();
		};
	}

	static String escapeLike(String text) {
		return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	/** "PENDING_APPROVAL" → "Pending approval", for exports. */
	public static String label(String constant) {
		if (constant == null || constant.isEmpty()) {
			return "";
		}
		String s = constant.toLowerCase(Locale.ROOT).replace('_', ' ');
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

}
