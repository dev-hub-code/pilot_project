package com.sealease.backend.helpdesk.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.helpdesk.entity.AssignmentStrategy;
import com.sealease.backend.helpdesk.entity.SupportTeam;
import com.sealease.backend.helpdesk.entity.SupportTicket;
import com.sealease.backend.helpdesk.entity.TicketCategory;
import com.sealease.backend.helpdesk.entity.TicketEventType;
import com.sealease.backend.notification.service.NotificationService;
import com.sealease.backend.permission.PermissionCode;
import com.sealease.backend.role.service.UserRoleService;
import com.sealease.backend.user.dto.UserAccount;
import com.sealease.backend.user.entity.UserStatus;
import com.sealease.backend.user.service.UserAccountService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Who may work on tickets, and automatic assignment. Agents are existing staff accounts holding SUPPORT_TICKET_MANAGE
 * (and, for tickets handed to finance, FINANCE_TICKET_HANDLE); a category can narrow that to a pool. Strategies:
 * manual, round-robin and least-loaded (fewest open tickets).
 */
@Service
public class AssignmentService {

	private static final String ENTITY = "SUPPORT_TICKET";

	private final UserRoleService roles;
	private final UserAccountService accounts;
	private final JdbcTemplate jdbc;
	private final TicketHistory history;
	private final NotificationService notifications;
	private final AuditService audit;
	private final Clock clock;

	public AssignmentService(UserRoleService roles, UserAccountService accounts, JdbcTemplate jdbc, TicketHistory history,
			NotificationService notifications, AuditService audit, Clock clock) {
		this.roles = roles;
		this.accounts = accounts;
		this.jdbc = jdbc;
		this.history = history;
		this.notifications = notifications;
		this.audit = audit;
		this.clock = clock;
	}

	/** Who may be assigned a ticket with this team: support agents, and finance members for tickets handed to finance. */
	public Set<UUID> handlers(SupportTeam team) {
		Set<UUID> ids = new HashSet<>(roles.usersWithPermission(PermissionCode.SUPPORT_TICKET_MANAGE.name()));
		if (team == SupportTeam.FINANCE) {
			ids.addAll(roles.usersWithPermission(PermissionCode.FINANCE_TICKET_HANDLE.name()));
		}
		return ids;
	}

	public AssignmentStrategy strategy(TicketCategory category) {
		List<String> found = jdbc.queryForList("SELECT strategy FROM support_assignment_rules WHERE category = ?", String.class,
				category.name());
		return found.isEmpty() ? AssignmentStrategy.MANUAL : AssignmentStrategy.valueOf(found.getFirst());
	}

	/** Agents eligible for the ticket: handlers of its team, in the category's pool if it has one, active, not the requester. */
	public List<UUID> eligible(SupportTicket ticket) {
		Set<UUID> handlers = handlers(ticket.getTeam());
		List<UUID> pool = jdbc.queryForList("SELECT user_id FROM support_assignment_members WHERE category = ?", UUID.class,
				ticket.getCategory().name());
		Set<UUID> candidates = new LinkedHashSet<>(pool.isEmpty() ? handlers : pool.stream().filter(handlers::contains).toList());
		candidates.remove(ticket.getUserId());
		Map<UUID, UserAccount> active = accounts.getAccounts(candidates);
		return candidates.stream()
			.filter(id -> active.containsKey(id) && active.get(id).status() == UserStatus.ACTIVE)
			.sorted()
			.toList();
	}

	/** Open (not resolved or closed) tickets per agent. */
	public Map<UUID, Long> openLoad() {
		Map<UUID, Long> load = new HashMap<>();
		jdbc.query("""
				SELECT assignee_id, count(*) FROM support_tickets
				WHERE assignee_id IS NOT NULL AND status NOT IN ('RESOLVED', 'CLOSED') GROUP BY assignee_id""",
				rs -> {
					load.put(rs.getObject(1, UUID.class), rs.getLong(2));
				});
		return load;
	}

	/** The agent the strategy picks; empty when manual or nobody is eligible. */
	public Optional<UUID> pick(SupportTicket ticket, AssignmentStrategy strategy) {
		List<UUID> candidates = eligible(ticket);
		if (candidates.isEmpty() || strategy == AssignmentStrategy.MANUAL) {
			return Optional.empty();
		}
		List<UUID> last = jdbc.queryForList("SELECT last_assignee_id FROM support_assignment_rules WHERE category = ? AND last_assignee_id IS NOT NULL",
				UUID.class, ticket.getCategory().name());
		UUID previous = last.isEmpty() ? null : last.getFirst();
		if (strategy == AssignmentStrategy.ROUND_ROBIN) {
			return Optional.of(after(candidates, previous));
		}
		Map<UUID, Long> load = openLoad();
		long least = candidates.stream().mapToLong(id -> load.getOrDefault(id, 0L)).min().orElse(0);
		List<UUID> lightest = candidates.stream().filter(id -> load.getOrDefault(id, 0L) == least).toList();
		return Optional.of(after(lightest, previous));
	}

	/** The candidate after {@code previous} in id order, wrapping around: round-robin, also used to break ties. */
	private static UUID after(List<UUID> sorted, UUID previous) {
		if (previous == null) {
			return sorted.getFirst();
		}
		for (UUID id : sorted) {
			if (id.compareTo(previous) > 0) {
				return id;
			}
		}
		return sorted.getFirst();
	}

	/**
	 * Assigns an unassigned ticket by its category's strategy (or {@code fallback} when the category is manual).
	 * Caller's transaction, ticket locked.
	 *
	 * @return the agent, if one was assigned
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<UUID> autoAssign(SupportTicket ticket, AssignmentStrategy fallback) {
		if (ticket.getAssigneeId() != null || !ticket.getStatus().isOpen()) {
			return Optional.empty();
		}
		AssignmentStrategy strategy = strategy(ticket.getCategory());
		if (strategy == AssignmentStrategy.MANUAL) {
			strategy = fallback;
		}
		Optional<UUID> agent = pick(ticket, strategy);
		agent.ifPresent(id -> {
			Instant now = clock.instant();
			ticket.assignTo(id);
			history.record(ticket, null, true, TicketEventType.ASSIGNED, null, null, id, now);
			jdbc.update("UPDATE support_assignment_rules SET last_assignee_id = ?, updated_at = ? WHERE category = ?", id,
					Timestamp.from(now), ticket.getCategory().name());
			audit.record(AuditRecord.of(null, AuditAction.TICKET_ASSIGNED, ENTITY, ticket.getId())
				.withNewValue(Map.of("assigneeId", id.toString(), "automatic", true)));
			notifications.notify(id, "TICKET_ASSIGNED", ticket.getReference() + " was assigned to you", ticket.getSubject(),
					"/admin/support/" + ticket.getId());
		});
		return agent;
	}

	/**
	 * @param resolvedRecently tickets resolved in the last 7 days
	 * @param breached         open tickets past an SLA target
	 */
	public record Workload(UUID agentId, String name, Set<SupportTeam> teams, long open, long inProgress, long waiting,
			long breached, long resolvedRecently) {
	}

	/** Every agent's open work and recent resolutions, busiest first. */
	public List<Workload> workload() {
		Set<UUID> support = new HashSet<>(roles.usersWithPermission(PermissionCode.SUPPORT_TICKET_MANAGE.name()));
		Set<UUID> finance = new HashSet<>(roles.usersWithPermission(PermissionCode.FINANCE_TICKET_HANDLE.name()));
		Set<UUID> agents = new HashSet<>(support);
		agents.addAll(finance);
		Map<UUID, long[]> counts = new HashMap<>();
		jdbc.query("""
				SELECT assignee_id,
				       count(*) FILTER (WHERE status NOT IN ('RESOLVED', 'CLOSED')),
				       count(*) FILTER (WHERE status = 'IN_PROGRESS'),
				       count(*) FILTER (WHERE status IN ('WAITING_ON_CUSTOMER', 'WAITING_ON_INTERNAL')),
				       count(*) FILTER (WHERE status NOT IN ('RESOLVED', 'CLOSED') AND (first_response_breached_at IS NOT NULL
				           OR next_response_breached_at IS NOT NULL OR resolution_breached_at IS NOT NULL)),
				       count(*) FILTER (WHERE resolved_at >= ?)
				FROM support_tickets WHERE assignee_id IS NOT NULL GROUP BY assignee_id""",
				rs -> {
					counts.put(rs.getObject(1, UUID.class), new long[] { rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5), rs.getLong(6) });
				}, Timestamp.from(clock.instant().minus(Duration.ofDays(7))));
		Map<UUID, UserAccount> people = accounts.getAccounts(agents);
		List<Workload> out = new ArrayList<>();
		for (UUID id : agents) {
			UserAccount person = people.get(id);
			if (person == null) {
				continue;
			}
			long[] c = counts.getOrDefault(id, new long[5]);
			Set<SupportTeam> teams = new HashSet<>();
			if (support.contains(id)) {
				teams.add(SupportTeam.SUPPORT);
			}
			if (finance.contains(id)) {
				teams.add(SupportTeam.FINANCE);
			}
			out.add(new Workload(id, person.firstName() + " " + person.lastName(), teams, c[0], c[1], c[2], c[3], c[4]));
		}
		out.sort(Comparator.comparingLong(Workload::open).reversed().thenComparing(Workload::name));
		return out;
	}

}
