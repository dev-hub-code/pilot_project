package com.sealease.backend.helpdesk.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.helpdesk.entity.SupportTicket;
import com.sealease.backend.helpdesk.entity.TicketEventType;
import com.sealease.backend.helpdesk.repository.SupportTicketRepository;
import com.sealease.backend.notification.service.NotificationService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Customer satisfaction: a customer rates a resolved or closed ticket of theirs once (1-5, optional comment). Ratings
 * are immutable. A rating of 2 or less tells the assignee, so poor experiences are followed up.
 */
@Service
public class FeedbackService {

	public record Feedback(UUID ticketId, int rating, String comment, Instant createdAt) {
	}

	public record FeedbackRow(UUID ticketId, String reference, String subject, int rating, String comment, Instant createdAt,
			UUID customerId, String customerName, UUID assigneeId, String assigneeName, String category) {
	}

	private final SupportTicketRepository tickets;
	private final TicketHistory history;
	private final NotificationService notifications;
	private final AuditService audit;
	private final JdbcTemplate jdbc;
	private final Clock clock;

	public FeedbackService(SupportTicketRepository tickets, TicketHistory history, NotificationService notifications,
			AuditService audit, JdbcTemplate jdbc, Clock clock) {
		this.tickets = tickets;
		this.history = history;
		this.notifications = notifications;
		this.audit = audit;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Transactional
	public Feedback give(UUID userId, UUID ticketId, int rating, String comment) {
		SupportTicket ticket = tickets.findByIdForUpdate(ticketId).filter(t -> t.getUserId().equals(userId))
			.orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
		if (ticket.getStatus().isOpen()) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "You can rate a request once it is resolved");
		}
		if (find(ticketId).isPresent()) {
			throw new BusinessException(ErrorCode.CONFLICT, "You have already rated this request");
		}
		Instant now = clock.instant();
		String text = comment == null || comment.isBlank() ? null : comment.strip();
		try {
			jdbc.update("INSERT INTO support_feedback (id, ticket_id, user_id, rating, comment, created_at) VALUES (gen_random_uuid(), ?, ?, ?, ?, ?)",
					ticketId, userId, rating, text, Timestamp.from(now));
		}
		catch (DuplicateKeyException ex) {
			throw new BusinessException(ErrorCode.CONFLICT, "You have already rated this request");
		}
		history.record(ticket, userId, false, TicketEventType.FEEDBACK, null, String.valueOf(rating), null, now);
		audit.record(AuditRecord.of(userId, AuditAction.TICKET_FEEDBACK_GIVEN, "SUPPORT_TICKET", ticketId)
			.withNewValue(Map.of("rating", rating)));
		if (rating <= 2 && ticket.getAssigneeId() != null) {
			notifications.notify(ticket.getAssigneeId(), "TICKET_LOW_RATING", ticket.getReference() + " was rated " + rating + "/5",
					text == null ? ticket.getSubject() : text, "/admin/support/" + ticketId);
		}
		return new Feedback(ticketId, rating, text, now);
	}

	@Transactional(readOnly = true)
	public Optional<Feedback> find(UUID ticketId) {
		return jdbc.query("SELECT ticket_id, rating, comment, created_at FROM support_feedback WHERE ticket_id = ?",
				(rs, i) -> new Feedback(rs.getObject(1, UUID.class), rs.getInt(2), rs.getString(3), rs.getTimestamp(4).toInstant()),
				ticketId).stream().findFirst();
	}

	/** The customer's own rating of their ticket. */
	@Transactional(readOnly = true)
	public Optional<Feedback> mine(UUID userId, UUID ticketId) {
		tickets.findById(ticketId).filter(t -> t.getUserId().equals(userId))
			.orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
		return find(ticketId);
	}

	/** Ratings between {@code from} (inclusive) and {@code to} (exclusive), newest first; {@code maxRating} filters. */
	@Transactional(readOnly = true)
	public List<FeedbackRow> list(Instant from, Instant to, Integer maxRating, int limit) {
		return jdbc.query("""
				SELECT f.ticket_id, t.reference, t.subject, f.rating, f.comment, f.created_at, t.user_id,
				       cu.first_name || ' ' || cu.last_name, t.assignee_id, ag.first_name || ' ' || ag.last_name, t.category
				FROM support_feedback f JOIN support_tickets t ON t.id = f.ticket_id
				JOIN users cu ON cu.id = t.user_id LEFT JOIN users ag ON ag.id = t.assignee_id
				WHERE f.created_at >= ? AND f.created_at < ? AND (CAST(? AS integer) IS NULL OR f.rating <= CAST(? AS integer))
				ORDER BY f.created_at DESC LIMIT ?""",
				(rs, i) -> new FeedbackRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getInt(4),
						rs.getString(5), rs.getTimestamp(6).toInstant(), rs.getObject(7, UUID.class), rs.getString(8),
						rs.getObject(9, UUID.class), rs.getString(10), rs.getString(11)),
				Timestamp.from(from), Timestamp.from(to), maxRating, maxRating, Math.clamp(limit, 1, 1000));
	}

}
