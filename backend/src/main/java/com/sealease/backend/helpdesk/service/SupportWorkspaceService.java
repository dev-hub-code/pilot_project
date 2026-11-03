package com.sealease.backend.helpdesk.service;

import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.helpdesk.entity.SupportTicket;
import com.sealease.backend.helpdesk.escalation.EscalationService;
import com.sealease.backend.helpdesk.repository.SupportTicketRepository;
import com.sealease.backend.helpdesk.sla.SlaService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The right-hand panel of the ticket workspace: SLA state, escalations, feedback, and the customer's context. Each
 * piece of customer data is included only when the viewer holds a permission that already grants it elsewhere in the
 * admin portal (contact details, investments, money movements); otherwise it is left out.
 */
@Service
public class SupportWorkspaceService {

	public record Context(SlaService.View sla, List<EscalationService.Escalation> escalations,
			FeedbackService.Feedback feedback, Customer customer, List<PreviousTicket> previousTickets,
			Investments investments, Money money) {
	}

	/** @param email null without contact permission */
	public record Customer(UUID id, String name, String userCode, String email, String status, String customerType,
			String kycStatus, Instant registeredAt, long openTickets, long totalTickets) {
	}

	public record PreviousTicket(UUID id, String reference, String subject, String status, Instant createdAt) {
	}

	public record Investments(long activeHoldings, BigDecimal investedAmount, long orders, String lastOrderNumber,
			String lastOrderStatus) {
	}

	public record Money(long pendingWithdrawals, String lastWithdrawalReference, String lastWithdrawalStatus,
			BigDecimal lastWithdrawalAmount, long paymentsAwaiting) {
	}

	private final SupportTicketRepository tickets;
	private final SlaService sla;
	private final EscalationService escalations;
	private final FeedbackService feedback;
	private final JdbcTemplate jdbc;

	public SupportWorkspaceService(SupportTicketRepository tickets, SlaService sla, EscalationService escalations,
			FeedbackService feedback, JdbcTemplate jdbc) {
		this.tickets = tickets;
		this.sla = sla;
		this.escalations = escalations;
		this.feedback = feedback;
		this.jdbc = jdbc;
	}

	@Transactional(readOnly = true)
	public Context context(UUID ticketId, boolean financeOnly, Predicate<String> can) {
		SupportTicket t = tickets.findById(ticketId).orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
		SupportService.requireVisible(t, financeOnly);
		UUID userId = t.getUserId();
		boolean contact = can.test("INVESTOR_CONTACT_VIEW") || can.test("USER_VIEW");
		Customer customer = jdbc.query("""
				SELECT u.id, u.first_name || ' ' || u.last_name, u.user_number, u.email, u.status, u.created_at,
				       (SELECT k.status FROM kyc_submissions k WHERE k.user_id = u.id ORDER BY k.submitted_at DESC LIMIT 1),
				       EXISTS (SELECT 1 FROM network_investors n WHERE n.user_id = u.id),
				       (SELECT count(*) FROM support_tickets s WHERE s.user_id = u.id AND s.status NOT IN ('RESOLVED', 'CLOSED')),
				       (SELECT count(*) FROM support_tickets s WHERE s.user_id = u.id)
				FROM users u WHERE u.id = ?""",
				(rs, i) -> new Customer(rs.getObject(1, UUID.class), rs.getString(2),
						rs.getObject(3) == null ? null : com.sealease.backend.user.entity.UserCodes.format(rs.getLong(3)),
						contact ? rs.getString(4) : null, rs.getString(5), rs.getBoolean(8) ? "NETWORK" : "ONLINE",
						rs.getString(7), rs.getTimestamp(6).toInstant(), rs.getLong(9), rs.getLong(10)), userId)
			.stream().findFirst().orElse(null);
		List<PreviousTicket> previous = jdbc.query("""
				SELECT id, reference, subject, status, created_at FROM support_tickets
				WHERE user_id = ? AND id <> ? ORDER BY created_at DESC LIMIT 10""",
				(rs, i) -> new PreviousTicket(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
						rs.getTimestamp(5).toInstant()), userId, ticketId);
		Investments investments = null;
		if (can.test("INVESTOR_VIEW") || can.test("INVESTMENT_VIEW") || can.test("ORDER_VIEW")) {
			investments = jdbc.query("""
					SELECT (SELECT count(*) FROM holdings h WHERE h.user_id = ? AND h.status = 'ACTIVE'),
					       (SELECT coalesce(sum(h.amount), 0) FROM holdings h WHERE h.user_id = ? AND h.status = 'ACTIVE'),
					       (SELECT count(*) FROM orders o WHERE o.user_id = ?),
					       o.order_number, o.status
					FROM (SELECT 1) one LEFT JOIN LATERAL (SELECT order_number, status FROM orders WHERE user_id = ?
					     ORDER BY created_at DESC LIMIT 1) o ON TRUE""",
					(rs, i) -> new Investments(rs.getLong(1), rs.getBigDecimal(2), rs.getLong(3), rs.getString(4), rs.getString(5)),
					userId, userId, userId, userId).getFirst();
		}
		Money money = null;
		if (can.test("WITHDRAWAL_VIEW") || can.test("FINANCE_VIEW") || can.test("PAYMENT_VIEW") || can.test("FINANCE_TICKET_HANDLE")) {
			money = jdbc.query("""
					SELECT (SELECT count(*) FROM withdrawals w WHERE w.user_id = ? AND w.closed_at IS NULL),
					       w.reference, w.status, w.amount,
					       (SELECT count(*) FROM payments p JOIN orders o ON o.id = p.order_id
					        WHERE o.user_id = ? AND p.status = 'PENDING')
					FROM (SELECT 1) one LEFT JOIN LATERAL (SELECT reference, status, amount FROM withdrawals WHERE user_id = ?
					     ORDER BY created_at DESC LIMIT 1) w ON TRUE""",
					(rs, i) -> new Money(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBigDecimal(4), rs.getLong(5)),
					userId, userId, userId).getFirst();
		}
		return new Context(sla.view(t), escalations.forTicket(ticketId), feedback.find(ticketId).orElse(null), customer,
				previous, investments, money);
	}

}
