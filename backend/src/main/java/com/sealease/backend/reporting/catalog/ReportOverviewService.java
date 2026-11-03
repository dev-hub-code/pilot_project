package com.sealease.backend.reporting.catalog;

import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.time.DisplayTimeZone;
import com.sealease.backend.ledger.service.AccountType;
import com.sealease.backend.security.AuthenticatedUser;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The executive overview: headline figures for a period compared with the equally long period before, figures as
 * they stand now (no comparison), twelve-month trends and the container fleet by status. Every figure is a fixed
 * aggregate query over the module tables or the ledger; nothing is estimated. Needs REPORT_VIEW (aggregates only).
 */
@Service
public class ReportOverviewService {

	private final NamedParameterJdbcTemplate jdbc;
	private final TransactionTemplate readOnly;
	private final ZoneId zone;
	private final Clock clock;

	public ReportOverviewService(NamedParameterJdbcTemplate jdbc, PlatformTransactionManager transactions,
			DisplayTimeZone display, Clock clock) {
		this.jdbc = jdbc;
		this.readOnly = new TransactionTemplate(transactions);
		this.readOnly.setReadOnly(true);
		this.zone = display.zone();
		this.clock = clock;
	}

	/**
	 * @param kind MONEY or COUNT or PERCENT
	 * @param previous the same figure over the previous period; null for figures as they stand now
	 * @param change percentage change from the previous period; null when there is no previous figure or it is zero
	 * @param report the catalogue report that lists the rows behind the figure
	 */
	public record Kpi(String key, String label, String kind, BigDecimal value, BigDecimal previous, BigDecimal change,
			String report, String hint) {
	}

	public record TrendPoint(String month, Map<String, BigDecimal> values) {
	}

	public record Slice(String key, long count) {
	}

	public record Overview(LocalDate from, LocalDate to, LocalDate previousFrom, LocalDate previousTo, String currency,
			List<Kpi> period, List<Kpi> now, List<TrendPoint> trends, List<Slice> containers, java.time.Instant generatedAt) {
	}

	private record Flow(String key, String label, String kind, String sql, String report, String hint) {
	}

	/** Figures over a period: sql returns one value for [:from, :to) (instants) or [:dayFrom, :dayTo] (days). */
	private static final List<Flow> FLOWS = List.of(
			new Flow("investedCapital", "Capital invested", "MONEY",
					"SELECT coalesce(sum(amount), 0) FROM holdings WHERE confirmed_at >= :from AND confirmed_at < :to",
					"investment-register", "Investor capital received for confirmed investments; not company revenue"),
			new Flow("investments", "New investments", "COUNT",
					"SELECT count(*) FROM holdings WHERE confirmed_at >= :from AND confirmed_at < :to",
					"investment-register", "Investments confirmed (one per container)"),
			new Flow("newInvestors", "New investors", "COUNT", """
					SELECT count(*) FROM users u WHERE u.created_at >= :from AND u.created_at < :to
					  AND EXISTS (SELECT 1 FROM user_roles ur JOIN roles r ON r.id = ur.role_id
					              WHERE ur.user_id = u.id AND r.name = 'INVESTOR')""",
					"investor-master", "Investor accounts opened"),
			new Flow("returnsCredited", "Returns credited", "MONEY",
					"SELECT coalesce(sum(rent_amount), 0) FROM rent_credits WHERE day >= :dayFrom AND day <= :dayTo",
					"earnings-register", "Earnings credited to investors' wallets (excludes capital returned)"),
			new Flow("capitalReturned", "Capital returned", "MONEY",
					"SELECT coalesce(sum(capital_amount), 0) FROM rent_credits WHERE day >= :dayFrom AND day <= :dayTo",
					"earnings-register", "Principal repaid to investors' wallets"),
			new Flow("referralCommission", "Referral commission", "MONEY",
					"SELECT coalesce(sum(amount), 0) FROM referral_earnings WHERE created_at >= :from AND created_at < :to",
					"commission-register", "Commission credited to referrers"),
			new Flow("withdrawalsPaid", "Withdrawals paid", "MONEY", """
					SELECT coalesce(sum(amount), 0) FROM withdrawals
					WHERE status = 'PAID' AND closed_at >= :from AND closed_at < :to""",
					"withdrawal-register", "Money paid out to investors' bank accounts or in cash"),
			new Flow("paymentsReceived", "Payments received", "MONEY", """
					SELECT coalesce(sum(amount), 0) FROM payments
					WHERE settled_at >= :from AND settled_at < :to AND status IN ('SUCCEEDED', 'REFUND_REQUIRED', 'REFUNDED')""",
					"payment-register", "Investment payments settled"),
			new Flow("refunds", "Refunds", "MONEY",
					"SELECT coalesce(sum(amount), 0) FROM payments WHERE refunded_at >= :from AND refunded_at < :to",
					"refund-register", "Payments returned to investors"),
			new Flow("newLeads", "New leads", "COUNT",
					"SELECT count(*) FROM leads WHERE created_at >= :from AND created_at < :to", "lead-register", null),
			new Flow("tickets", "Tickets opened", "COUNT",
					"SELECT count(*) FROM support_tickets WHERE created_at >= :from AND created_at < :to", "ticket-register", null));

	private static String investorAccounts() {
		return Arrays.stream(AccountType.values()).filter(AccountType::isPerInvestor).map(t -> "'" + t.name() + "'")
			.collect(Collectors.joining(", ", "(", ")"));
	}

	/** Figures as they stand now. */
	private static final List<Flow> STOCKS = List.of(
			new Flow("activeInvestments", "Active investments", "COUNT",
					"SELECT count(*) FROM holdings WHERE status = 'ACTIVE'", "investment-register", null),
			new Flow("capitalOutstanding", "Capital to be repaid", "MONEY", """
					SELECT coalesce(sum(coalesce(ra.capital_total, 0) - coalesce(ra.credited_capital, 0)), 0)
					FROM holdings h LEFT JOIN rent_accruals ra ON ra.holding_id = h.id
					WHERE h.status IN ('ACTIVE', 'AWAITING_ACCEPTANCE')""",
					"investment-register", "Capital the investment terms schedule for return, not yet returned"),
			new Flow("earningsOwed", "Owed to investors", "MONEY", """
					SELECT coalesce(sum(CASE WHEN e.direction = 'CREDIT' THEN e.amount ELSE -e.amount END), 0)
					FROM ledger_accounts a JOIN ledger_entries e ON e.account_id = a.id
					WHERE a.account_type IN """ + investorAccounts(),
					"investor-statements", "Wallet balances in the ledger: credited and not yet withdrawn"),
			new Flow("pendingWithdrawals", "Withdrawals in progress", "MONEY", """
					SELECT coalesce(sum(amount), 0) FROM withdrawals
					WHERE status IN ('PENDING_APPROVAL', 'APPROVED', 'BATCHED', 'PROCESSING')""",
					"withdrawal-aging", "Requested and not yet paid or returned"),
			new Flow("utilisation", "Container utilisation", "PERCENT", """
					SELECT coalesce(round(100.0 * count(*) FILTER (WHERE status = 'ON_LEASE')
					       / NULLIF(count(*) FILTER (WHERE status <> 'RETIRED'), 0), 1), 0) FROM containers""",
					"container-utilisation", "Containers on lease as a share of those not retired"),
			new Flow("kycPending", "KYC awaiting review", "COUNT",
					"SELECT count(*) FROM kyc_submissions WHERE status = 'PENDING'", "kyc-register", null),
			new Flow("openTickets", "Open tickets", "COUNT",
					"SELECT count(*) FROM support_tickets WHERE status IN ('OPEN', 'WAITING_ON_CUSTOMER')", "ticket-register", null),
			new Flow("openLeads", "Open leads", "COUNT",
					"SELECT count(*) FROM leads WHERE stage NOT IN ('WON', 'LOST')", "lead-register", null));

	public Overview overview(AuthenticatedUser viewer, LocalDate from, LocalDate to) {
		if (!viewer.hasPermission("REPORT_VIEW") && !viewer.hasPermission("REPORT_GENERATE")) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "Reports need the REPORT_VIEW permission");
		}
		ReportQuery period = new ReportQuery(from, to, Map.of(), null, null, null, null, null, 0, 1);
		ReportQuery before = period.previousPeriod();
		return readOnly.execute(status -> {
			jdbc.getJdbcTemplate().execute("SET LOCAL statement_timeout = '20s'");
			List<Kpi> flows = new ArrayList<>();
			for (Flow flow : FLOWS) {
				BigDecimal now = value(flow.sql(), params(from, to));
				BigDecimal prev = value(flow.sql(), params(before.from(), before.to()));
				flows.add(new Kpi(flow.key(), flow.label(), flow.kind(), now, prev, change(now, prev), flow.report(), flow.hint()));
			}
			List<Kpi> stocks = new ArrayList<>();
			for (Flow flow : STOCKS) {
				stocks.add(new Kpi(flow.key(), flow.label(), flow.kind(), value(flow.sql(), new MapSqlParameterSource()), null,
						null, flow.report(), flow.hint()));
			}
			return new Overview(from, to, before.from(), before.to(), "INR", flows, stocks, trends(),
					containers(), clock.instant());
		});
	}

	/** Twelve months to the current one, by month in the display time zone. */
	private List<TrendPoint> trends() {
		YearMonth last = YearMonth.from(clock.instant().atZone(zone));
		YearMonth first = last.minusMonths(11);
		Map<String, String> series = new LinkedHashMap<>();
		series.put("investedCapital", "SELECT %s AS m, sum(amount) FROM holdings WHERE confirmed_at >= :from AND confirmed_at < :to GROUP BY 1"
			.formatted(month("confirmed_at")));
		series.put("returnsCredited", "SELECT date_trunc('month', day)::date AS m, sum(rent_amount) FROM rent_credits WHERE day >= :dayFrom AND day <= :dayTo GROUP BY 1");
		series.put("capitalReturned", "SELECT date_trunc('month', day)::date AS m, sum(capital_amount) FROM rent_credits WHERE day >= :dayFrom AND day <= :dayTo GROUP BY 1");
		series.put("referralCommission", "SELECT %s AS m, sum(amount) FROM referral_earnings WHERE created_at >= :from AND created_at < :to GROUP BY 1"
			.formatted(month("created_at")));
		series.put("withdrawalsPaid", "SELECT %s AS m, sum(amount) FROM withdrawals WHERE status = 'PAID' AND closed_at >= :from AND closed_at < :to GROUP BY 1"
			.formatted(month("closed_at")));
		Map<String, Map<String, BigDecimal>> byMonth = new LinkedHashMap<>();
		for (YearMonth m = first; !m.isAfter(last); m = m.plusMonths(1)) {
			byMonth.put(m.toString(), new LinkedHashMap<>());
		}
		MapSqlParameterSource params = params(first.atDay(1), last.atEndOfMonth());
		series.forEach((key, sql) -> {
			jdbc.query(sql, params, rs -> {
				String m = YearMonth.from(rs.getDate(1).toLocalDate()).toString();
				Map<String, BigDecimal> values = byMonth.get(m);
				if (values != null) {
					values.put(key, rs.getBigDecimal(2).setScale(2, RoundingMode.HALF_UP));
				}
			});
			byMonth.values().forEach(values -> values.putIfAbsent(key, BigDecimal.ZERO.setScale(2)));
		});
		return byMonth.entrySet().stream().map(e -> new TrendPoint(e.getKey(), e.getValue())).toList();
	}

	private List<Slice> containers() {
		return jdbc.query("SELECT status, count(*) FROM containers GROUP BY status ORDER BY count(*) DESC",
				(rs, n) -> new Slice(rs.getString(1), rs.getLong(2)));
	}

	private String month(String column) {
		return "(date_trunc('month', " + column + " AT TIME ZONE '" + zone.getId() + "'))::date";
	}

	private MapSqlParameterSource params(LocalDate from, LocalDate to) {
		return new MapSqlParameterSource()
			.addValue("from", Timestamp.from(from.atStartOfDay(zone).toInstant()))
			.addValue("to", Timestamp.from(to.plusDays(1).atStartOfDay(zone).toInstant()))
			.addValue("dayFrom", java.sql.Date.valueOf(from))
			.addValue("dayTo", java.sql.Date.valueOf(to));
	}

	private BigDecimal value(String sql, MapSqlParameterSource params) {
		BigDecimal value = jdbc.queryForObject(sql, params, BigDecimal.class);
		return value == null ? BigDecimal.ZERO : value;
	}

	/** Percentage change, one decimal; null when the previous figure is zero (no meaningful percentage). */
	static BigDecimal change(BigDecimal now, BigDecimal previous) {
		if (previous == null || previous.signum() == 0) {
			return null;
		}
		return now.subtract(previous).multiply(BigDecimal.valueOf(100)).divide(previous.abs(), 1, RoundingMode.HALF_UP);
	}

	static long days(LocalDate from, LocalDate to) {
		return ChronoUnit.DAYS.between(from, to) + 1;
	}

}
