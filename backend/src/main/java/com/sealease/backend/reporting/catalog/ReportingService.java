package com.sealease.backend.reporting.catalog;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.common.time.DisplayTimeZone;
import com.sealease.backend.reporting.export.ExportContext;
import com.sealease.backend.reporting.export.ExportFormat;
import com.sealease.backend.reporting.export.ReportExporter;
import com.sealease.backend.security.AuthenticatedUser;
import com.sealease.backend.user.dto.UserAccount;
import com.sealease.backend.user.service.UserAccountService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The reporting area: which catalogue reports a viewer may open, running them, exporting them (audited and kept in
 * the execution history) and listing that history. Access uses the existing permissions only: REPORT_VIEW (or
 * REPORT_GENERATE) for the area, a report's own permissions for registers that name investors or show their money,
 * and REPORT_GENERATE or REPORT_EXPORT to download.
 */
@Service
public class ReportingService {

	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm z", Locale.ENGLISH);
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

	private final ReportCatalog catalog;
	private final ReportQueryEngine engine;
	private final ReportExporter exporter;
	private final AuditService audit;
	private final UserAccountService accounts;
	private final JdbcTemplate jdbc;
	private final TransactionTemplate ownTransaction;
	private final DisplayTimeZone display;
	private final JsonMapper json;
	private final Clock clock;

	public ReportingService(ReportCatalog catalog, ReportQueryEngine engine, ReportExporter exporter, AuditService audit,
			UserAccountService accounts, JdbcTemplate jdbc, PlatformTransactionManager transactions, DisplayTimeZone display,
			JsonMapper json, Clock clock) {
		this.catalog = catalog;
		this.engine = engine;
		this.exporter = exporter;
		this.audit = audit;
		this.accounts = accounts;
		this.jdbc = jdbc;
		this.ownTransaction = new TransactionTemplate(transactions);
		this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.display = display;
		this.json = json;
		this.clock = clock;
	}

	// ----------------------------------------------------------------------- catalogue

	public record CatalogEntry(String key, String title, String description, boolean usesPeriod, String dateLabel,
			List<Choice> choices, boolean searchable, boolean amountFilter, List<ReportResult.Column> columns,
			String defaultSort, boolean defaultDescending) {
	}

	public record Choice(String key, String label, List<String> values) {
	}

	public record CatalogCategory(String key, String label, List<CatalogEntry> reports) {
	}

	/** The categories and reports this viewer may open, in order; categories with none are left out. */
	public List<CatalogCategory> catalog(AuthenticatedUser viewer) {
		requireArea(viewer);
		List<CatalogCategory> categories = new ArrayList<>();
		catalog.byCategory().forEach((category, reports) -> {
			List<CatalogEntry> open = reports.stream().filter(r -> mayOpen(viewer, r)).map(r -> entry(r, viewer)).toList();
			if (!open.isEmpty()) {
				categories.add(new CatalogCategory(category.name(), category.label(), open));
			}
		});
		return categories;
	}

	private CatalogEntry entry(ReportDefinition r, AuthenticatedUser viewer) {
		List<ReportResult.Column> columns = r.columns().stream()
			.filter(c -> !c.hidden())
			.map(c -> {
				boolean masked = !c.visibleWith().isEmpty() && c.visibleWith().stream().noneMatch(viewer::hasPermission);
				return new ReportResult.Column(c.key(), c.label(), c.type(), c.sortable() && !masked, masked ? null : c.link(), masked);
			})
			.toList();
		return new CatalogEntry(r.key(), r.title(), r.description(), r.usesPeriod(), r.dateLabel(),
				r.choices().stream().map(c -> new Choice(c.key(), c.label(), c.values())).toList(), !r.searchSql().isEmpty(),
				r.amountSql() != null, columns, r.defaultSort(), r.defaultDescending());
	}

	// ------------------------------------------------------------------------- running

	public ReportResult run(AuthenticatedUser viewer, String key, ReportQuery query) {
		ReportDefinition report = open(viewer, key);
		return engine.page(report, query, viewer::hasPermission);
	}

	public record Export(byte[] body, String filename, ExportFormat format, int rows) {
	}

	/** Every matching row as a file; audited, and kept in the execution history whether it succeeds or fails. */
	public Export export(AuthenticatedUser viewer, String key, ReportQuery query, ExportFormat format) {
		ReportDefinition report = open(viewer, key);
		if (!viewer.hasPermission("REPORT_GENERATE") && !viewer.hasPermission("REPORT_EXPORT")) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "Downloading reports needs the REPORT_GENERATE or REPORT_EXPORT permission");
		}
		Instant started = clock.instant();
		Map<String, Object> filters = filters(query);
		try {
			ReportResult result = engine.all(report, query, viewer::hasPermission);
			byte[] body = exporter.export(result, context(report, query, viewer), format);
			int ms = (int) Duration.between(started, clock.instant()).toMillis();
			record(viewer.userId(), report, format, filters, result.rows().size(), ms, null);
			Map<String, Object> audited = new LinkedHashMap<>(filters);
			audited.put("report", report.key());
			audited.put("format", format.name());
			audited.put("rows", result.rows().size());
			ownTransaction.executeWithoutResult(status ->
					audit.record(AuditRecord.of(viewer.userId(), AuditAction.REPORT_GENERATED, "REPORT", report.key())
						.withNewValue(audited)));
			String stamp = query.from() == null ? started.atZone(display.zone()).toLocalDate().toString()
					: query.from() + "-to-" + query.to();
			return new Export(body, report.key() + "-" + stamp + "." + format.extension(), format, result.rows().size());
		}
		catch (RuntimeException ex) {
			int ms = (int) Duration.between(started, clock.instant()).toMillis();
			String failure = ex instanceof BusinessException business ? business.getMessage() : "The report could not be produced";
			record(viewer.userId(), report, format, filters, null, ms, failure);
			throw ex;
		}
	}

	private ExportContext context(ReportDefinition report, ReportQuery query, AuthenticatedUser viewer) {
		List<String> filters = new ArrayList<>();
		query.choices().forEach((choiceKey, values) -> report.choice(choiceKey).ifPresent(c -> {
			if (values != null && !values.isEmpty()) {
				filters.add(c.label() + ": " + String.join(", ", values.stream().map(ReportQueryEngine::label).toList()));
			}
		}));
		if (query.search() != null && !query.search().isBlank()) {
			filters.add("Search: " + query.search().strip());
		}
		if (query.min() != null) {
			filters.add("Amount from ₹" + query.min().toPlainString());
		}
		if (query.max() != null) {
			filters.add("Amount up to ₹" + query.max().toPlainString());
		}
		String period = !report.usesPeriod() ? "As of " + DAY.format(clock.instant().atZone(display.zone()))
				: query.from() == null ? "All dates"
				: DAY.format(query.from()) + " – " + DAY.format(query.to()) + (report.dateLabel() == null ? "" : " (" + report.dateLabel().toLowerCase(Locale.ROOT) + ")");
		UserAccount who = accounts.getAccount(viewer.userId());
		return new ExportContext(report.title(), report.description(), period, filters,
				STAMP.format(clock.instant().atZone(display.zone())), who.firstName() + " " + who.lastName(), report.note());
	}

	private static Map<String, Object> filters(ReportQuery query) {
		Map<String, Object> filters = new LinkedHashMap<>();
		if (query.from() != null) {
			filters.put("from", query.from().toString());
			filters.put("to", query.to().toString());
		}
		query.choices().forEach((k, v) -> {
			if (v != null && !v.isEmpty()) {
				filters.put(k, v);
			}
		});
		if (query.search() != null && !query.search().isBlank()) {
			filters.put("search", query.search().strip());
		}
		if (query.min() != null) {
			filters.put("min", query.min().toPlainString());
		}
		if (query.max() != null) {
			filters.put("max", query.max().toPlainString());
		}
		return filters;
	}

	private void record(UUID userId, ReportDefinition report, ExportFormat format, Map<String, Object> filters, Integer rows,
			int ms, String failure) {
		ownTransaction.executeWithoutResult(status -> jdbc.update("""
				INSERT INTO report_executions (id, report_key, report_title, executed_by, executed_at, format, filters,
				    row_count, duration_ms, status, failure)
				VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)""",
				UUID.randomUUID(), report.key(), report.title(), userId, Timestamp.from(clock.instant()), format.name(),
				json.writeValueAsString(filters), rows, ms, failure == null ? "COMPLETED" : "FAILED",
				failure == null ? null : failure.length() > 500 ? failure.substring(0, 500) : failure));
	}

	// ------------------------------------------------------------------------- history

	public record Execution(UUID id, String reportKey, String reportTitle, String executedBy, Instant executedAt,
			String format, String filters, Integer rows, Integer durationMs, String status, String failure) {
	}

	public record HistoryPage(List<Execution> executions, long total, int page, int size, boolean everyone) {
	}

	/** The viewer's own report runs; everyone's for those with AUDIT_VIEW. */
	public HistoryPage history(AuthenticatedUser viewer, int page, int size) {
		requireArea(viewer);
		int pageSize = Math.clamp(size, 1, 100);
		int pageIndex = Math.max(page, 0);
		boolean everyone = viewer.hasPermission("AUDIT_VIEW");
		String where = everyone ? "" : " WHERE e.executed_by = ?";
		Object[] args = everyone ? new Object[0] : new Object[] { viewer.userId() };
		Long total = jdbc.queryForObject("SELECT count(*) FROM report_executions e" + where, Long.class, args);
		List<Object> pageArgs = new ArrayList<>(List.of(args));
		pageArgs.add(pageSize);
		pageArgs.add((long) pageIndex * pageSize);
		List<Execution> rows = jdbc.query("""
				SELECT e.id, e.report_key, e.report_title, u.first_name || ' ' || u.last_name AS who, e.executed_at, e.format,
				       e.filters::text AS filters, e.row_count, e.duration_ms, e.status, e.failure
				FROM report_executions e JOIN users u ON u.id = e.executed_by""" + where
				+ " ORDER BY e.executed_at DESC LIMIT ? OFFSET ?",
				(rs, n) -> new Execution(rs.getObject("id", UUID.class), rs.getString("report_key"), rs.getString("report_title"),
						rs.getString("who"), rs.getTimestamp("executed_at").toInstant(), rs.getString("format"),
						rs.getString("filters"), (Integer) rs.getObject("row_count"), (Integer) rs.getObject("duration_ms"),
						rs.getString("status"), rs.getString("failure")),
				pageArgs.toArray());
		return new HistoryPage(rows, total == null ? 0 : total, pageIndex, pageSize, everyone);
	}

	// ---------------------------------------------------------------------------- access

	static boolean mayEnterArea(AuthenticatedUser viewer) {
		return viewer.hasPermission("REPORT_VIEW") || viewer.hasPermission("REPORT_GENERATE");
	}

	static boolean mayOpen(AuthenticatedUser viewer, ReportDefinition report) {
		return mayEnterArea(viewer) && (report.requires().isEmpty() || report.requires().stream().anyMatch(viewer::hasPermission));
	}

	private static void requireArea(AuthenticatedUser viewer) {
		if (!mayEnterArea(viewer)) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "Reports need the REPORT_VIEW permission");
		}
	}

	private ReportDefinition open(AuthenticatedUser viewer, String key) {
		requireArea(viewer);
		ReportDefinition report = catalog.find(key).orElseThrow(() -> new ResourceNotFoundException("Report", key));
		if (!mayOpen(viewer, report)) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "This report needs one of these permissions: "
					+ String.join(", ", report.requires().stream().sorted().toList()));
		}
		return report;
	}

}
