package com.sealease.backend.helpdesk.automation;

import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.helpdesk.entity.AssignmentStrategy;
import com.sealease.backend.helpdesk.entity.AutomationAction;
import com.sealease.backend.helpdesk.entity.AutomationTrigger;
import com.sealease.backend.helpdesk.entity.SupportTeam;
import com.sealease.backend.helpdesk.entity.SupportTicket;
import com.sealease.backend.helpdesk.entity.TicketCategory;
import com.sealease.backend.helpdesk.entity.TicketChannel;
import com.sealease.backend.helpdesk.entity.TicketPriority;
import com.sealease.backend.helpdesk.entity.TicketStatus;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The structured conditions and parameters of an automation rule: a closed set of keys with plain values, checked when
 * the rule is saved. Nothing in them is ever evaluated as code or SQL.
 *
 * <p>Conditions (all optional, all must hold): {@code priorities}, {@code categories}, {@code teams}, {@code statuses},
 * {@code channels} (lists of names), {@code unassigned} (boolean), {@code maxEscalationLevel} (0-3), {@code metrics}
 * (FIRST_RESPONSE, NEXT_RESPONSE, RESOLUTION, for SLA triggers), {@code minutes} (UNASSIGNED_FOR, required),
 * {@code percent} (SLA_PERCENT, 1-99, required).
 *
 * <p>Parameters: {@code priority} (SET_PRIORITY, required), {@code level} (ESCALATE, 1-4: escalate straight to it),
 * {@code strategy} (AUTO_ASSIGN: ROUND_ROBIN or LEAST_LOADED, default LEAST_LOADED).
 */
public record AutomationConditions(Set<TicketPriority> priorities, Set<TicketCategory> categories, Set<SupportTeam> teams,
		Set<TicketStatus> statuses, Set<TicketChannel> channels, Boolean unassigned, Integer maxEscalationLevel,
		Set<String> metrics, Integer minutes, Integer percent) {

	public static final Set<String> METRICS = Set.of("FIRST_RESPONSE", "NEXT_RESPONSE", "RESOLUTION");
	private static final Set<String> CONDITION_KEYS = Set.of("priorities", "categories", "teams", "statuses", "channels",
			"unassigned", "maxEscalationLevel", "metrics", "minutes", "percent");
	private static final TypeReference<Map<String, Object>> OBJECT = new TypeReference<>() {
	};

	public record Parameters(TicketPriority priority, Integer level, AssignmentStrategy strategy) {
	}

	/** Whether the ticket (and SLA metric, for SLA triggers) meets every condition. */
	public boolean matches(SupportTicket t, String metric) {
		return (priorities == null || priorities.contains(t.getPriority()))
				&& (categories == null || categories.contains(t.getCategory()))
				&& (teams == null || teams.contains(t.getTeam()))
				&& (statuses == null || statuses.contains(t.getStatus()))
				&& (channels == null || channels.contains(t.getChannel()))
				&& (unassigned == null || unassigned == (t.getAssigneeId() == null))
				&& (maxEscalationLevel == null || t.getEscalationLevel() <= maxEscalationLevel)
				&& (metrics == null || metric == null || metrics.contains(metric));
	}

	// ----------------------------------------------------------------------------------------------- parsing

	public static AutomationConditions parse(JsonMapper json, String text) {
		return of(map(json, text));
	}

	public static Parameters parseParameters(JsonMapper json, String text) {
		return parameters(map(json, text));
	}

	private static Map<String, Object> map(JsonMapper json, String text) {
		Map<String, Object> m = text == null || text.isBlank() ? Map.of() : json.readValue(text, OBJECT);
		return m == null ? Map.of() : m;
	}

	/** Checks a rule before it is saved; returns its conditions and parameters as normalised JSON. */
	public static String[] validate(JsonMapper json, AutomationTrigger trigger, AutomationAction action,
			Map<String, Object> conditions, Map<String, Object> parameters) {
		Map<String, Object> c = conditions == null ? Map.of() : conditions;
		Map<String, Object> p = parameters == null ? Map.of() : parameters;
		AutomationConditions parsed = of(c);
		if (trigger == AutomationTrigger.UNASSIGNED_FOR && parsed.minutes() == null) {
			throw invalid("An \"unassigned for\" rule needs minutes");
		}
		if (trigger == AutomationTrigger.SLA_PERCENT && parsed.percent() == null) {
			throw invalid("An SLA percentage rule needs a percent");
		}
		Parameters params = parameters(p);
		if (action == AutomationAction.SET_PRIORITY && params.priority() == null) {
			throw invalid("Setting a priority needs the priority");
		}
		return new String[] { json.writeValueAsString(normalise(c)), json.writeValueAsString(normalise(p)) };
	}

	private static Map<String, Object> normalise(Map<String, Object> m) {
		Map<String, Object> out = new LinkedHashMap<>();
		m.entrySet().stream().filter(e -> e.getValue() != null).sorted(Map.Entry.comparingByKey())
			.forEach(e -> out.put(e.getKey(), e.getValue()));
		return out;
	}

	private static AutomationConditions of(Map<String, Object> m) {
		for (String key : m.keySet()) {
			if (!CONDITION_KEYS.contains(key)) {
				throw invalid("Unknown condition: " + key);
			}
		}
		Set<String> metrics = strings(m, "metrics");
		if (metrics != null && !METRICS.containsAll(metrics)) {
			throw invalid("metrics must be among " + METRICS);
		}
		return new AutomationConditions(enums(m, "priorities", TicketPriority.class), enums(m, "categories", TicketCategory.class),
				enums(m, "teams", SupportTeam.class), enums(m, "statuses", TicketStatus.class),
				enums(m, "channels", TicketChannel.class), bool(m, "unassigned"), integer(m, "maxEscalationLevel", 0, 3),
				metrics, integer(m, "minutes", 1, 60 * 24 * 30), integer(m, "percent", 1, 99));
	}

	private static Parameters parameters(Map<String, Object> m) {
		for (String key : m.keySet()) {
			if (!Set.of("priority", "level", "strategy").contains(key)) {
				throw invalid("Unknown parameter: " + key);
			}
		}
		TicketPriority priority = m.get("priority") == null ? null : constant(TicketPriority.class, m.get("priority"), "priority");
		AssignmentStrategy strategy = m.get("strategy") == null ? null : constant(AssignmentStrategy.class, m.get("strategy"), "strategy");
		if (strategy == AssignmentStrategy.MANUAL) {
			throw invalid("Automatic assignment needs ROUND_ROBIN or LEAST_LOADED");
		}
		return new Parameters(priority, integer(m, "level", 1, 4), strategy);
	}

	private static <E extends Enum<E>> Set<E> enums(Map<String, Object> m, String key, Class<E> type) {
		Object v = m.get(key);
		if (v == null) {
			return null;
		}
		if (!(v instanceof List<?> list) || list.isEmpty()) {
			throw invalid(key + " must be a non-empty list");
		}
		EnumSet<E> out = EnumSet.noneOf(type);
		list.forEach(item -> out.add(constant(type, item, key)));
		return out;
	}

	private static Set<String> strings(Map<String, Object> m, String key) {
		Object v = m.get(key);
		if (v == null) {
			return null;
		}
		if (!(v instanceof List<?> list) || list.isEmpty() || !list.stream().allMatch(String.class::isInstance)) {
			throw invalid(key + " must be a non-empty list of names");
		}
		return Set.copyOf(list.stream().map(String.class::cast).toList());
	}

	private static <E extends Enum<E>> E constant(Class<E> type, Object value, String key) {
		if (value instanceof String s) {
			try {
				return Enum.valueOf(type, s);
			}
			catch (IllegalArgumentException ignored) {
				// reported below
			}
		}
		throw invalid("Invalid " + key + ": " + value);
	}

	private static Boolean bool(Map<String, Object> m, String key) {
		Object v = m.get(key);
		if (v == null) {
			return null;
		}
		if (!(v instanceof Boolean b)) {
			throw invalid(key + " must be true or false");
		}
		return b;
	}

	private static Integer integer(Map<String, Object> m, String key, int min, int max) {
		Object v = m.get(key);
		if (v == null) {
			return null;
		}
		if (!(v instanceof Integer || v instanceof Long || v instanceof Short) || ((Number) v).longValue() < min
				|| ((Number) v).longValue() > max) {
			throw invalid(key + " must be a whole number from " + min + " to " + max);
		}
		return ((Number) v).intValue();
	}

	private static BusinessException invalid(String message) {
		return new BusinessException(ErrorCode.VALIDATION_FAILED, message);
	}

}
