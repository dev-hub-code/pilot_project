package com.sealease.backend.helpdesk.sla;

import com.sealease.backend.common.time.DisplayTimeZone;
import com.sealease.backend.helpdesk.entity.SlaPolicy;
import com.sealease.backend.helpdesk.entity.SupportTicket;
import com.sealease.backend.helpdesk.entity.TicketStatus;
import com.sealease.backend.helpdesk.repository.SlaPolicyRepository;
import com.sealease.backend.helpdesk.service.SupportProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * SLA targets for tickets. The most specific active policy (priority, category, customer type) sets the first-response,
 * next-response and resolution targets, in business time on the support calendar or in calendar time. While a ticket
 * waits on the customer its resolution clock stops (when the policy says so); the time stopped is added to the target.
 * Without a matching policy the configured first-response target per priority applies and there is no resolution target.
 */
@Service
public class SlaService {

	/** Share of a target used from which a ticket is "at risk". */
	public static final int AT_RISK_PERCENT = 75;

	public enum State {
		/** Met in time. */
		MET,
		/** Missed (met late, or still open past the target). */
		BREACHED,
		AT_RISK,
		ON_TRACK,
		PAUSED,
		/** No target. */
		NONE
	}

	/** One SLA metric of a ticket. */
	public record Target(String metric, Instant dueAt, Instant metAt, State state, Integer percentUsed, Long minutesLeft) {
	}

	public record View(UUID policyId, String policyName, boolean businessHours, boolean paused, Target firstResponse,
			Target nextResponse, Target resolution, State overall) {
	}

	private final SlaPolicyRepository policies;
	private final SupportProperties properties;
	private final JdbcTemplate jdbc;
	private final DisplayTimeZone display;
	private final Clock clock;
	private final AtomicReference<BusinessCalendar> calendar = new AtomicReference<>();

	public SlaService(SlaPolicyRepository policies, SupportProperties properties, JdbcTemplate jdbc, DisplayTimeZone display,
			Clock clock) {
		this.policies = policies;
		this.properties = properties;
		this.jdbc = jdbc;
		this.display = display;
		this.clock = clock;
	}

	// ------------------------------------------------------------------------------------------------- calendar

	public BusinessCalendar calendar() {
		BusinessCalendar current = calendar.get();
		if (current == null) {
			Map<DayOfWeek, BusinessCalendar.Hours> hours = new EnumMap<>(DayOfWeek.class);
			jdbc.query("SELECT day_of_week, opens_at, closes_at FROM support_business_hours", rs -> {
				hours.put(DayOfWeek.of(rs.getInt(1)), new BusinessCalendar.Hours(rs.getTime(2).toLocalTime(),
						rs.getTime(3).toLocalTime()));
			});
			Set<LocalDate> holidays = new HashSet<>(jdbc.queryForList("SELECT day FROM support_holidays", LocalDate.class));
			current = new BusinessCalendar(display.zone(), hours, holidays);
			calendar.set(current);
		}
		return current;
	}

	/** After the business hours or holidays change. */
	public void calendarChanged() {
		calendar.set(null);
	}

	// ------------------------------------------------------------------------------------------------- policies

	public String customerType(UUID userId) {
		Boolean network = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM network_investors WHERE user_id = ?)",
				Boolean.class, userId);
		return Boolean.TRUE.equals(network) ? "NETWORK" : "ONLINE";
	}

	public Optional<SlaPolicy> policyFor(SupportTicket ticket) {
		String type = customerType(ticket.getUserId());
		return policies.findByActiveTrue().stream()
			.filter(p -> p.specificity(ticket.getPriority(), ticket.getCategory(), type) >= 0)
			.max(Comparator.<SlaPolicy>comparingInt(p -> p.specificity(ticket.getPriority(), ticket.getCategory(), type))
				.thenComparing(Comparator.comparingInt(SlaPolicy::getFirstResponseMinutes).reversed()));
	}

	private Optional<SlaPolicy> policyOf(SupportTicket ticket) {
		return ticket.getSlaPolicyId() == null ? Optional.empty() : policies.findById(ticket.getSlaPolicyId());
	}

	/**
	 * Sets the ticket's targets from its policy, counted from when it was created (plus any time the resolution clock
	 * was stopped). A first response already given keeps its target.
	 */
	public void applyTargets(SupportTicket ticket, Instant createdAt) {
		Optional<SlaPolicy> policy = policyFor(ticket);
		Instant firstDue;
		Instant resolutionDue = null;
		if (policy.isPresent()) {
			SlaPolicy p = policy.get();
			firstDue = calendar().plus(createdAt, p.getFirstResponseMinutes(), p.isBusinessHours());
			resolutionDue = calendar().plus(createdAt, (long) p.getResolutionMinutes() + ticket.getSlaPausedMinutes(),
					p.isBusinessHours());
		}
		else {
			firstDue = createdAt.plus(properties.responseTargets().of(ticket.getPriority()));
		}
		if (ticket.getFirstRespondedAt() != null && ticket.getFirstResponseDueAt() != null) {
			firstDue = ticket.getFirstResponseDueAt();
		}
		ticket.applySla(policy.map(SlaPolicy::getId).orElse(null), firstDue, resolutionDue);
	}

	/** When staff owe the next reply after the customer wrote at {@code now}; null without a next-response target. */
	public Instant nextResponseDue(SupportTicket ticket, Instant now) {
		return policyOf(ticket).filter(p -> p.getNextResponseMinutes() != null)
			.map(p -> calendar().plus(now, p.getNextResponseMinutes(), p.isBusinessHours()))
			.orElse(null);
	}

	/**
	 * Stops or restarts the resolution clock as the ticket starts or stops waiting on the customer.
	 *
	 * @return SLA_PAUSED, SLA_RESUMED, or empty when nothing changed
	 */
	public Optional<String> statusChanged(SupportTicket ticket, TicketStatus previous, Instant now) {
		Optional<SlaPolicy> policy = policyOf(ticket).filter(SlaPolicy::isPauseWhileWaiting);
		if (policy.isEmpty() || ticket.getResolutionDueAt() == null) {
			return Optional.empty();
		}
		boolean waitingNow = ticket.getStatus() == TicketStatus.WAITING_ON_CUSTOMER;
		if (waitingNow && ticket.getSlaPausedAt() == null && ticket.getStatus().isOpen()) {
			ticket.pauseSla(now);
			return Optional.of("SLA_PAUSED");
		}
		if (!waitingNow && ticket.getSlaPausedAt() != null) {
			SlaPolicy p = policy.get();
			int paused = (int) Math.min(Integer.MAX_VALUE, calendar().minutesBetween(ticket.getSlaPausedAt(), now, p.isBusinessHours()));
			Instant due = calendar().plus(ticket.getCreatedAt() == null ? now : ticket.getCreatedAt(),
					(long) p.getResolutionMinutes() + ticket.getSlaPausedMinutes() + paused, p.isBusinessHours());
			ticket.resumeSla(paused, due);
			return Optional.of("SLA_RESUMED");
		}
		return Optional.empty();
	}

	// ---------------------------------------------------------------------------------------------------- state

	public View view(SupportTicket ticket) {
		return view(ticket, policyOf(ticket), clock.instant());
	}

	public View view(SupportTicket ticket, Optional<SlaPolicy> policy, Instant now) {
		boolean business = policy.map(SlaPolicy::isBusinessHours).orElse(false);
		Instant created = ticket.getCreatedAt() == null ? now : ticket.getCreatedAt();
		Target first = target("FIRST_RESPONSE", created, ticket.getFirstResponseDueAt(), ticket.getFirstRespondedAt(),
				ticket.getFirstResponseBreachedAt(), false, business, ticket, now);
		Target next = ticket.getNextResponseDueAt() == null ? new Target("NEXT_RESPONSE", null, null, State.NONE, null, null)
				: target("NEXT_RESPONSE", ticket.getLastMessageAt(), ticket.getNextResponseDueAt(), null,
						ticket.getNextResponseBreachedAt(), false, business, ticket, now);
		Instant resolvedAt = ticket.getResolvedAt() != null ? ticket.getResolvedAt()
				: ticket.getStatus() == TicketStatus.CLOSED ? ticket.getClosedAt() : null;
		Target resolution = target("RESOLUTION", created, ticket.getResolutionDueAt(), resolvedAt,
				ticket.getResolutionBreachedAt(), ticket.getSlaPausedAt() != null, business, ticket, now);
		State overall = worst(List.of(first.state(), next.state(), resolution.state()));
		return new View(ticket.getSlaPolicyId(), policy.map(SlaPolicy::getName).orElse(null), business,
				ticket.getSlaPausedAt() != null, first, next, resolution, overall);
	}

	private Target target(String metric, Instant start, Instant due, Instant metAt, Instant breachedAt, boolean paused,
			boolean business, SupportTicket ticket, Instant now) {
		if (due == null) {
			return new Target(metric, null, metAt, State.NONE, null, null);
		}
		if (metAt != null) {
			return new Target(metric, due, metAt, metAt.isAfter(due) ? State.BREACHED : State.MET, null, null);
		}
		if (!ticket.getStatus().isOpen()) {
			return new Target(metric, due, null, breachedAt != null ? State.BREACHED : State.NONE, null, null);
		}
		Instant at = paused && ticket.getSlaPausedAt() != null ? ticket.getSlaPausedAt() : now;
		long total = Math.max(1, calendar().minutesBetween(start, due, business));
		long used = calendar().minutesBetween(start, at, business);
		int percent = (int) Math.min(999, used * 100 / total);
		long left = at.isAfter(due) ? -calendar().minutesBetween(due, at, business) : calendar().minutesBetween(at, due, business);
		State state = at.isAfter(due) || breachedAt != null ? State.BREACHED
				: paused ? State.PAUSED
				: percent >= AT_RISK_PERCENT ? State.AT_RISK : State.ON_TRACK;
		return new Target(metric, due, null, state, percent, left);
	}

	private static State worst(List<State> states) {
		for (State s : List.of(State.BREACHED, State.AT_RISK, State.PAUSED, State.ON_TRACK, State.MET)) {
			if (states.contains(s)) {
				return s;
			}
		}
		return State.NONE;
	}

	/** Business minutes to first response and to resolution, on the ticket's policy calendar. */
	public Integer[] businessMinutes(SupportTicket ticket) {
		boolean business = policyOf(ticket).map(SlaPolicy::isBusinessHours).orElse(false);
		Instant created = ticket.getCreatedAt();
		Integer first = ticket.getFirstRespondedAt() == null ? null
				: (int) calendar().minutesBetween(created, ticket.getFirstRespondedAt(), business);
		Instant end = ticket.getResolvedAt() != null ? ticket.getResolvedAt() : ticket.getClosedAt();
		Integer resolution = end == null ? null
				: (int) Math.max(0, calendar().minutesBetween(created, end, business) - ticket.getSlaPausedMinutes());
		return new Integer[] { first, resolution };
	}

	public Instant now() {
		return clock.instant();
	}

}
