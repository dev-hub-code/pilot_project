package com.sealease.backend.withdrawal.service;

import com.sealease.backend.notification.service.NotificationService;
import com.sealease.backend.withdrawal.dto.WithdrawalEventResponse;
import com.sealease.backend.withdrawal.entity.Withdrawal;
import com.sealease.backend.withdrawal.entity.WithdrawalStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * A withdrawal's timeline: every status change, who made it and why, kept in {@code withdrawal_events} (immutable),
 * and the investor's notification for it. Called in the transaction that makes the change, so the event and the
 * notification exist only if the change committed, and a retried webhook or job that changes nothing records nothing.
 */
@Service
public class WithdrawalHistory {

	private final JdbcTemplate jdbc;
	private final NotificationService notifications;
	private final Clock clock;

	public WithdrawalHistory(JdbcTemplate jdbc, NotificationService notifications, Clock clock) {
		this.jdbc = jdbc;
		this.notifications = notifications;
		this.clock = clock;
	}

	/** What the investor is told, per kind of change. {@code null}: nothing. */
	public enum Notice {
		SUBMITTED("WITHDRAWAL_SUBMITTED", "Withdrawal request received"),
		UNDER_REVIEW("WITHDRAWAL_UNDER_REVIEW", "Your withdrawal is being reviewed"),
		INFO_REQUIRED("WITHDRAWAL_INFO_REQUIRED", "More information needed for your withdrawal"),
		ON_HOLD("WITHDRAWAL_ON_HOLD", "Your withdrawal is on hold"),
		APPROVED("WITHDRAWAL_APPROVED", "Your withdrawal is approved"),
		REJECTED("WITHDRAWAL_REJECTED", "Your withdrawal was not approved"),
		CANCELLED("WITHDRAWAL_CANCELLED", "Your withdrawal is cancelled"),
		PAYMENT_INITIATED("WITHDRAWAL_PAYMENT_INITIATED", "Your withdrawal is being paid"),
		PAID("WITHDRAWAL_PAID", "Your withdrawal has been paid"),
		FAILED("WITHDRAWAL_FAILED", "Your withdrawal payment did not go through"),
		RETURNED("WITHDRAWAL_RETURNED", "Your withdrawal is back in your wallet");

		private final String type;
		private final String title;

		Notice(String type, String title) {
			this.type = type;
			this.title = title;
		}

		public String type() {
			return type;
		}

	}

	/**
	 * Records a status change on the timeline and, with a {@code notice}, tells the investor.
	 *
	 * @param reason   why (a rejection reason, a hold reason, a payout reference); shown to the investor only when
	 *                 {@code visible}
	 * @param visible  whether the investor sees this step on their tracking page
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void record(Withdrawal w, WithdrawalStatus from, UUID actorId, String reason, boolean visible, Notice notice) {
		jdbc.update("""
				INSERT INTO withdrawal_events (id, withdrawal_id, from_status, to_status, actor_id, reason,
				                               visible_to_investor, created_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)""", UUID.randomUUID(), w.getId(), from == null ? null : from.name(),
				w.getStatus().name(), actorId, reason, visible, Timestamp.from(clock.instant()));
		if (notice != null) {
			notifications.notify(w.getUserId(), notice.type, notice.title, body(w, notice, visible ? reason : null),
					"/withdrawals/" + w.getId());
		}
	}

	/** The timeline, oldest first; investors see only the steps meant for them, and no staff names. */
	@Transactional(readOnly = true)
	public List<WithdrawalEventResponse> timeline(UUID withdrawalId, boolean staff) {
		return jdbc.query("""
				SELECT e.*, trim(coalesce(u.first_name, '') || ' ' || coalesce(u.last_name, '')) AS actor_name
				FROM withdrawal_events e LEFT JOIN users u ON u.id = e.actor_id
				WHERE e.withdrawal_id = ? %s ORDER BY e.created_at, e.id""".formatted(staff ? "" : "AND e.visible_to_investor"),
				(rs, i) -> new WithdrawalEventResponse(
						rs.getString("from_status") == null ? null : WithdrawalStatus.valueOf(rs.getString("from_status")),
						WithdrawalStatus.valueOf(rs.getString("to_status")),
						staff ? rs.getObject("actor_id", UUID.class) : null,
						staff ? blankToNull(rs.getString("actor_name")) : null,
						rs.getString("reason"), rs.getBoolean("visible_to_investor"),
						rs.getTimestamp("created_at").toInstant()),
				withdrawalId);
	}

	private static String body(Withdrawal w, Notice notice, String reason) {
		String what = "Withdrawal " + w.getReference() + " of " + w.amount().display();
		return switch (notice) {
			case SUBMITTED -> what + " is submitted. " + w.net().display() + " will be paid once it is reviewed.";
			case UNDER_REVIEW -> what + " is being reviewed by our finance team.";
			case INFO_REQUIRED -> what + " needs more information from you: " + reason;
			case ON_HOLD -> what + " is on hold" + (reason == null ? "." : ": " + reason);
			case APPROVED -> what + " is approved. " + w.net().display() + " will be paid to you shortly.";
			case REJECTED -> what + " was not approved: " + reason + ". The amount is back in your wallet.";
			case CANCELLED -> what + " is cancelled. The amount is back in your wallet.";
			case PAYMENT_INITIATED -> w.net().display() + " from withdrawal " + w.getReference() + " is being paid"
					+ (w.isOfficeCash() ? " in cash at our office." : " into your bank account.");
			case PAID -> w.net().display() + " from withdrawal " + w.getReference() + " has been paid"
					+ (w.getPayoutReference() == null ? "." : " (reference " + w.getPayoutReference() + ").");
			case FAILED -> "The payment for withdrawal " + w.getReference() + " did not go through. The amount is held "
					+ "safely while our finance team sorts it out.";
			case RETURNED -> w.amount().display() + " from withdrawal " + w.getReference() + " is back in your wallet.";
		};
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}

}
