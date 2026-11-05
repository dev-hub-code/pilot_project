package com.sealease.backend.withdrawal.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyProperties;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.kafka.KafkaTopics;
import com.sealease.backend.ledger.service.LedgerService;
import com.sealease.backend.user.dto.UserAccount;
import com.sealease.backend.user.service.UserAccountService;
import com.sealease.backend.user.service.UserProfileService;
import com.sealease.backend.withdrawal.dto.WithdrawalAdminDetail;
import com.sealease.backend.withdrawal.dto.WithdrawalDashboard;
import com.sealease.backend.withdrawal.dto.WithdrawalNoteResponse;
import com.sealease.backend.withdrawal.dto.WithdrawalResponse;
import com.sealease.backend.withdrawal.entity.Withdrawal;
import com.sealease.backend.withdrawal.entity.WithdrawalStatus;
import com.sealease.backend.withdrawal.repository.WithdrawalBatchRepository;
import com.sealease.backend.withdrawal.repository.WithdrawalRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The finance team's review of online investors' requests: a reviewer takes a submitted request, then approves it,
 * rejects it with a reason, or puts it on hold (with a reason, possibly asking the investor for more information) and
 * resumes it. Above the dual-approval threshold, a second, different approver is needed (maker-checker). Nobody
 * reviews or approves their own withdrawal. Every step is audited, goes on the withdrawal's timeline and, where the
 * investor should know, notifies them.
 *
 * <p>Paying an approved withdrawal is {@link WithdrawalPaymentService}'s job.
 */
@Service
public class WithdrawalReviewService {

	private final WithdrawalRepository withdrawals;
	private final WithdrawalBatchRepository batches;
	private final WithdrawalService withdrawalService;
	private final WithdrawalHistory history;
	private final UserAccountService accounts;
	private final UserProfileService profiles;
	private final LedgerService ledger;
	private final AuditService audit;
	private final JdbcTemplate jdbc;
	private final MoneyProperties money;
	private final Clock clock;

	public WithdrawalReviewService(WithdrawalRepository withdrawals, WithdrawalBatchRepository batches,
			WithdrawalService withdrawalService, WithdrawalHistory history, UserAccountService accounts,
			UserProfileService profiles, LedgerService ledger, AuditService audit, JdbcTemplate jdbc,
			MoneyProperties money, Clock clock) {
		this.withdrawals = withdrawals;
		this.batches = batches;
		this.withdrawalService = withdrawalService;
		this.history = history;
		this.accounts = accounts;
		this.profiles = profiles;
		this.ledger = ledger;
		this.audit = audit;
		this.jdbc = jdbc;
		this.money = money;
		this.clock = clock;
	}

	// --------------------------------------------------------------------------------------------- review

	/** A reviewer takes a request: SUBMITTED → UNDER_REVIEW, or picks up one under review that nobody holds. */
	@Transactional
	public WithdrawalResponse take(UUID reviewerId, UUID withdrawalId) {
		Withdrawal w = lock(withdrawalId);
		notOwn(reviewerId, w);
		Instant now = clock.instant();
		if (w.getStatus() == WithdrawalStatus.SUBMITTED) {
			WithdrawalStatus before = w.startReview(reviewerId, now);
			withdrawals.flush();
			history.record(w, before, reviewerId, null, true, WithdrawalHistory.Notice.UNDER_REVIEW);
		}
		else if (w.getStatus() == WithdrawalStatus.UNDER_REVIEW && w.getReviewAssigneeId() == null) {
			w.assignReview(reviewerId, now);
		}
		else if (reviewerId.equals(w.getReviewAssigneeId())) {
			return WithdrawalResponse.forStaff(w);
		}
		else {
			throw new BusinessException(ErrorCode.CONFLICT, w.getReviewAssigneeId() != null
					? "Someone else is reviewing this withdrawal" : "This withdrawal is " + w.getStatus());
		}
		audit.record(AuditRecord.of(reviewerId, AuditAction.WITHDRAWAL_REVIEW_STARTED, WithdrawalService.ENTITY,
				withdrawalId));
		return WithdrawalResponse.forStaff(w);
	}

	/** The reviewer gives the request back to the queue, still under review. */
	@Transactional
	public WithdrawalResponse release(UUID reviewerId, UUID withdrawalId) {
		Withdrawal w = lock(withdrawalId);
		if (!reviewerId.equals(w.getReviewAssigneeId())) {
			throw new BusinessException(ErrorCode.CONFLICT, "You are not reviewing this withdrawal");
		}
		w.releaseReview();
		audit.record(AuditRecord.of(reviewerId, AuditAction.WITHDRAWAL_REVIEW_RELEASED, WithdrawalService.ENTITY,
				withdrawalId));
		return WithdrawalResponse.forStaff(w);
	}

	/**
	 * Approves: the reviewer holding it gives the first approval; above the threshold it then waits for a second,
	 * different approver (who need not hold it).
	 */
	@Transactional
	public WithdrawalResponse approve(UUID approverId, UUID withdrawalId) {
		Withdrawal w = lock(withdrawalId);
		notOwn(approverId, w);
		// The first approval comes from the reviewer holding it; a second (after a hold, too) from anyone else.
		if (w.getStatus() == WithdrawalStatus.UNDER_REVIEW && w.approvals() == 0
				&& !approverId.equals(w.getReviewAssigneeId())) {
			throw new BusinessException(ErrorCode.CONFLICT, "Take the withdrawal for review before approving it");
		}
		if (w.getStatus() != WithdrawalStatus.UNDER_REVIEW && w.getStatus() != WithdrawalStatus.PENDING_APPROVAL) {
			throw new BusinessException(ErrorCode.CONFLICT, "This withdrawal is " + w.getStatus()
					+ " and cannot be approved");
		}
		WithdrawalStatus before = w.approve(approverId, clock.instant());
		withdrawals.flush();
		boolean done = w.getStatus() == WithdrawalStatus.APPROVED;
		audit.record(AuditRecord.of(approverId, AuditAction.WITHDRAWAL_APPROVED, WithdrawalService.ENTITY, withdrawalId)
			.withOldValue(Map.of("status", before))
			.withNewValue(Map.of("status", w.getStatus(), "approvals", w.approvals(),
					"requiredApprovals", w.getRequiredApprovals())));
		history.record(w, before, approverId, done ? null : "First approval; a second approver is needed", done,
				done ? WithdrawalHistory.Notice.APPROVED : null);
		if (done) {
			withdrawalService.publish(KafkaTopics.WITHDRAWAL_APPROVED, "WithdrawalApproved", w, Map.of("scheduled", false));
		}
		return WithdrawalResponse.forStaff(w);
	}

	/** Rejects with a reason the investor sees; the amount goes back to their wallet. */
	@Transactional
	public WithdrawalResponse reject(UUID actorId, UUID withdrawalId, String reason) {
		String why = required(reason, "Give a reason for rejecting");
		Withdrawal w = lock(withdrawalId);
		notOwn(actorId, w);
		if (!WithdrawalStatus.IN_REVIEW.contains(w.getStatus()) && w.getStatus() != WithdrawalStatus.APPROVED) {
			throw new BusinessException(ErrorCode.CONFLICT, "This withdrawal is " + w.getStatus()
					+ " and can no longer be rejected");
		}
		WithdrawalStatus before = w.reject(actorId, why, clock.instant());
		w.releaseReview();
		withdrawals.flush();
		withdrawalService.release(w, actorId, "rejected");
		audit.record(AuditRecord.of(actorId, AuditAction.WITHDRAWAL_REJECTED, WithdrawalService.ENTITY, withdrawalId)
			.withOldValue(Map.of("status", before)).withNewValue(Map.of("status", w.getStatus(), "reason", why)));
		history.record(w, before, actorId, why, true, WithdrawalHistory.Notice.REJECTED);
		return WithdrawalResponse.forStaff(w);
	}

	/**
	 * Puts the request on hold with a reason the investor sees. With {@code askInvestor}, the investor is asked for
	 * more information and the request comes back to review when they answer.
	 */
	@Transactional
	public WithdrawalResponse hold(UUID actorId, UUID withdrawalId, String reason, boolean askInvestor) {
		String why = required(reason, askInvestor ? "Say what information is needed" : "Give a reason for the hold");
		Withdrawal w = lock(withdrawalId);
		notOwn(actorId, w);
		WithdrawalStatus before = w.hold(why, askInvestor, clock.instant());
		withdrawals.flush();
		audit.record(AuditRecord.of(actorId, askInvestor ? AuditAction.WITHDRAWAL_INFO_REQUESTED
				: AuditAction.WITHDRAWAL_ON_HOLD, WithdrawalService.ENTITY, withdrawalId)
			.withOldValue(Map.of("status", before)).withNewValue(Map.of("status", w.getStatus(), "reason", why)));
		history.record(w, before, actorId, why, true,
				askInvestor ? WithdrawalHistory.Notice.INFO_REQUIRED : WithdrawalHistory.Notice.ON_HOLD);
		return WithdrawalResponse.forStaff(w);
	}

	/** Back to review from hold. */
	@Transactional
	public WithdrawalResponse resume(UUID actorId, UUID withdrawalId) {
		Withdrawal w = lock(withdrawalId);
		notOwn(actorId, w);
		WithdrawalStatus before = w.resume(clock.instant());
		withdrawals.flush();
		audit.record(AuditRecord.of(actorId, AuditAction.WITHDRAWAL_RESUMED, WithdrawalService.ENTITY, withdrawalId));
		history.record(w, before, actorId, null, true, null);
		return WithdrawalResponse.forStaff(w);
	}

	// --------------------------------------------------------------------------------------------- notes

	@Transactional
	public WithdrawalNoteResponse addNote(UUID authorId, UUID withdrawalId, String body) {
		String text = required(body, "Write the note");
		withdrawals.findById(withdrawalId).orElseThrow(() -> new ResourceNotFoundException("Withdrawal", withdrawalId));
		UUID id = UUID.randomUUID();
		Instant now = clock.instant();
		jdbc.update("INSERT INTO withdrawal_notes (id, withdrawal_id, author_id, body, created_at) VALUES (?, ?, ?, ?, ?)",
				id, withdrawalId, authorId, text, Timestamp.from(now));
		audit.record(AuditRecord.of(authorId, AuditAction.WITHDRAWAL_NOTE_ADDED, WithdrawalService.ENTITY, withdrawalId)
			.withNewValue(Map.of("noteId", id.toString())));
		UserAccount author = accounts.getAccount(authorId);
		return new WithdrawalNoteResponse(id, authorId, name(author), text, now);
	}

	@Transactional(readOnly = true)
	public List<WithdrawalNoteResponse> notes(UUID withdrawalId) {
		return jdbc.query("""
				SELECT n.*, trim(coalesce(u.first_name, '') || ' ' || coalesce(u.last_name, '')) AS author_name
				FROM withdrawal_notes n JOIN users u ON u.id = n.author_id
				WHERE n.withdrawal_id = ? ORDER BY n.created_at, n.id""",
				(rs, i) -> new WithdrawalNoteResponse(rs.getObject("id", UUID.class), rs.getObject("author_id", UUID.class),
						rs.getString("author_name"), rs.getString("body"), rs.getTimestamp("created_at").toInstant()),
				withdrawalId);
	}

	// --------------------------------------------------------------------------------------------- views

	/** One withdrawal with its investor, timeline and notes. */
	@Transactional(readOnly = true)
	public WithdrawalAdminDetail detail(UUID withdrawalId) {
		Withdrawal w = withdrawals.findById(withdrawalId)
			.orElseThrow(() -> new ResourceNotFoundException("Withdrawal", withdrawalId));
		UserAccount investor = accounts.getAccount(w.getUserId());
		Currency currency = Currency.getInstance(w.getCurrency());
		Money wallet = WithdrawalRunService.whole(ledger.investorBalance(w.getUserId(), currency, w.getBalance()));
		String batch = w.getBatchId() == null ? null
				: batches.findById(w.getBatchId()).map(b -> b.getReference()).orElse(null);
		return new WithdrawalAdminDetail(WithdrawalResponse.forStaff(w),
				new WithdrawalAdminDetail.Investor(investor.userCode(), name(investor), investor.email(),
						profiles.kycStatusOf(w.getUserId()).name()),
				MoneyResponse.from(wallet), batch, history.timeline(withdrawalId, true), notes(withdrawalId));
	}

	/** The queue at a glance. */
	@Transactional(readOnly = true)
	public WithdrawalDashboard dashboard() {
		Currency currency = money.defaultCurrency();
		Instant now = clock.instant();
		LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
		Instant dayStart = today.atStartOfDay(ZoneOffset.UTC).toInstant();
		Instant monthStart = today.withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
		Map<String, Object> row = jdbc.queryForMap("""
				SELECT
				  count(*) FILTER (WHERE status = 'SUBMITTED') AS submitted_n,
				  coalesce(sum(amount) FILTER (WHERE status = 'SUBMITTED'), 0) AS submitted_a,
				  count(*) FILTER (WHERE status = 'UNDER_REVIEW') AS review_n,
				  coalesce(sum(amount) FILTER (WHERE status = 'UNDER_REVIEW'), 0) AS review_a,
				  count(*) FILTER (WHERE status = 'ON_HOLD') AS hold_n,
				  coalesce(sum(amount) FILTER (WHERE status = 'ON_HOLD'), 0) AS hold_a,
				  count(*) FILTER (WHERE status = 'PENDING_APPROVAL') AS second_n,
				  coalesce(sum(amount) FILTER (WHERE status = 'PENDING_APPROVAL'), 0) AS second_a,
				  count(*) FILTER (WHERE status = 'APPROVED') AS approved_n,
				  coalesce(sum(amount) FILTER (WHERE status = 'APPROVED'), 0) AS approved_a,
				  count(*) FILTER (WHERE status IN ('BATCHED', 'PROCESSING')) AS payment_n,
				  coalesce(sum(amount) FILTER (WHERE status IN ('BATCHED', 'PROCESSING')), 0) AS payment_a,
				  count(*) FILTER (WHERE status = 'FAILED') AS failed_n,
				  coalesce(sum(amount) FILTER (WHERE status = 'FAILED'), 0) AS failed_a,
				  count(*) FILTER (WHERE status = 'PAID' AND closed_at >= ?) AS today_n,
				  coalesce(sum(amount) FILTER (WHERE status = 'PAID' AND closed_at >= ?), 0) AS today_a,
				  count(*) FILTER (WHERE status = 'PAID' AND closed_at >= ?) AS month_n,
				  coalesce(sum(amount) FILTER (WHERE status = 'PAID' AND closed_at >= ?), 0) AS month_a,
				  count(*) FILTER (WHERE status = 'REJECTED' AND closed_at >= ?) AS rejected_n,
				  coalesce(sum(amount) FILTER (WHERE status = 'REJECTED' AND closed_at >= ?), 0) AS rejected_a,
				  avg(extract(epoch FROM closed_at - created_at) / 3600)
				    FILTER (WHERE status = 'PAID' AND closed_at >= ?) AS avg_hours
				FROM withdrawals WHERE currency = ?""",
				Timestamp.from(dayStart), Timestamp.from(dayStart), Timestamp.from(monthStart), Timestamp.from(monthStart),
				Timestamp.from(monthStart), Timestamp.from(monthStart), Timestamp.from(now.minus(30, ChronoUnit.DAYS)),
				currency.getCurrencyCode());
		Number avg = (Number) row.get("avg_hours");
		return new WithdrawalDashboard(figure(row, "submitted", currency), figure(row, "review", currency),
				figure(row, "hold", currency), figure(row, "second", currency), figure(row, "approved", currency),
				figure(row, "payment", currency), figure(row, "failed", currency), figure(row, "today", currency),
				figure(row, "month", currency), figure(row, "rejected", currency),
				avg == null ? null : Math.round(avg.doubleValue() * 10) / 10.0);
	}

	// --------------------------------------------------------------------------------------------- internal

	private Withdrawal lock(UUID withdrawalId) {
		return withdrawals.findByIdForUpdate(withdrawalId)
			.orElseThrow(() -> new ResourceNotFoundException("Withdrawal", withdrawalId));
	}

	/** Segregation of duties: nobody reviews or decides their own withdrawal. */
	private static void notOwn(UUID actorId, Withdrawal w) {
		if (actorId.equals(w.getUserId())) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "You cannot review your own withdrawal");
		}
	}

	private static String required(String text, String message) {
		if (text == null || text.isBlank()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, message);
		}
		return text.strip();
	}

	private static String name(UserAccount a) {
		return ((a.firstName() == null ? "" : a.firstName()) + " " + (a.lastName() == null ? "" : a.lastName())).strip();
	}

	private static WithdrawalDashboard.Figure figure(Map<String, Object> row, String key, Currency currency) {
		long count = ((Number) row.get(key + "_n")).longValue();
		BigDecimal amount = (BigDecimal) row.get(key + "_a");
		return new WithdrawalDashboard.Figure(count, MoneyResponse.from(Money.of(amount, currency)));
	}

}
