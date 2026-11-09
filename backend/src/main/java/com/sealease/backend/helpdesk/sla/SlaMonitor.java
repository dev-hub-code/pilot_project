package com.sealease.backend.helpdesk.sla;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.helpdesk.automation.AutomationConditions;
import com.sealease.backend.helpdesk.automation.AutomationEngine;
import com.sealease.backend.helpdesk.entity.AutomationRule;
import com.sealease.backend.helpdesk.entity.AutomationTrigger;
import com.sealease.backend.helpdesk.entity.EscalationTrigger;
import com.sealease.backend.helpdesk.entity.SupportTicket;
import com.sealease.backend.helpdesk.entity.TicketEventType;
import com.sealease.backend.helpdesk.entity.TicketPriority;
import com.sealease.backend.helpdesk.escalation.EscalationService;
import com.sealease.backend.helpdesk.repository.SupportTicketRepository;
import com.sealease.backend.helpdesk.service.SupportProperties;
import com.sealease.backend.helpdesk.service.TicketHistory;
import com.sealease.backend.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Watches open tickets against their SLA targets: records breaches (once per target), runs the SLA-percentage,
 * breach and unassigned-for automation rules, applies the built-in escalation checks (critical not acknowledged,
 * repeated follow-ups, repeated reopens) and retries failed automation runs. Idempotent: running it twice changes
 * nothing more, so it is safe on several instances (each ticket is locked while changed).
 */
@Component
public class SlaMonitor {

	private static final Logger log = LoggerFactory.getLogger(SlaMonitor.class);
	private static final int BATCH = 2000;

	public record Result(int breaches, int warnings, int escalations, int automationRuns, int retried) {
	}

	private final SupportTicketRepository tickets;
	private final SlaService sla;
	private final AutomationEngine automation;
	private final EscalationService escalations;
	private final NotificationService notifications;
	private final TicketHistory history;
	private final AuditService audit;
	private final SupportProperties properties;
	private final JdbcTemplate jdbc;
	private final JsonMapper json;
	private final TransactionTemplate transactions;
	private final Clock clock;

	public SlaMonitor(SupportTicketRepository tickets, SlaService sla, AutomationEngine automation,
			EscalationService escalations, NotificationService notifications,
			TicketHistory history, AuditService audit, SupportProperties properties, JdbcTemplate jdbc, JsonMapper json,
			TransactionTemplate transactions, Clock clock) {
		this.tickets = tickets;
		this.sla = sla;
		this.automation = automation;
		this.escalations = escalations;
		this.notifications = notifications;
		this.history = history;
		this.audit = audit;
		this.properties = properties;
		this.jdbc = jdbc;
		this.json = json;
		this.transactions = transactions;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${app.support.sla-monitor-interval:1m}", initialDelayString = "${app.support.sla-monitor-initial-delay:1m}")
	void scheduled() {
		Result result = run();
		if (result.breaches() + result.escalations() + result.automationRuns() > 0) {
			log.info("Support SLA monitor: {}", result);
		}
	}

	public Result run() {
		Instant now = clock.instant();
		int breaches = recordBreaches(now);
		int[] warnings = percentRules(now);
		int unassigned = unassignedRules(now);
		int escalated = builtInEscalations(now);
		int retried = automation.retryFailed();
		return new Result(breaches, warnings[0], escalated, warnings[1] + unassigned, retried);
	}

	// ----------------------------------------------------------------------------------------------- breaches

	private int recordBreaches(Instant now) {
		Timestamp at = Timestamp.from(now);
		List<Object[]> due = jdbc.query("""
				SELECT id, 'FIRST_RESPONSE' FROM support_tickets
				WHERE status NOT IN ('RESOLVED', 'CLOSED') AND first_responded_at IS NULL AND first_response_breached_at IS NULL
				  AND first_response_due_at < ?
				UNION ALL
				SELECT id, 'NEXT_RESPONSE' FROM support_tickets
				WHERE status NOT IN ('RESOLVED', 'CLOSED') AND next_response_due_at < ? AND next_response_breached_at IS NULL
				UNION ALL
				SELECT id, 'RESOLUTION' FROM support_tickets
				WHERE status NOT IN ('RESOLVED', 'CLOSED') AND sla_paused_at IS NULL AND resolution_breached_at IS NULL
				  AND resolution_due_at < ?
				LIMIT ?""", (rs, i) -> new Object[] { rs.getObject(1, UUID.class), rs.getString(2) }, at, at, at, BATCH);
		int count = 0;
		for (Object[] row : due) {
			UUID id = (UUID) row[0];
			String metric = (String) row[1];
			Instant dueAt = transactions.execute(s -> breach(id, metric, now));
			if (dueAt != null) {
				count++;
				automation.runAll(AutomationTrigger.SLA_BREACHED, id, "breach:" + dueAt.toEpochMilli() + "|" + metric, metric);
			}
		}
		return count;
	}

	/** Marks one target breached (if it still is); returns its due time, or null when nothing changed. */
	private Instant breach(UUID ticketId, String metric, Instant now) {
		SupportTicket t = tickets.findByIdForUpdate(ticketId).orElse(null);
		if (t == null || !t.getStatus().isOpen()) {
			return null;
		}
		Instant due;
		switch (metric) {
			case "FIRST_RESPONSE" -> {
				if (t.getFirstRespondedAt() != null || t.getFirstResponseBreachedAt() != null || !now.isAfter(t.getFirstResponseDueAt())) {
					return null;
				}
				due = t.getFirstResponseDueAt();
				t.firstResponseBreached(now);
			}
			case "NEXT_RESPONSE" -> {
				if (t.getNextResponseDueAt() == null || t.getNextResponseBreachedAt() != null || !now.isAfter(t.getNextResponseDueAt())) {
					return null;
				}
				due = t.getNextResponseDueAt();
				t.nextResponseBreached(now);
			}
			default -> {
				if (t.getResolutionDueAt() == null || t.getSlaPausedAt() != null || t.getResolutionBreachedAt() != null
						|| !now.isAfter(t.getResolutionDueAt())) {
					return null;
				}
				due = t.getResolutionDueAt();
				t.resolutionBreached(now);
			}
		}
		history.record(t, null, true, TicketEventType.SLA_BREACHED, null, metric, null, now);
		audit.record(AuditRecord.of(null, AuditAction.TICKET_SLA_BREACHED, "SUPPORT_TICKET", t.getId())
			.withNewValue(Map.of("metric", metric, "dueAt", due.toString())));
		String title = t.getReference() + ": " + metric.replace('_', ' ').toLowerCase() + " target breached";
		Set<UUID> told = new HashSet<>();
		if (t.getAssigneeId() != null) {
			told.add(t.getAssigneeId());
		}
		else {
			told.addAll(escalations.contactIds(1));
		}
		told.remove(t.getUserId());
		told.forEach(id -> notifications.notify(id, "TICKET_SLA_BREACHED", title, t.getSubject(), "/platform/support/" + t.getId()));
		tickets.flush();
		return due;
	}

	// -------------------------------------------------------------------------------------------- percent rules

	/** @return {warnings recorded, rule runs} */
	private int[] percentRules(Instant now) {
		List<AutomationRule> rules = automation.enabled(AutomationTrigger.SLA_PERCENT);
		if (rules.isEmpty()) {
			return new int[2];
		}
		List<UUID> open = jdbc.queryForList("""
				SELECT id FROM support_tickets WHERE status NOT IN ('RESOLVED', 'CLOSED')
				  AND ((first_responded_at IS NULL AND first_response_breached_at IS NULL)
				    OR (next_response_due_at IS NOT NULL AND next_response_breached_at IS NULL)
				    OR (resolution_due_at IS NOT NULL AND resolution_breached_at IS NULL AND sla_paused_at IS NULL))
				ORDER BY created_at LIMIT ?""", UUID.class, BATCH);
		int warnings = 0;
		int runs = 0;
		for (UUID id : open) {
			SupportTicket t = tickets.findById(id).orElse(null);
			if (t == null) {
				continue;
			}
			SlaService.View view = sla.view(t);
			for (SlaService.Target target : List.of(view.firstResponse(), view.nextResponse(), view.resolution())) {
				if (target.percentUsed() == null || target.state() == SlaService.State.BREACHED
						|| target.state() == SlaService.State.PAUSED) {
					continue;
				}
				for (AutomationRule rule : rules) {
					Integer threshold = AutomationConditions.parse(json, rule.getConditions()).percent();
					if (threshold == null || target.percentUsed() < threshold) {
						continue;
					}
					String occurrence = "pct" + threshold + ":" + target.dueAt().toEpochMilli() + "|" + target.metric();
					AutomationEngine.Outcome outcome = automation.run(rule, id, occurrence, target.metric());
					if (outcome != AutomationEngine.Outcome.DUPLICATE) {
						runs++;
						String metric = target.metric();
						transactions.executeWithoutResult(s -> tickets.findByIdForUpdate(id).ifPresent(locked ->
								history.record(locked, null, true, TicketEventType.SLA_WARNING, threshold + "%", metric, null, now)));
						warnings++;
					}
				}
			}
		}
		return new int[] { warnings, runs };
	}

	private int unassignedRules(Instant now) {
		int runs = 0;
		for (AutomationRule rule : automation.enabled(AutomationTrigger.UNASSIGNED_FOR)) {
			Integer minutes = AutomationConditions.parse(json, rule.getConditions()).minutes();
			if (minutes == null) {
				continue;
			}
			List<UUID> waiting = jdbc.queryForList("""
					SELECT id FROM support_tickets WHERE status NOT IN ('RESOLVED', 'CLOSED') AND assignee_id IS NULL
					  AND created_at < ? ORDER BY created_at LIMIT ?""", UUID.class,
					Timestamp.from(now.minus(Duration.ofMinutes(minutes))), BATCH);
			for (UUID id : waiting) {
				if (automation.run(rule, id, "unassigned", null) != AutomationEngine.Outcome.DUPLICATE) {
					runs++;
				}
			}
		}
		return runs;
	}

	// ------------------------------------------------------------------------------------ built-in escalation

	private int builtInEscalations(Instant now) {
		SupportProperties.Escalation config = properties.escalation();
		int count = 0;
		List<UUID> critical = jdbc.queryForList("""
				SELECT id FROM support_tickets WHERE status NOT IN ('RESOLVED', 'CLOSED') AND priority = ?
				  AND acknowledged_at IS NULL AND escalation_level = 0 AND created_at < ? LIMIT ?""", UUID.class,
				TicketPriority.URGENT.name(), Timestamp.from(now.minus(config.criticalAcknowledgement())), BATCH);
		for (UUID id : critical) {
			count += escalateOnce(id, EscalationTrigger.CRITICAL_UNACKNOWLEDGED) ? 1 : 0;
		}
		for (UUID id : jdbc.queryForList("""
				SELECT id FROM support_tickets WHERE status NOT IN ('RESOLVED', 'CLOSED') AND customer_follow_ups >= ? LIMIT ?""",
				UUID.class, config.followUps(), BATCH)) {
			count += escalateOnce(id, EscalationTrigger.REPEATED_FOLLOW_UPS) ? 1 : 0;
		}
		for (UUID id : jdbc.queryForList("""
				SELECT id FROM support_tickets WHERE status NOT IN ('RESOLVED', 'CLOSED') AND reopen_count >= ? LIMIT ?""",
				UUID.class, config.reopens(), BATCH)) {
			count += escalateOnce(id, EscalationTrigger.REPEATED_REOPENS) ? 1 : 0;
		}
		return count;
	}

	/** Escalates a ticket one level for this reason, unless it already was for it. */
	private boolean escalateOnce(UUID ticketId, EscalationTrigger trigger) {
		return Boolean.TRUE.equals(transactions.execute(s -> {
			SupportTicket t = tickets.findByIdForUpdate(ticketId).orElse(null);
			Boolean done = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM support_escalations WHERE ticket_id = ? AND trigger = ?)",
					Boolean.class, ticketId, trigger.name());
			if (t == null || Boolean.TRUE.equals(done)) {
				return false;
			}
			boolean escalated = escalations.escalate(t, null, trigger, null, null, null);
			tickets.flush();
			return escalated;
		}));
	}

}
