package com.sealease.backend.reconciliation;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.ledger.dto.TrialBalanceLine;
import com.sealease.backend.ledger.service.AccountType;
import com.sealease.backend.ledger.service.LedgerService;
import com.sealease.backend.payment.dto.UnmatchedReceiptResponse;
import com.sealease.backend.payment.service.UnmatchedGatewayReceipts;
import com.sealease.backend.reconciliation.ReconciliationReport.InTransit;
import com.sealease.backend.reconciliation.ReconciliationReport.PayoutEvent;
import com.sealease.backend.reconciliation.ReconciliationReport.PendingRefund;
import com.sealease.backend.reconciliation.ReconciliationReport.StuckWithdrawal;
import com.sealease.backend.reconciliation.ReconciliationReport.UnpostedDeposit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Reconciliation: lists what needs finance's attention and checks the ledger against payments and withdrawals. The
 * report is computed on request; a daily run keeps a copy ({@code reconciliation_runs}).
 */
@Service
public class ReconciliationService {

	private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);
	private static final int LIMIT = 200;
	/** Statuses after which a withdrawal holds no money in transit (paid out, or given back to the wallet). */
	private static final String CLOSED = "'PAID', 'RETURNED', 'REJECTED', 'CANCELLED'";

	private final JdbcTemplate jdbc;
	private final LedgerService ledger;
	private final UnmatchedGatewayReceipts unmatched;
	private final JsonMapper json;
	private final Clock clock;
	private final Duration stuckAfter;
	private final Duration refundAfter;
	private final Duration eventsWithin;

	public ReconciliationService(JdbcTemplate jdbc, LedgerService ledger, UnmatchedGatewayReceipts unmatched,
			JsonMapper json, Clock clock,
			@Value("${app.reconciliation.withdrawal-stuck-after:3d}") Duration stuckAfter,
			@Value("${app.reconciliation.refund-overdue-after:3d}") Duration refundAfter,
			@Value("${app.reconciliation.payout-events-within:30d}") Duration eventsWithin) {
		this.jdbc = jdbc;
		this.ledger = ledger;
		this.unmatched = unmatched;
		this.json = json;
		this.clock = clock;
		this.stuckAfter = stuckAfter;
		this.refundAfter = refundAfter;
		this.eventsWithin = eventsWithin;
	}

	@Transactional(readOnly = true)
	public ReconciliationReport report() {
		Instant now = clock.instant();
		List<PayoutEvent> events = payoutEvents(now.minus(eventsWithin));
		List<StuckWithdrawal> stuck = stuckWithdrawals(now.minus(stuckAfter));
		List<PendingRefund> refunds = pendingRefunds(now.minus(refundAfter));
		List<UnmatchedReceiptResponse> receipts = unmatched.list(true);
		List<InTransit> inTransit = inTransit();
		List<TrialBalanceLine> trial = ledger.trialBalance();
		List<UnpostedDeposit> unposted = unpostedDeposits();
		int issues = events.size() + stuck.size() + refunds.size() + receipts.size() + unposted.size()
				+ (int) inTransit.stream().filter(t -> !t.matches()).count()
				+ (int) trial.stream().filter(t -> !t.balanced()).count();
		return new ReconciliationReport(now, issues, events, stuck, refunds, receipts, inTransit, trial, unposted);
	}

	/**
	 * Runs the reconciliation and keeps the result, once a day across all instances: the first to take the lock runs
	 * it, the others find it done.
	 *
	 * @return the report, or empty when another instance ran it within {@code minimumGap}
	 */
	@Transactional
	public Optional<ReconciliationReport> run(Duration minimumGap) {
		Boolean locked = jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(hashtext('reconciliation_runs'))",
				Boolean.class);
		if (!Boolean.TRUE.equals(locked)) {
			return Optional.empty();
		}
		Instant now = clock.instant();
		Integer recent = jdbc.queryForObject("SELECT count(*) FROM reconciliation_runs WHERE run_at > ?", Integer.class,
				Timestamp.from(now.minus(minimumGap)));
		if (recent != null && recent > 0) {
			return Optional.empty();
		}
		ReconciliationReport report = report();
		jdbc.update("INSERT INTO reconciliation_runs (id, run_at, issues, report) VALUES (?, ?, ?, CAST(? AS jsonb))",
				UUID.randomUUID(), Timestamp.from(report.generatedAt()), report.issues(), json.writeValueAsString(report));
		if (report.issues() > 0) {
			log.warn("Reconciliation: {} item(s) need attention: {} payout event(s), {} stuck withdrawal(s), {} overdue "
					+ "refund(s), {} unmatched receipt(s), {} in-transit mismatch(es), {} unbalanced currency(ies), {} "
					+ "unposted deposit(s)", report.issues(), report.payoutEvents().size(), report.stuckWithdrawals().size(),
					report.pendingRefunds().size(), report.unmatchedReceipts().size(),
					report.inTransit().stream().filter(t -> !t.matches()).count(),
					report.trialBalance().stream().filter(t -> !t.balanced()).count(), report.unpostedDeposits().size());
		}
		else {
			log.info("Reconciliation: everything reconciles");
		}
		return Optional.of(report);
	}

	/** Recent daily runs: when, and how many items needed attention. */
	@Transactional(readOnly = true)
	public List<Map<String, Object>> runs(int limit) {
		return jdbc.query("SELECT id, run_at, issues FROM reconciliation_runs ORDER BY run_at DESC LIMIT ?",
				(rs, i) -> Map.<String, Object>of("id", rs.getObject("id", UUID.class), "runAt",
						rs.getTimestamp("run_at").toInstant(), "issues", rs.getInt("issues")), Math.min(limit, 365));
	}

	// ----------------------------------------------------------------------------- checks

	private List<PayoutEvent> payoutEvents(Instant since) {
		return jdbc.query("""
				SELECT e.id, e.event_id, e.event_type, e.outcome, e.provider_reference, e.received_at,
				       w.reference AS withdrawal_reference, w.status AS withdrawal_status
				FROM payout_webhook_events e LEFT JOIN withdrawals w ON w.id = e.withdrawal_id
				WHERE e.outcome IN ('NEEDS_RECONCILIATION', 'AMOUNT_MISMATCH', 'UNKNOWN_WITHDRAWAL') AND e.received_at >= ?
				ORDER BY e.received_at LIMIT ?""", (rs, i) -> new PayoutEvent(rs.getObject("id", UUID.class),
				rs.getString("event_id"), rs.getString("event_type"), rs.getString("outcome"),
				rs.getString("provider_reference"), rs.getString("withdrawal_reference"), rs.getString("withdrawal_status"),
				rs.getTimestamp("received_at").toInstant()), Timestamp.from(since), LIMIT);
	}

	private List<StuckWithdrawal> stuckWithdrawals(Instant before) {
		return jdbc.query("""
				SELECT id, reference, status, amount, currency, status_changed_at FROM withdrawals
				WHERE status = 'PROCESSING' AND status_changed_at < ?
				ORDER BY status_changed_at LIMIT ?""", (rs, i) -> new StuckWithdrawal(rs.getObject("id", UUID.class),
				rs.getString("reference"), rs.getString("status"), money(rs.getBigDecimal("amount"), rs.getString("currency")),
				rs.getTimestamp("status_changed_at").toInstant()), Timestamp.from(before), LIMIT);
	}

	private List<PendingRefund> pendingRefunds(Instant before) {
		return jdbc.query("""
				SELECT id, order_id, method, amount, currency, settled_at FROM payments
				WHERE status = 'REFUND_REQUIRED' AND settled_at < ?
				ORDER BY settled_at LIMIT ?""", (rs, i) -> new PendingRefund(rs.getObject("id", UUID.class),
				rs.getObject("order_id", UUID.class), rs.getString("method"),
				money(rs.getBigDecimal("amount"), rs.getString("currency")), rs.getTimestamp("settled_at").toInstant()),
				Timestamp.from(before), LIMIT);
	}

	/** Every withdrawal that reserved money and is not closed should still be in transit, and nothing else. */
	private List<InTransit> inTransit() {
		Map<String, Money> ledgerSide = ledger.balancesAt(AccountType.WITHDRAWALS_IN_TRANSIT, clock.instant().plusSeconds(1));
		Map<String, Money> open = new TreeMap<>();
		jdbc.query("""
				SELECT w.currency, sum(w.amount) AS total FROM withdrawals w
				WHERE w.status NOT IN (%s) AND EXISTS (SELECT 1 FROM ledger_transactions t
				      WHERE t.transaction_type = 'WITHDRAWAL_RESERVE' AND t.reference = w.id::text)
				GROUP BY w.currency""".formatted(CLOSED), rs -> {
			open.put(rs.getString("currency"), Money.of(rs.getBigDecimal("total"), Currency.getInstance(rs.getString("currency"))));
		});
		TreeSet<String> currencies = new TreeSet<>(ledgerSide.keySet());
		currencies.addAll(open.keySet());
		return currencies.stream().map(c -> {
			Money zero = Money.zero(Currency.getInstance(c));
			Money inLedger = ledgerSide.getOrDefault(c, zero);
			Money inWithdrawals = open.getOrDefault(c, zero);
			return new InTransit(c, MoneyResponse.from(inLedger), MoneyResponse.from(inWithdrawals),
					inLedger.equals(inWithdrawals));
		}).toList();
	}

	/**
	 * Payments settled since deposits are posted to the ledger (the first such posting) without their posting, and
	 * refunds of posted deposits without theirs. Earlier payments were never posted, and are left as they are. A payment
	 * from the investor's balance is posted as a reinvestment (always, so its own posting is looked for from the start).
	 */
	private List<UnpostedDeposit> unpostedDeposits() {
		return jdbc.query("""
				WITH since AS (SELECT min(created_at) AS at FROM ledger_transactions WHERE transaction_type = 'DEPOSIT_RECEIVED')
				SELECT p.id, p.status, p.amount, p.currency, p.settled_at,
				       CASE WHEN r.id IS NOT NULL THEN 'DEPOSIT_REFUNDED'
				            WHEN p.method = 'WALLET' THEN 'REINVESTMENT' ELSE 'DEPOSIT_RECEIVED' END AS missing
				FROM payments p CROSS JOIN since
				LEFT JOIN ledger_transactions r ON r.reference = p.id::text
				      AND r.transaction_type = CASE WHEN p.method = 'WALLET' THEN 'REINVESTMENT' ELSE 'DEPOSIT_RECEIVED' END
				LEFT JOIN ledger_transactions f ON f.transaction_type = 'DEPOSIT_REFUNDED' AND f.reference = p.id::text
				WHERE p.settled_at IS NOT NULL AND (p.method = 'WALLET' OR (since.at IS NOT NULL AND p.settled_at >= since.at))
				  AND (r.id IS NULL OR (p.status = 'REFUNDED' AND f.id IS NULL))
				ORDER BY p.settled_at LIMIT ?""", (rs, i) -> new UnpostedDeposit(rs.getObject("id", UUID.class),
				rs.getString("status"), money(rs.getBigDecimal("amount"), rs.getString("currency")),
				rs.getTimestamp("settled_at").toInstant(), rs.getString("missing")), LIMIT);
	}

	private static MoneyResponse money(BigDecimal amount, String currency) {
		return MoneyResponse.from(Money.of(amount, Currency.getInstance(currency)));
	}

}
