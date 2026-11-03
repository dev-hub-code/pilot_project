package com.sealease.backend.helpdesk.escalation;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.helpdesk.entity.EscalationTrigger;
import com.sealease.backend.helpdesk.entity.SupportTicket;
import com.sealease.backend.helpdesk.entity.TicketEventType;
import com.sealease.backend.helpdesk.entity.TicketStatus;
import com.sealease.backend.helpdesk.service.TicketHistory;
import com.sealease.backend.notification.service.NotificationService;
import com.sealease.backend.user.dto.UserAccount;
import com.sealease.backend.user.service.UserAccountService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Multi-level escalation (1 assigned agent, 2 team lead, 3 support manager, 4 senior management). Levels are
 * configuration: who is told at each level is a list of existing staff accounts; no roles are created or changed.
 * Escalating raises the ticket's level, sets it ESCALATED, keeps an immutable record and tells the level's contacts
 * (and the assignee). Levels only go up.
 */
@Service
public class EscalationService {

	public static final int MAX_LEVEL = 4;
	private static final String ENTITY = "SUPPORT_TICKET";

	public record Level(int level, String name, String description, List<Contact> contacts) {
	}

	public record Contact(UUID userId, String name, String email) {
	}

	public record Escalation(UUID id, UUID ticketId, String reference, String subject, int level, int previousLevel,
			EscalationTrigger trigger, String reason, UUID escalatedBy, String escalatedByName, UUID ownerId,
			String ownerName, Instant createdAt, TicketStatus status) {
	}

	private final JdbcTemplate jdbc;
	private final TicketHistory history;
	private final NotificationService notifications;
	private final UserAccountService accounts;
	private final AuditService audit;
	private final Clock clock;

	public EscalationService(JdbcTemplate jdbc, TicketHistory history, NotificationService notifications,
			UserAccountService accounts, AuditService audit, Clock clock) {
		this.jdbc = jdbc;
		this.history = history;
		this.notifications = notifications;
		this.accounts = accounts;
		this.audit = audit;
		this.clock = clock;
	}

	/**
	 * Escalates a (locked) ticket one level, or to {@code level} when given. Caller's transaction.
	 *
	 * @return false when the ticket is resolved, closed or already at that level or above (nothing done)
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public boolean escalate(SupportTicket ticket, Integer level, EscalationTrigger trigger, String reason, UUID actorId,
			UUID ownerId) {
		int previous = ticket.getEscalationLevel();
		int target = level == null ? previous + 1 : level;
		if (!ticket.getStatus().isOpen() || target <= previous || target > MAX_LEVEL) {
			if (trigger == EscalationTrigger.MANUAL) {
				throw new BusinessException(!ticket.getStatus().isOpen() ? ErrorCode.BUSINESS_RULE_VIOLATION : ErrorCode.CONFLICT,
						!ticket.getStatus().isOpen() ? "Resolved and closed tickets cannot be escalated"
								: target > MAX_LEVEL ? "The ticket is already at the highest escalation level"
								: "Escalation only goes up: the ticket is at level " + previous);
			}
			return false;
		}
		Instant now = clock.instant();
		TicketStatus previousStatus = ticket.getStatus();
		ticket.escalateTo(target);
		ticket.acknowledge(now);
		String given = reason == null || reason.isBlank() ? describe(trigger) : reason.strip();
		String text = given.length() > 500 ? given.substring(0, 500) : given;
		jdbc.update("""
				INSERT INTO support_escalations (id, ticket_id, level, previous_level, trigger, reason, escalated_by, owner_id, created_at)
				VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?, ?, ?)""",
				ticket.getId(), target, previous, trigger.name(), text, actorId, ownerId, Timestamp.from(now));
		history.record(ticket, actorId, actorId != null, TicketEventType.ESCALATED, "L" + previous, "L" + target, ownerId, now);
		if (previousStatus != TicketStatus.ESCALATED) {
			history.record(ticket, actorId, true, TicketEventType.STATUS_CHANGED, previousStatus, TicketStatus.ESCALATED, null, now);
		}
		Map<String, Object> details = new HashMap<>();
		details.put("level", target);
		details.put("previousLevel", previous);
		details.put("trigger", trigger.name());
		details.put("reason", text);
		audit.record(AuditRecord.of(actorId, AuditAction.TICKET_ESCALATED, ENTITY, ticket.getId()).withNewValue(details));
		Set<UUID> told = new LinkedHashSet<>(contactIds(target));
		if (ticket.getAssigneeId() != null) {
			told.add(ticket.getAssigneeId());
		}
		if (ownerId != null) {
			told.add(ownerId);
		}
		told.remove(actorId);
		told.remove(ticket.getUserId());
		String title = ticket.getReference() + " escalated to level " + target + " (" + levelName(target) + ")";
		told.forEach(id -> notifications.notify(id, "TICKET_ESCALATED", title, text, "/admin/support/" + ticket.getId()));
		return true;
	}

	public static String describe(EscalationTrigger trigger) {
		return switch (trigger) {
			case MANUAL -> "Escalated by an agent";
			case FIRST_RESPONSE_AT_RISK -> "First response target at risk";
			case FIRST_RESPONSE_BREACHED -> "First response target breached";
			case RESOLUTION_BREACHED -> "Resolution target breached";
			case CRITICAL_UNACKNOWLEDGED -> "Critical ticket not acknowledged in time";
			case REPEATED_FOLLOW_UPS -> "The customer followed up repeatedly";
			case REPEATED_REOPENS -> "The ticket was reopened repeatedly";
			case AUTOMATION -> "Escalated by an automation rule";
		};
	}

	public List<UUID> contactIds(int level) {
		return jdbc.queryForList("SELECT user_id FROM support_escalation_contacts WHERE level = ?", UUID.class, level);
	}

	public String levelName(int level) {
		List<String> names = jdbc.queryForList("SELECT name FROM support_escalation_levels WHERE level = ?", String.class, level);
		return names.isEmpty() ? "Level " + level : names.getFirst();
	}

	@Transactional(readOnly = true)
	public List<Level> levels() {
		Map<Integer, List<UUID>> contacts = new HashMap<>();
		jdbc.query("SELECT level, user_id FROM support_escalation_contacts", rs -> {
			contacts.computeIfAbsent(rs.getInt(1), k -> new ArrayList<>()).add(rs.getObject(2, UUID.class));
		});
		Set<UUID> all = new HashSet<>();
		contacts.values().forEach(all::addAll);
		Map<UUID, UserAccount> people = accounts.getAccounts(all);
		return jdbc.query("SELECT level, name, description FROM support_escalation_levels ORDER BY level", (rs, i) -> {
			int level = rs.getInt(1);
			List<Contact> list = contacts.getOrDefault(level, List.of()).stream()
				.map(people::get).filter(java.util.Objects::nonNull)
				.map(p -> new Contact(p.id(), p.firstName() + " " + p.lastName(), p.email()))
				.sorted(java.util.Comparator.comparing(Contact::name))
				.toList();
			return new Level(level, rs.getString(2), rs.getString(3), list);
		});
	}

	/** Replaces a level's name, description and contacts. Caller checks the contacts are staff. */
	@Transactional
	public void updateLevel(UUID actorId, int level, String name, String description, Set<UUID> contacts) {
		int updated = jdbc.update("UPDATE support_escalation_levels SET name = ?, description = ? WHERE level = ?", name.strip(),
				description == null || description.isBlank() ? null : description.strip(), level);
		if (updated == 0) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Unknown escalation level " + level);
		}
		jdbc.update("DELETE FROM support_escalation_contacts WHERE level = ?", level);
		contacts.forEach(id -> jdbc.update("INSERT INTO support_escalation_contacts (level, user_id) VALUES (?, ?)", level, id));
		audit.record(AuditRecord.of(actorId, AuditAction.SUPPORT_SETTINGS_CHANGED, "SUPPORT_ESCALATION_LEVEL", null)
			.withNewValue(Map.of("level", level, "name", name.strip(), "contacts", contacts.stream().map(UUID::toString).sorted().toList())));
	}

	/** A ticket's escalations, oldest first. */
	@Transactional(readOnly = true)
	public List<Escalation> forTicket(UUID ticketId) {
		return list("WHERE e.ticket_id = ? ORDER BY e.created_at", ticketId);
	}

	/** The latest escalations across tickets; {@code openOnly}: tickets not resolved or closed. */
	@Transactional(readOnly = true)
	public List<Escalation> recent(boolean openOnly, int limit) {
		return list((openOnly ? "WHERE t.status NOT IN ('RESOLVED', 'CLOSED') " : "") + "ORDER BY e.created_at DESC LIMIT ?",
				Math.clamp(limit, 1, 500));
	}

	private List<Escalation> list(String tail, Object... args) {
		List<Object[]> rows = jdbc.query("""
				SELECT e.id, e.ticket_id, t.reference, t.subject, e.level, e.previous_level, e.trigger, e.reason, e.escalated_by,
				       e.owner_id, e.created_at, t.status
				FROM support_escalations e JOIN support_tickets t ON t.id = e.ticket_id
				""" + tail, (rs, i) -> new Object[] { rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
				rs.getString(4), rs.getInt(5), rs.getInt(6), rs.getString(7), rs.getString(8), rs.getObject(9, UUID.class),
				rs.getObject(10, UUID.class), rs.getTimestamp(11).toInstant(), rs.getString(12) }, args);
		Set<UUID> people = new HashSet<>();
		rows.forEach(r -> {
			if (r[8] != null) {
				people.add((UUID) r[8]);
			}
			if (r[9] != null) {
				people.add((UUID) r[9]);
			}
		});
		Map<UUID, UserAccount> names = accounts.getAccounts(people);
		return rows.stream().map(r -> new Escalation((UUID) r[0], (UUID) r[1], (String) r[2], (String) r[3], (int) r[4],
				(int) r[5], EscalationTrigger.valueOf((String) r[6]), (String) r[7], (UUID) r[8], name(names, (UUID) r[8]),
				(UUID) r[9], name(names, (UUID) r[9]), (Instant) r[10], TicketStatus.valueOf((String) r[11]))).toList();
	}

	private static String name(Map<UUID, UserAccount> names, UUID id) {
		UserAccount a = id == null ? null : names.get(id);
		return a == null ? null : a.firstName() + " " + a.lastName();
	}

}
