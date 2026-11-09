package com.sealease.backend.helpdesk.automation;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.helpdesk.entity.AssignmentStrategy;
import com.sealease.backend.helpdesk.entity.AutomationRule;
import com.sealease.backend.helpdesk.entity.AutomationTrigger;
import com.sealease.backend.helpdesk.entity.EscalationTrigger;
import com.sealease.backend.helpdesk.entity.SupportTicket;
import com.sealease.backend.helpdesk.entity.TicketEventType;
import com.sealease.backend.helpdesk.entity.TicketPriority;
import com.sealease.backend.helpdesk.escalation.EscalationService;
import com.sealease.backend.helpdesk.repository.AutomationRuleRepository;
import com.sealease.backend.helpdesk.repository.SupportTicketRepository;
import com.sealease.backend.helpdesk.service.AssignmentService;
import com.sealease.backend.helpdesk.service.TicketHistory;
import com.sealease.backend.helpdesk.sla.SlaService;
import com.sealease.backend.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Runs the enabled automation rules for a trigger on a ticket. Each rule runs once per ticket and occurrence (the
 * dedupe key is unique in {@code support_automation_runs}), in its own transaction after the change that triggered it
 * has committed, so a failing rule never undoes the user's action. Failed runs are retried by the SLA monitor up to
 * {@link #MAX_ATTEMPTS} times. Actions are a fixed set of operations; nothing configurable is executed as code.
 */
@Service
public class AutomationEngine {

	public static final int MAX_ATTEMPTS = 3;
	private static final Logger log = LoggerFactory.getLogger(AutomationEngine.class);

	public enum Outcome { SUCCEEDED, SKIPPED, FAILED, DUPLICATE }

	private final AutomationRuleRepository rules;
	private final SupportTicketRepository tickets;
	private final AssignmentService assignment;
	private final EscalationService escalations;
	private final NotificationService notifications;
	private final SlaService sla;
	private final TicketHistory history;
	private final AuditService audit;
	private final JdbcTemplate jdbc;
	private final JsonMapper json;
	private final TransactionTemplate transactions;
	private final Clock clock;

	public AutomationEngine(AutomationRuleRepository rules, SupportTicketRepository tickets, AssignmentService assignment,
			EscalationService escalations, NotificationService notifications, SlaService sla, TicketHistory history,
			AuditService audit, JdbcTemplate jdbc, JsonMapper json, PlatformTransactionManager transactionManager, Clock clock) {
		this.rules = rules;
		this.tickets = tickets;
		this.assignment = assignment;
		this.escalations = escalations;
		this.notifications = notifications;
		this.sla = sla;
		this.history = history;
		this.audit = audit;
		this.jdbc = jdbc;
		this.json = json;
		// Always a new transaction: runs start after the triggering transaction committed (its resources may still be bound).
		this.transactions = new TransactionTemplate(transactionManager);
		this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.clock = clock;
	}

	/**
	 * Runs the trigger's rules once the current transaction commits (straight away outside one).
	 *
	 * @param occurrence what makes this firing distinct (e.g. the time the ticket was resolved), for idempotency
	 */
	public void fire(AutomationTrigger trigger, UUID ticketId, String occurrence) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					runAll(trigger, ticketId, occurrence, null);
				}
			});
		}
		else {
			runAll(trigger, ticketId, occurrence, null);
		}
	}

	/** Runs the rules now (from the SLA monitor, outside a transaction). */
	public void runAll(AutomationTrigger trigger, UUID ticketId, String occurrence, String metric) {
		for (AutomationRule rule : rules.findByTriggerAndEnabledTrue(trigger)) {
			run(rule, ticketId, occurrence, metric);
		}
	}

	public List<AutomationRule> enabled(AutomationTrigger trigger) {
		return rules.findByTriggerAndEnabledTrue(trigger);
	}

	/** One rule on one ticket, at most once per occurrence. Never throws. */
	public Outcome run(AutomationRule rule, UUID ticketId, String occurrence, String metric) {
		String key = rule.getId() + ":" + ticketId + ":" + occurrence;
		if (key.length() > 200) {
			key = key.substring(0, 200);
		}
		String dedupe = key;
		try {
			Instant now = clock.instant();
			Integer inserted = transactions.execute(s -> jdbc.update("""
					INSERT INTO support_automation_runs (id, rule_id, ticket_id, dedupe_key, status, attempts, created_at, updated_at)
					VALUES (gen_random_uuid(), ?, ?, ?, 'FAILED', 0, ?, ?) ON CONFLICT (dedupe_key) DO NOTHING""",
					rule.getId(), ticketId, dedupe, Timestamp.from(now), Timestamp.from(now)));
			if (inserted == null || inserted == 0) {
				return Outcome.DUPLICATE;
			}
			return attempt(rule, ticketId, dedupe, metric);
		}
		catch (RuntimeException ex) {
			log.warn("Automation rule {} could not start on ticket {}", rule.getId(), ticketId, ex);
			return Outcome.FAILED;
		}
	}

	/** Retries failed runs that have attempts left. */
	public int retryFailed() {
		List<Object[]> failed = jdbc.query("""
				SELECT r.rule_id, r.ticket_id, r.dedupe_key FROM support_automation_runs r
				JOIN support_automation_rules a ON a.id = r.rule_id
				WHERE r.status = 'FAILED' AND r.attempts < ? AND a.enabled ORDER BY r.updated_at LIMIT 200""",
				(rs, i) -> new Object[] { rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3) }, MAX_ATTEMPTS);
		int retried = 0;
		for (Object[] f : failed) {
			AutomationRule rule = rules.findById((UUID) f[0]).orElse(null);
			if (rule != null) {
				attempt(rule, (UUID) f[1], (String) f[2], metricOf((String) f[2]));
				retried++;
			}
		}
		return retried;
	}

	/** SLA occurrences end in "|METRIC" so retries know which target the run was for. */
	private static String metricOf(String dedupe) {
		int bar = dedupe.lastIndexOf('|');
		return bar < 0 ? null : dedupe.substring(bar + 1);
	}

	private Outcome attempt(AutomationRule rule, UUID ticketId, String dedupe, String metric) {
		Outcome outcome;
		String error = null;
		try {
			outcome = transactions.execute(s -> apply(rule, ticketId, metric));
		}
		catch (RuntimeException ex) {
			log.warn("Automation rule {} failed on ticket {}", rule.getId(), ticketId, ex);
			outcome = Outcome.FAILED;
			error = ex.getClass().getSimpleName() + (ex.getMessage() == null ? "" : ": " + ex.getMessage());
		}
		String status = outcome == Outcome.FAILED ? "FAILED" : outcome.name();
		String message = error == null ? null : error.substring(0, Math.min(500, error.length()));
		transactions.executeWithoutResult(s -> jdbc.update(
				"UPDATE support_automation_runs SET status = ?, attempts = attempts + 1, last_error = ?, updated_at = ? WHERE dedupe_key = ?",
				status, message, Timestamp.from(clock.instant()), dedupe));
		return outcome;
	}

	private Outcome apply(AutomationRule rule, UUID ticketId, String metric) {
		SupportTicket ticket = tickets.findByIdForUpdate(ticketId).orElse(null);
		if (ticket == null || !AutomationConditions.parse(json, rule.getConditions()).matches(ticket, metric)) {
			return Outcome.SKIPPED;
		}
		AutomationConditions.Parameters p = AutomationConditions.parseParameters(json, rule.getParameters());
		Instant now = clock.instant();
		String link = "/platform/support/" + ticket.getId();
		String title = ticket.getReference() + ": " + rule.getName();
		boolean done = switch (rule.getAction()) {
			case NOTIFY_ASSIGNEE -> {
				if (ticket.getAssigneeId() == null) {
					yield false;
				}
				notifications.notify(ticket.getAssigneeId(), "TICKET_AUTOMATION", title, ticket.getSubject(), link);
				yield true;
			}
			case NOTIFY_TEAM -> {
				Set<UUID> team = new HashSet<>(assignment.handlers(ticket.getTeam()));
				team.remove(ticket.getUserId());
				team.forEach(id -> notifications.notify(id, "TICKET_AUTOMATION", title, ticket.getSubject(), link));
				yield !team.isEmpty();
			}
			case NOTIFY_ESCALATION_CONTACTS -> {
				Set<UUID> contacts = new HashSet<>(escalations.contactIds(Math.max(1, ticket.getEscalationLevel())));
				contacts.remove(ticket.getUserId());
				contacts.forEach(id -> notifications.notify(id, "TICKET_AUTOMATION", title, ticket.getSubject(), link));
				yield !contacts.isEmpty();
			}
			case AUTO_ASSIGN -> assignment.autoAssign(ticket,
					p.strategy() == null ? AssignmentStrategy.LEAST_LOADED : p.strategy()).isPresent();
			case ESCALATE -> escalations.escalate(ticket, p.level(), trigger(rule, metric),
					rule.getName(), null, null);
			case SET_PRIORITY -> {
				TicketPriority previous = ticket.getPriority();
				if (previous == p.priority()) {
					yield false;
				}
				ticket.reprioritise(p.priority(), ticket.getFirstResponseDueAt());
				sla.applyTargets(ticket, ticket.getCreatedAt());
				history.record(ticket, null, true, TicketEventType.PRIORITY_CHANGED, previous, p.priority(), null, now);
				audit.record(AuditRecord.of(null, AuditAction.TICKET_PRIORITY_CHANGED, "SUPPORT_TICKET", ticket.getId())
					.withOldValue(Map.of("priority", previous))
					.withNewValue(Map.of("priority", p.priority(), "rule", rule.getName())));
				yield true;
			}
			case REQUEST_FEEDBACK -> {
				// Turns on the rating prompt on the customer's ticket; the resolution notice they just got links there.
				if (ticket.getFeedbackRequestedAt() != null || ticket.getStatus().isOpen()) {
					yield false;
				}
				ticket.feedbackRequested(now);
				yield true;
			}
			case RECORD_RESOLUTION_METRICS -> {
				Integer[] minutes = sla.businessMinutes(ticket);
				ticket.recordMetrics(minutes[0], minutes[1]);
				yield true;
			}
		};
		tickets.flush();
		return done ? Outcome.SUCCEEDED : Outcome.SKIPPED;
	}

	private static EscalationTrigger trigger(AutomationRule rule, String metric) {
		if (rule.getTrigger() == AutomationTrigger.SLA_BREACHED) {
			return "RESOLUTION".equals(metric) ? EscalationTrigger.RESOLUTION_BREACHED : EscalationTrigger.FIRST_RESPONSE_BREACHED;
		}
		if (rule.getTrigger() == AutomationTrigger.SLA_PERCENT && "FIRST_RESPONSE".equals(metric)) {
			return EscalationTrigger.FIRST_RESPONSE_AT_RISK;
		}
		return EscalationTrigger.AUTOMATION;
	}

}
