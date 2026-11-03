package com.sealease.backend.helpdesk.config;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.helpdesk.automation.AutomationConditions;
import com.sealease.backend.helpdesk.entity.AssignmentStrategy;
import com.sealease.backend.helpdesk.entity.AutomationAction;
import com.sealease.backend.helpdesk.entity.AutomationRule;
import com.sealease.backend.helpdesk.entity.AutomationTrigger;
import com.sealease.backend.helpdesk.entity.SlaPolicy;
import com.sealease.backend.helpdesk.entity.SupportCategory;
import com.sealease.backend.helpdesk.entity.SupportTeam;
import com.sealease.backend.helpdesk.entity.TicketCategory;
import com.sealease.backend.helpdesk.entity.TicketPriority;
import com.sealease.backend.helpdesk.repository.AutomationRuleRepository;
import com.sealease.backend.helpdesk.repository.SlaPolicyRepository;
import com.sealease.backend.helpdesk.repository.SupportCategoryRepository;
import com.sealease.backend.helpdesk.service.AssignmentService;
import com.sealease.backend.helpdesk.sla.SlaService;
import com.sealease.backend.user.dto.UserAccount;
import com.sealease.backend.user.service.UserAccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Support configuration: categories and subcategories, SLA policies, the business calendar, assignment strategies
 * and automation rules. Every change is audited as SUPPORT_SETTINGS_CHANGED with what changed.
 */
@Service
public class SupportConfigService {

	private static final TypeReference<Map<String, Object>> OBJECT = new TypeReference<>() {
	};

	// ------------------------------------------------------------------------------------------------- views

	public record Category(UUID id, UUID parentId, TicketCategory category, String code, String name, String description,
			TicketPriority defaultPriority, SupportTeam defaultTeam, boolean active, int sortOrder) {

		static Category of(SupportCategory c) {
			return new Category(c.getId(), c.getParentId(), c.getCategory(), c.getCode(), c.getName(), c.getDescription(),
					c.getDefaultPriority(), c.getDefaultTeam(), c.isActive(), c.getSortOrder());
		}
	}

	public record CategoryRequest(UUID parentId, TicketCategory category,
			@Pattern(regexp = "[A-Z][A-Z0-9_]{1,39}") String code, @NotBlank @Size(max = 80) String name,
			@Size(max = 255) String description, TicketPriority defaultPriority, SupportTeam defaultTeam, Boolean active,
			@Min(0) @Max(10000) Integer sortOrder) {

		boolean isActive() {
			return active == null || active;
		}

		int order() {
			return sortOrder == null ? 0 : sortOrder;
		}
	}

	public record Policy(UUID id, String name, TicketPriority priority, TicketCategory category, String customerType,
			int firstResponseMinutes, Integer nextResponseMinutes, int resolutionMinutes, boolean businessHours,
			boolean pauseWhileWaiting, boolean active, Instant updatedAt) {

		static Policy of(SlaPolicy p) {
			return new Policy(p.getId(), p.getName(), p.getPriority(), p.getCategory(), p.getCustomerType(),
					p.getFirstResponseMinutes(), p.getNextResponseMinutes(), p.getResolutionMinutes(), p.isBusinessHours(),
					p.isPauseWhileWaiting(), p.isActive(), p.getUpdatedAt());
		}
	}

	public record PolicyRequest(@NotBlank @Size(max = 100) String name, TicketPriority priority, TicketCategory category,
			@Pattern(regexp = "NETWORK|ONLINE") String customerType, @NotNull @Min(1) @Max(43200) Integer firstResponseMinutes,
			@Min(1) @Max(43200) Integer nextResponseMinutes, @NotNull @Min(1) @Max(129600) Integer resolutionMinutes,
			Boolean businessHours, Boolean pauseWhileWaiting, Boolean active) {
	}

	public record Day(@Min(1) @Max(7) int dayOfWeek, @NotNull LocalTime opensAt, @NotNull LocalTime closesAt) {
	}

	public record Holiday(@NotNull LocalDate day, @NotBlank @Size(max = 100) String name) {
	}

	public record Calendar(String timeZone, List<Day> hours, List<Holiday> holidays) {
	}

	public record CalendarRequest(@NotNull @Size(max = 7) List<@Valid Day> hours, @NotNull @Size(max = 200) List<@Valid Holiday> holidays) {
	}

	public record Member(UUID userId, String name) {
	}

	public record AssignmentRule(TicketCategory category, AssignmentStrategy strategy, List<Member> members,
			Instant updatedAt) {
	}

	public record AssignmentRequest(@NotNull AssignmentStrategy strategy, @NotNull @Size(max = 200) Set<UUID> members) {
	}

	public record Rule(UUID id, String name, AutomationTrigger trigger, Map<String, Object> conditions,
			AutomationAction action, Map<String, Object> parameters, boolean enabled, Instant updatedAt, long runs,
			long failures, Instant lastRunAt) {
	}

	public record RuleRequest(@NotBlank @Size(max = 120) String name, @NotNull AutomationTrigger trigger,
			Map<String, Object> conditions, @NotNull AutomationAction action, Map<String, Object> parameters,
			Boolean enabled) {
	}

	public record Run(UUID id, UUID ruleId, String ruleName, UUID ticketId, String reference, String status, int attempts,
			String lastError, Instant createdAt, Instant updatedAt) {
	}

	private final SupportCategoryRepository categories;
	private final SlaPolicyRepository policies;
	private final AutomationRuleRepository rules;
	private final AssignmentService assignment;
	private final SlaService sla;
	private final UserAccountService accounts;
	private final AuditService audit;
	private final JdbcTemplate jdbc;
	private final JsonMapper json;
	private final Clock clock;

	public SupportConfigService(SupportCategoryRepository categories, SlaPolicyRepository policies,
			AutomationRuleRepository rules, AssignmentService assignment, SlaService sla, UserAccountService accounts,
			AuditService audit, JdbcTemplate jdbc, JsonMapper json, Clock clock) {
		this.categories = categories;
		this.policies = policies;
		this.rules = rules;
		this.assignment = assignment;
		this.sla = sla;
		this.accounts = accounts;
		this.audit = audit;
		this.jdbc = jdbc;
		this.json = json;
		this.clock = clock;
	}

	// -------------------------------------------------------------------------------------------- categories

	@Transactional(readOnly = true)
	public List<Category> categories(boolean activeOnly) {
		return categories.findAllByOrderBySortOrderAscNameAsc().stream()
			.filter(c -> !activeOnly || c.isActive())
			.map(Category::of).toList();
	}

	/** A new subcategory (top-level categories are fixed: one per ticket category). */
	@Transactional
	public Category createCategory(UUID actorId, CategoryRequest r) {
		if (r.parentId() == null) {
			throw invalid("Choose the category this subcategory belongs to");
		}
		SupportCategory parent = categories.findById(r.parentId()).filter(SupportCategory::isTopLevel)
			.orElseThrow(() -> invalid("Subcategories go under a top-level category"));
		String code = r.code() == null || r.code().isBlank() ? parent.getCategory().name() + "_"
				+ r.name().strip().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "") : r.code();
		code = code.length() > 40 ? code.substring(0, 40) : code;
		if (categories.findByCode(code).isPresent()) {
			throw new BusinessException(ErrorCode.CONFLICT, "A category with code " + code + " already exists");
		}
		SupportCategory c = new SupportCategory(parent.getId(), parent.getCategory(), code, r.name().strip());
		c.update(r.name().strip(), blank(r.description()), r.defaultPriority(), r.defaultTeam(), r.isActive(), r.order());
		c = categories.saveAndFlush(c);
		changed(actorId, "SUPPORT_CATEGORY", c.getId(), Map.of("created", code, "name", c.getName()));
		return Category.of(c);
	}

	@Transactional
	public Category updateCategory(UUID actorId, UUID id, CategoryRequest r) {
		SupportCategory c = categories.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category", id));
		if (c.isTopLevel() && !r.isActive()) {
			throw invalid("Top-level categories stay active; deactivate their subcategories instead");
		}
		c.update(r.name().strip(), blank(r.description()), r.defaultPriority(), r.defaultTeam(), r.isActive(), r.order());
		categories.flush();
		Map<String, Object> details = new HashMap<>();
		details.put("code", c.getCode());
		details.put("name", c.getName());
		details.put("active", c.isActive());
		details.put("defaultPriority", c.getDefaultPriority());
		details.put("defaultTeam", c.getDefaultTeam());
		changed(actorId, "SUPPORT_CATEGORY", id, details);
		return Category.of(c);
	}

	// -------------------------------------------------------------------------------------------------- SLA

	@Transactional(readOnly = true)
	public List<Policy> policies() {
		return policies.findAllByOrderByNameAsc().stream().map(Policy::of).toList();
	}

	@Transactional
	public Policy savePolicy(UUID actorId, UUID id, PolicyRequest r) {
		if (r.nextResponseMinutes() != null && r.nextResponseMinutes() > r.resolutionMinutes()
				|| r.firstResponseMinutes() > r.resolutionMinutes()) {
			throw invalid("Response targets must not be longer than the resolution target");
		}
		SlaPolicy p = id == null ? new SlaPolicy(r.name().strip())
				: policies.findById(id).orElseThrow(() -> new ResourceNotFoundException("SLA policy", id));
		p.update(r.name().strip(), r.priority(), r.category(), r.customerType(), r.firstResponseMinutes(),
				r.nextResponseMinutes(), r.resolutionMinutes(), Boolean.TRUE.equals(r.businessHours()),
				Boolean.TRUE.equals(r.pauseWhileWaiting()), r.active() == null || r.active(), actorId);
		p = policies.saveAndFlush(p);
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("name", p.getName());
		details.put("priority", p.getPriority());
		details.put("category", p.getCategory());
		details.put("customerType", p.getCustomerType());
		details.put("firstResponseMinutes", p.getFirstResponseMinutes());
		details.put("nextResponseMinutes", p.getNextResponseMinutes());
		details.put("resolutionMinutes", p.getResolutionMinutes());
		details.put("businessHours", p.isBusinessHours());
		details.put("pauseWhileWaiting", p.isPauseWhileWaiting());
		details.put("active", p.isActive());
		changed(actorId, "SUPPORT_SLA_POLICY", p.getId(), details);
		return Policy.of(p);
	}

	@Transactional(readOnly = true)
	public Calendar calendar() {
		List<Day> hours = jdbc.query("SELECT day_of_week, opens_at, closes_at FROM support_business_hours ORDER BY day_of_week",
				(rs, i) -> new Day(rs.getInt(1), rs.getTime(2).toLocalTime(), rs.getTime(3).toLocalTime()));
		List<Holiday> holidays = jdbc.query("SELECT day, name FROM support_holidays ORDER BY day",
				(rs, i) -> new Holiday(rs.getDate(1).toLocalDate(), rs.getString(2)));
		return new Calendar(sla.calendar().zone().getId(), hours, holidays);
	}

	/** Replaces the working hours and holidays. Targets already set on tickets stay; new targets use the new calendar. */
	@Transactional
	public Calendar saveCalendar(UUID actorId, CalendarRequest r) {
		Set<Integer> days = new HashSet<>();
		for (Day d : r.hours()) {
			if (!days.add(d.dayOfWeek())) {
				throw invalid("Each day of the week can appear once");
			}
			if (!d.closesAt().isAfter(d.opensAt())) {
				throw invalid("Closing time must be after opening time");
			}
		}
		Set<LocalDate> dates = new HashSet<>();
		for (Holiday h : r.holidays()) {
			if (!dates.add(h.day())) {
				throw invalid("Each holiday date can appear once");
			}
		}
		jdbc.update("DELETE FROM support_business_hours");
		r.hours().forEach(d -> jdbc.update("INSERT INTO support_business_hours (day_of_week, opens_at, closes_at) VALUES (?, ?, ?)",
				d.dayOfWeek(), Time.valueOf(d.opensAt()), Time.valueOf(d.closesAt())));
		jdbc.update("DELETE FROM support_holidays");
		r.holidays().forEach(h -> jdbc.update("INSERT INTO support_holidays (day, name) VALUES (?, ?)",
				Date.valueOf(h.day()), h.name().strip()));
		sla.calendarChanged();
		changed(actorId, "SUPPORT_CALENDAR", null, Map.of("hours", r.hours().stream().map(Day::toString).toList(),
				"holidays", r.holidays().stream().map(h -> h.day().toString()).toList()));
		return calendar();
	}

	// ------------------------------------------------------------------------------------------- assignment

	@Transactional(readOnly = true)
	public List<AssignmentRule> assignmentRules() {
		Map<String, List<UUID>> members = new HashMap<>();
		jdbc.query("SELECT category, user_id FROM support_assignment_members", rs -> {
			members.computeIfAbsent(rs.getString(1), k -> new ArrayList<>()).add(rs.getObject(2, UUID.class));
		});
		Set<UUID> all = new HashSet<>();
		members.values().forEach(all::addAll);
		Map<UUID, UserAccount> people = accounts.getAccounts(all);
		return jdbc.query("SELECT category, strategy, updated_at FROM support_assignment_rules ORDER BY category", (rs, i) -> {
			List<Member> list = members.getOrDefault(rs.getString(1), List.of()).stream().map(people::get)
				.filter(java.util.Objects::nonNull).map(a -> new Member(a.id(), a.firstName() + " " + a.lastName()))
				.sorted(java.util.Comparator.comparing(Member::name)).toList();
			return new AssignmentRule(TicketCategory.valueOf(rs.getString(1)), AssignmentStrategy.valueOf(rs.getString(2)), list,
					rs.getTimestamp(3).toInstant());
		});
	}

	/** Sets a category's strategy and (optionally) its pool of agents; an empty pool means every support agent. */
	@Transactional
	public List<AssignmentRule> saveAssignment(UUID actorId, TicketCategory category, AssignmentRequest r) {
		Set<UUID> agents = assignment.handlers(SupportTeam.FINANCE);
		if (!agents.containsAll(r.members())) {
			throw invalid("Pool members must be staff who handle tickets");
		}
		Instant now = clock.instant();
		jdbc.update("""
				INSERT INTO support_assignment_rules (category, strategy, updated_by, updated_at) VALUES (?, ?, ?, ?)
				ON CONFLICT (category) DO UPDATE SET strategy = EXCLUDED.strategy, updated_by = EXCLUDED.updated_by,
				  updated_at = EXCLUDED.updated_at""", category.name(), r.strategy().name(), actorId, Timestamp.from(now));
		jdbc.update("DELETE FROM support_assignment_members WHERE category = ?", category.name());
		r.members().forEach(id -> jdbc.update("INSERT INTO support_assignment_members (category, user_id) VALUES (?, ?)",
				category.name(), id));
		changed(actorId, "SUPPORT_ASSIGNMENT_RULE", null, Map.of("category", category, "strategy", r.strategy(),
				"members", r.members().stream().map(UUID::toString).sorted().toList()));
		return assignmentRules();
	}

	// ------------------------------------------------------------------------------------------- automation

	@Transactional(readOnly = true)
	public List<Rule> rules() {
		Map<UUID, Object[]> stats = new HashMap<>();
		jdbc.query("""
				SELECT rule_id, count(*), count(*) FILTER (WHERE status = 'FAILED'), max(updated_at)
				FROM support_automation_runs GROUP BY rule_id""", rs -> {
			stats.put(rs.getObject(1, UUID.class), new Object[] { rs.getLong(2), rs.getLong(3), rs.getTimestamp(4).toInstant() });
		});
		return rules.findAllByOrderByCreatedAtAsc().stream().map(r -> {
			Object[] s = stats.getOrDefault(r.getId(), new Object[] { 0L, 0L, null });
			return new Rule(r.getId(), r.getName(), r.getTrigger(), json.readValue(r.getConditions(), OBJECT), r.getAction(),
					json.readValue(r.getParameters(), OBJECT), r.isEnabled(), r.getUpdatedAt(), (long) s[0], (long) s[1],
					(Instant) s[2]);
		}).toList();
	}

	@Transactional
	public Rule saveRule(UUID actorId, UUID id, RuleRequest r) {
		String[] checked = AutomationConditions.validate(json, r.trigger(), r.action(), r.conditions(), r.parameters());
		AutomationRule rule = id == null ? new AutomationRule(r.name().strip(), r.trigger(), r.action())
				: rules.findById(id).orElseThrow(() -> new ResourceNotFoundException("Automation rule", id));
		rule.update(r.name().strip(), r.trigger(), checked[0], r.action(), checked[1], r.enabled() == null || r.enabled(), actorId);
		rule = rules.saveAndFlush(rule);
		changed(actorId, "SUPPORT_AUTOMATION_RULE", rule.getId(), Map.of("name", rule.getName(), "trigger", rule.getTrigger(),
				"action", rule.getAction(), "conditions", checked[0], "parameters", checked[1], "enabled", rule.isEnabled()));
		UUID saved = rule.getId();
		return rules().stream().filter(x -> x.id().equals(saved)).findFirst().orElseThrow();
	}

	@Transactional(readOnly = true)
	public List<Run> runs(UUID ruleId, String status, int limit) {
		return jdbc.query("""
				SELECT r.id, r.rule_id, a.name, r.ticket_id, t.reference, r.status, r.attempts, r.last_error, r.created_at, r.updated_at
				FROM support_automation_runs r JOIN support_automation_rules a ON a.id = r.rule_id
				JOIN support_tickets t ON t.id = r.ticket_id
				WHERE (CAST(? AS uuid) IS NULL OR r.rule_id = CAST(? AS uuid)) AND (CAST(? AS varchar) IS NULL OR r.status = CAST(? AS varchar))
				ORDER BY r.updated_at DESC LIMIT ?""",
				(rs, i) -> new Run(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
						rs.getObject(4, UUID.class), rs.getString(5), rs.getString(6), rs.getInt(7), rs.getString(8),
						rs.getTimestamp(9).toInstant(), rs.getTimestamp(10).toInstant()),
				ruleId, ruleId, status, status, Math.clamp(limit, 1, 500));
	}

	// ---------------------------------------------------------------------------------------------- helpers

	private void changed(UUID actorId, String entity, UUID id, Map<String, Object> details) {
		audit.record(AuditRecord.of(actorId, AuditAction.SUPPORT_SETTINGS_CHANGED, entity, id).withNewValue(details));
	}

	private static String blank(String s) {
		return s == null || s.isBlank() ? null : s.strip();
	}

	private static BusinessException invalid(String message) {
		return new BusinessException(ErrorCode.VALIDATION_FAILED, message);
	}

}
