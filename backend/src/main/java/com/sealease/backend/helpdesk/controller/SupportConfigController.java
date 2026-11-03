package com.sealease.backend.helpdesk.controller;

import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.helpdesk.config.SupportConfigService;
import com.sealease.backend.helpdesk.entity.SupportTeam;
import com.sealease.backend.helpdesk.entity.TicketCategory;
import com.sealease.backend.helpdesk.escalation.EscalationService;
import com.sealease.backend.helpdesk.service.AssignmentService;
import com.sealease.backend.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Support configuration: SUPPORT_CONFIG_MANAGE (new, unassigned by default) or SYSTEM_SETTINGS_MANAGE (which super
 * admins already hold). Support staff may read it.
 */
@RestController
@Validated
@RequestMapping("/api/v1/admin/support/config")
@PreAuthorize("hasAnyAuthority('SUPPORT_CONFIG_MANAGE', 'SYSTEM_SETTINGS_MANAGE')")
public class SupportConfigController {

	private static final String READ = "hasAnyAuthority('SUPPORT_CONFIG_MANAGE', 'SYSTEM_SETTINGS_MANAGE', 'SUPPORT_TICKET_VIEW')";

	public record LevelRequest(@NotBlank @Size(max = 60) String name, @Size(max = 255) String description,
			@NotNull @Size(max = 50) Set<UUID> contacts) {
	}

	private final SupportConfigService config;
	private final EscalationService escalations;
	private final AssignmentService assignment;

	public SupportConfigController(SupportConfigService config, EscalationService escalations, AssignmentService assignment) {
		this.config = config;
		this.escalations = escalations;
		this.assignment = assignment;
	}

	@GetMapping("/categories")
	@PreAuthorize(READ)
	public List<SupportConfigService.Category> categories() {
		return config.categories(false);
	}

	@PostMapping("/categories")
	@ResponseStatus(HttpStatus.CREATED)
	public SupportConfigService.Category createCategory(AuthenticatedUser actor,
			@Valid @RequestBody SupportConfigService.CategoryRequest request) {
		return config.createCategory(actor.userId(), request);
	}

	@PutMapping("/categories/{id}")
	public SupportConfigService.Category updateCategory(AuthenticatedUser actor, @PathVariable UUID id,
			@Valid @RequestBody SupportConfigService.CategoryRequest request) {
		return config.updateCategory(actor.userId(), id, request);
	}

	@GetMapping("/sla-policies")
	@PreAuthorize(READ)
	public List<SupportConfigService.Policy> policies() {
		return config.policies();
	}

	@PostMapping("/sla-policies")
	@ResponseStatus(HttpStatus.CREATED)
	public SupportConfigService.Policy createPolicy(AuthenticatedUser actor, @Valid @RequestBody SupportConfigService.PolicyRequest request) {
		return config.savePolicy(actor.userId(), null, request);
	}

	@PutMapping("/sla-policies/{id}")
	public SupportConfigService.Policy updatePolicy(AuthenticatedUser actor, @PathVariable UUID id,
			@Valid @RequestBody SupportConfigService.PolicyRequest request) {
		return config.savePolicy(actor.userId(), id, request);
	}

	@GetMapping("/calendar")
	@PreAuthorize(READ)
	public SupportConfigService.Calendar calendar() {
		return config.calendar();
	}

	@PutMapping("/calendar")
	public SupportConfigService.Calendar saveCalendar(AuthenticatedUser actor, @Valid @RequestBody SupportConfigService.CalendarRequest request) {
		return config.saveCalendar(actor.userId(), request);
	}

	@GetMapping("/assignment")
	@PreAuthorize(READ)
	public List<SupportConfigService.AssignmentRule> assignment() {
		return config.assignmentRules();
	}

	@PutMapping("/assignment/{category}")
	public List<SupportConfigService.AssignmentRule> saveAssignment(AuthenticatedUser actor, @PathVariable TicketCategory category,
			@Valid @RequestBody SupportConfigService.AssignmentRequest request) {
		return config.saveAssignment(actor.userId(), category, request);
	}

	@GetMapping("/escalation-levels")
	@PreAuthorize(READ)
	public List<EscalationService.Level> levels() {
		return escalations.levels();
	}

	/** Contacts must be staff who handle tickets (support or finance): existing accounts, no role changes. */
	@PutMapping("/escalation-levels/{level}")
	public List<EscalationService.Level> saveLevel(AuthenticatedUser actor, @PathVariable @Min(1) @Max(4) int level,
			@Valid @RequestBody LevelRequest request) {
		Set<UUID> staff = assignment.handlers(SupportTeam.FINANCE);
		if (!staff.containsAll(request.contacts())) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Escalation contacts must be staff who handle tickets");
		}
		escalations.updateLevel(actor.userId(), level, request.name(), request.description(), request.contacts());
		return escalations.levels();
	}

	@GetMapping("/automation-rules")
	@PreAuthorize(READ)
	public List<SupportConfigService.Rule> rules() {
		return config.rules();
	}

	@PostMapping("/automation-rules")
	@ResponseStatus(HttpStatus.CREATED)
	public SupportConfigService.Rule createRule(AuthenticatedUser actor, @Valid @RequestBody SupportConfigService.RuleRequest request) {
		return config.saveRule(actor.userId(), null, request);
	}

	@PutMapping("/automation-rules/{id}")
	public SupportConfigService.Rule updateRule(AuthenticatedUser actor, @PathVariable UUID id,
			@Valid @RequestBody SupportConfigService.RuleRequest request) {
		return config.saveRule(actor.userId(), id, request);
	}

	@GetMapping("/automation-runs")
	@PreAuthorize(READ)
	public List<SupportConfigService.Run> runs(@RequestParam(required = false) UUID ruleId,
			@RequestParam(required = false) String status, @RequestParam(defaultValue = "100") int limit) {
		return config.runs(ruleId, status, limit);
	}

}
