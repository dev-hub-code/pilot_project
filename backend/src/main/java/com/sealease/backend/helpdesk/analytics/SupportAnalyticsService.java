package com.sealease.backend.helpdesk.analytics;

import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.time.DisplayTimeZone;
import com.sealease.backend.helpdesk.escalation.EscalationService;
import com.sealease.backend.helpdesk.service.AssignmentService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Support dashboard and analytics, computed from the tickets, their history, escalations and feedback (nothing is
 * stored or estimated). Period figures are compared with the period of the same length just before; snapshot figures
 * (backlog now) have no comparison. Each KPI names the ticket queue filter that lists the tickets behind it.
 */
@Service
public class SupportAnalyticsService {

	/**
	 * @param previous     the same figure for the previous period; null for snapshots
	 * @param unit         count, minutes, percent or rating
	 * @param higherBetter whether a rise is good news
	 * @param drill        ticket queue query string listing the tickets behind the figure, or null
	 */
	public record Kpi(String key, String label, Double value, Double previous, String unit, boolean higherBetter, String drill) {
	}

	public record Point(LocalDate day, long created, long resolved, long breaches) {
	}

	public record Slice(String key, long count) {
	}

	public record CsatPoint(LocalDate weekStart, Double average, long ratings) {
	}

	public record Dashboard(LocalDate from, LocalDate to, LocalDate previousFrom, LocalDate previousTo, String timeZone,
			List<Kpi> kpis, List<Point> trend, List<Slice> byStatus, List<Slice> byPriority, List<Slice> byCategory,
			List<Slice> byChannel, List<Slice> aging, List<AssignmentService.Workload> workload,
			List<EscalationService.Escalation> recentEscalations, List<CsatPoint> csat, List<Slice> ratings) {
	}

	private final JdbcTemplate jdbc;
	private final AssignmentService assignment;
	private final EscalationService escalations;
	private final DisplayTimeZone display;
	private final Clock clock;

	public SupportAnalyticsService(JdbcTemplate jdbc, AssignmentService assignment, EscalationService escalations,
			DisplayTimeZone display, Clock clock) {
		this.jdbc = jdbc;
		this.assignment = assignment;
		this.escalations = escalations;
		this.display = display;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public Dashboard dashboard(LocalDate from, LocalDate to) {
		ZoneId zone = display.zone();
		LocalDate today = clock.instant().atZone(zone).toLocalDate();
		LocalDate end = to == null ? today : to;
		LocalDate start = from == null ? end.minusDays(29) : from;
		if (start.isAfter(end)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "The period must start on or before its end");
		}
		long days = ChronoUnit.DAYS.between(start, end) + 1;
		if (days > 366) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Choose a period of at most a year");
		}
		LocalDate prevEnd = start.minusDays(1);
		LocalDate prevStart = prevEnd.minusDays(days - 1);
		Instant a = start.atStartOfDay(zone).toInstant();
		Instant b = end.plusDays(1).atStartOfDay(zone).toInstant();
		Instant pa = prevStart.atStartOfDay(zone).toInstant();
		Map<String, Double> now = period(a, b);
		Map<String, Double> before = period(pa, a);
		Map<String, Double> snap = snapshot();
		String range = "view=all&from=" + start + "&to=" + end;
		Timestamp ta = Timestamp.from(a);
		Timestamp tb = Timestamp.from(b);
		List<Kpi> kpis = new ArrayList<>();
		kpis.add(kpi("created", "New tickets", now, before, "count", false, range));
		kpis.add(kpi("resolved", "Resolved", now, before, "count", true, null));
		kpis.add(kpi("closed", "Closed", now, before, "count", true, null));
		kpis.add(kpi("reopened", "Reopened", now, before, "count", false, null));
		kpis.add(kpi("escalated", "Escalated", now, before, "count", false, "view=escalated"));
		kpis.add(kpi("breaches", "SLA breaches", now, before, "count", false, "view=breached"));
		kpis.add(kpi("firstResponseMinutes", "Avg first response", now, before, "minutes", false, null));
		kpis.add(kpi("resolutionMinutes", "Avg resolution time", now, before, "minutes", false, null));
		kpis.add(kpi("firstResponseSla", "First response SLA met", now, before, "percent", true, null));
		kpis.add(kpi("resolutionSla", "Resolution SLA met", now, before, "percent", true, null));
		kpis.add(kpi("csat", "Customer satisfaction", now, before, "rating", true, null));
		kpis.add(kpi("csatPercent", "Satisfied customers", now, before, "percent", true, null));
		kpis.add(new Kpi("open", "Open now", snap.get("open"), null, "count", false, "view=open"));
		kpis.add(new Kpi("unassigned", "Unassigned now", snap.get("unassigned"), null, "count", false, "view=unassigned"));
		kpis.add(new Kpi("breachedOpen", "Breached now", snap.get("breachedOpen"), null, "count", false, "view=breached"));
		kpis.add(new Kpi("atRisk", "At risk now", snap.get("atRisk"), null, "count", false, "view=at-risk"));
		kpis.add(new Kpi("escalatedOpen", "Escalated now", snap.get("escalatedOpen"), null, "count", false, "view=escalated"));
		kpis.add(new Kpi("waiting", "Waiting now", snap.get("waiting"), null, "count", false, "view=waiting"));
		return new Dashboard(start, end, prevStart, prevEnd, zone.getId(), kpis, trend(start, end, zone),
				slices("SELECT status, count(*) FROM support_tickets WHERE status NOT IN ('RESOLVED', 'CLOSED') GROUP BY status ORDER BY 2 DESC"),
				slices("SELECT priority, count(*) FROM support_tickets WHERE created_at >= ? AND created_at < ? GROUP BY priority ORDER BY 2 DESC", ta, tb),
				slices("SELECT category, count(*) FROM support_tickets WHERE created_at >= ? AND created_at < ? GROUP BY category ORDER BY 2 DESC", ta, tb),
				slices("SELECT channel, count(*) FROM support_tickets WHERE created_at >= ? AND created_at < ? GROUP BY channel ORDER BY 2 DESC", ta, tb),
				aging(), assignment.workload(), escalations.recent(false, 8), csat(start, end, zone),
				slices("SELECT CAST(rating AS varchar), count(*) FROM support_feedback WHERE created_at >= ? AND created_at < ? GROUP BY rating ORDER BY rating DESC", ta, tb));
	}

	public String dashboardZone() {
		return display.zone().getId();
	}

	private static Kpi kpi(String key, String label, Map<String, Double> now, Map<String, Double> before, String unit,
			boolean higherBetter, String drill) {
		return new Kpi(key, label, now.get(key), before.get(key), unit, higherBetter, drill);
	}

	/** Figures for tickets and events in [from, to). */
	private Map<String, Double> period(Instant from, Instant to) {
		Timestamp a = Timestamp.from(from);
		Timestamp b = Timestamp.from(to);
		Map<String, Double> m = new LinkedHashMap<>();
		jdbc.query("""
				SELECT
				  (SELECT count(*) FROM support_tickets WHERE created_at >= ? AND created_at < ?),
				  (SELECT count(DISTINCT ticket_id) FROM ticket_events WHERE type = 'STATUS_CHANGED' AND to_value = 'RESOLVED' AND created_at >= ? AND created_at < ?),
				  (SELECT count(DISTINCT ticket_id) FROM ticket_events WHERE type = 'STATUS_CHANGED' AND to_value = 'CLOSED' AND created_at >= ? AND created_at < ?),
				  (SELECT count(*) FROM ticket_events WHERE type = 'REOPENED' AND created_at >= ? AND created_at < ?),
				  (SELECT count(DISTINCT ticket_id) FROM support_escalations WHERE created_at >= ? AND created_at < ?),
				  (SELECT count(*) FROM ticket_events WHERE type = 'SLA_BREACHED' AND created_at >= ? AND created_at < ?),
				  (SELECT avg(extract(epoch FROM (first_responded_at - created_at)) / 60) FROM support_tickets
				     WHERE first_responded_at >= ? AND first_responded_at < ?),
				  (SELECT avg(extract(epoch FROM (e.created_at - t.created_at)) / 60) FROM ticket_events e JOIN support_tickets t ON t.id = e.ticket_id
				     WHERE e.type = 'STATUS_CHANGED' AND e.to_value = 'RESOLVED' AND e.created_at >= ? AND e.created_at < ?),
				  (SELECT 100.0 * count(*) FILTER (WHERE first_responded_at <= first_response_due_at) / NULLIF(count(*), 0)
				     FROM support_tickets WHERE first_responded_at >= ? AND first_responded_at < ?),
				  (SELECT 100.0 * count(*) FILTER (WHERE coalesce(resolved_at, closed_at) <= resolution_due_at) / NULLIF(count(*), 0)
				     FROM support_tickets WHERE resolution_due_at IS NOT NULL AND coalesce(resolved_at, closed_at) >= ? AND coalesce(resolved_at, closed_at) < ?),
				  (SELECT avg(rating) FROM support_feedback WHERE created_at >= ? AND created_at < ?),
				  (SELECT 100.0 * count(*) FILTER (WHERE rating >= 4) / NULLIF(count(*), 0) FROM support_feedback WHERE created_at >= ? AND created_at < ?)
				""", rs -> {
			String[] keys = { "created", "resolved", "closed", "reopened", "escalated", "breaches", "firstResponseMinutes",
					"resolutionMinutes", "firstResponseSla", "resolutionSla", "csat", "csatPercent" };
			for (int i = 0; i < keys.length; i++) {
				Object v = rs.getObject(i + 1);
				m.put(keys[i], v == null ? null : round(((Number) v).doubleValue(), i >= 6 ? 1 : 0));
			}
		}, a, b, a, b, a, b, a, b, a, b, a, b, a, b, a, b, a, b, a, b, a, b, a, b);
		return m;
	}

	private Map<String, Double> snapshot() {
		Timestamp now = Timestamp.from(clock.instant());
		Timestamp soon = Timestamp.from(clock.instant().plus(2, ChronoUnit.HOURS));
		Map<String, Double> m = new LinkedHashMap<>();
		jdbc.query("""
				WITH o AS (SELECT * FROM support_tickets WHERE status NOT IN ('RESOLVED', 'CLOSED')),
				b AS (SELECT id FROM o WHERE (first_responded_at IS NULL AND first_response_due_at < ?) OR next_response_due_at < ?
				       OR (sla_paused_at IS NULL AND resolution_due_at < ?) OR first_response_breached_at IS NOT NULL
				       OR next_response_breached_at IS NOT NULL OR resolution_breached_at IS NOT NULL)
				SELECT (SELECT count(*) FROM o), (SELECT count(*) FROM o WHERE assignee_id IS NULL), (SELECT count(*) FROM b),
				       (SELECT count(*) FROM o WHERE id NOT IN (SELECT id FROM b) AND ((first_responded_at IS NULL AND first_response_due_at < ?)
				          OR next_response_due_at < ? OR (sla_paused_at IS NULL AND resolution_due_at < ?))),
				       (SELECT count(*) FROM o WHERE escalation_level > 0),
				       (SELECT count(*) FROM o WHERE status IN ('WAITING_ON_CUSTOMER', 'WAITING_ON_INTERNAL'))
				""", rs -> {
			m.put("open", rs.getDouble(1));
			m.put("unassigned", rs.getDouble(2));
			m.put("breachedOpen", rs.getDouble(3));
			m.put("atRisk", rs.getDouble(4));
			m.put("escalatedOpen", rs.getDouble(5));
			m.put("waiting", rs.getDouble(6));
		}, now, now, now, soon, soon, soon);
		return m;
	}

	private List<Point> trend(LocalDate start, LocalDate end, ZoneId zone) {
		String tz = zone.getId();
		return jdbc.query("""
				SELECT d::date,
				  (SELECT count(*) FROM support_tickets t WHERE (t.created_at AT TIME ZONE ?)::date = d::date),
				  (SELECT count(DISTINCT e.ticket_id) FROM ticket_events e WHERE e.type = 'STATUS_CHANGED' AND e.to_value = 'RESOLVED'
				     AND (e.created_at AT TIME ZONE ?)::date = d::date),
				  (SELECT count(*) FROM ticket_events e WHERE e.type = 'SLA_BREACHED' AND (e.created_at AT TIME ZONE ?)::date = d::date)
				FROM generate_series(CAST(? AS date), CAST(? AS date), interval '1 day') d ORDER BY 1""",
				(rs, i) -> new Point(rs.getDate(1).toLocalDate(), rs.getLong(2), rs.getLong(3), rs.getLong(4)),
				tz, tz, tz, java.sql.Date.valueOf(start), java.sql.Date.valueOf(end));
	}

	private List<CsatPoint> csat(LocalDate start, LocalDate end, ZoneId zone) {
		String tz = zone.getId();
		return jdbc.query("""
				SELECT w::date, avg(f.rating), count(f.id)
				FROM generate_series(date_trunc('week', CAST(? AS date)), CAST(? AS date), interval '1 week') w
				LEFT JOIN support_feedback f ON date_trunc('week', (f.created_at AT TIME ZONE ?)) = w
				GROUP BY w ORDER BY w""",
				(rs, i) -> new CsatPoint(rs.getDate(1).toLocalDate(), rs.getObject(2) == null ? null : round(rs.getDouble(2), 2),
						rs.getLong(3)), java.sql.Date.valueOf(start), java.sql.Date.valueOf(end), tz);
	}

	private List<Slice> aging() {
		Timestamp now = Timestamp.from(clock.instant());
		return jdbc.query("""
				SELECT band, count(*) FROM (
				  SELECT CASE WHEN created_at > CAST(? AS timestamptz) - interval '1 day' THEN 1
				              WHEN created_at > CAST(? AS timestamptz) - interval '3 days' THEN 2
				              WHEN created_at > CAST(? AS timestamptz) - interval '7 days' THEN 3 ELSE 4 END AS band
				  FROM support_tickets WHERE status NOT IN ('RESOLVED', 'CLOSED')) x
				GROUP BY band ORDER BY band""",
				(rs, i) -> new Slice(switch (rs.getInt(1)) {
					case 1 -> "Under 1 day";
					case 2 -> "1-3 days";
					case 3 -> "3-7 days";
					default -> "Over 7 days";
				}, rs.getLong(2)), now, now, now);
	}

	private List<Slice> slices(String sql, Object... args) {
		return jdbc.query(sql, (rs, i) -> new Slice(rs.getString(1), rs.getLong(2)), args);
	}

	private static double round(double v, int places) {
		double f = Math.pow(10, places);
		return Math.round(v * f) / f;
	}

}
