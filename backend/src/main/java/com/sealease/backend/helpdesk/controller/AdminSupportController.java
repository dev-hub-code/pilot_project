package com.sealease.backend.helpdesk.controller;

import com.sealease.backend.common.api.PageResponse;
import com.sealease.backend.document.web.DocumentResponses;
import com.sealease.backend.security.AuthenticatedUser;
import com.sealease.backend.helpdesk.analytics.SupportAnalyticsService;
import com.sealease.backend.helpdesk.config.SupportConfigService;
import com.sealease.backend.helpdesk.dto.Agent;
import com.sealease.backend.helpdesk.dto.BulkAssignRequest;
import com.sealease.backend.helpdesk.dto.CategoryRequest;
import com.sealease.backend.helpdesk.dto.EscalateRequest;
import com.sealease.backend.helpdesk.dto.StaffTicketRequest;
import com.sealease.backend.helpdesk.entity.TicketCategory;
import com.sealease.backend.helpdesk.entity.TicketChannel;
import com.sealease.backend.helpdesk.escalation.EscalationService;
import com.sealease.backend.helpdesk.service.AssignmentService;
import com.sealease.backend.helpdesk.service.FeedbackService;
import com.sealease.backend.helpdesk.service.SupportWorkspaceService;
import com.sealease.backend.user.service.UserAccountService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import java.time.LocalDate;
import com.sealease.backend.helpdesk.dto.AssignTicketRequest;
import com.sealease.backend.helpdesk.dto.PriorityRequest;
import com.sealease.backend.helpdesk.dto.StatusRequest;
import com.sealease.backend.helpdesk.dto.TeamRequest;
import com.sealease.backend.helpdesk.dto.TicketDetail;
import com.sealease.backend.helpdesk.dto.TicketResponse;
import com.sealease.backend.helpdesk.dto.TicketSearchCriteria;
import com.sealease.backend.helpdesk.entity.SupportTeam;
import com.sealease.backend.helpdesk.entity.TicketPriority;
import com.sealease.backend.helpdesk.entity.TicketStatus;
import com.sealease.backend.helpdesk.service.SupportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/v1/admin/support")
public class AdminSupportController {

	/**
	 * Staff who handle only the tickets handed to finance (FINANCE_TICKET_HANDLE without the support team's
	 * permission): they see those tickets alone, and work on one only once they have assigned it to themselves.
	 */
	private static boolean financeOnly(AuthenticatedUser user, String supportPermission) {
		return !user.hasPermission(supportPermission);
	}

	private final SupportService support;
	private final SupportWorkspaceService workspace;
	private final AssignmentService assignment;
	private final EscalationService escalations;
	private final FeedbackService feedback;
	private final SupportAnalyticsService analytics;
	private final SupportConfigService config;
	private final UserAccountService accounts;

	public AdminSupportController(SupportService support, SupportWorkspaceService workspace, AssignmentService assignment,
			EscalationService escalations, FeedbackService feedback, SupportAnalyticsService analytics,
			SupportConfigService config, UserAccountService accounts) {
		this.support = support;
		this.workspace = workspace;
		this.assignment = assignment;
		this.escalations = escalations;
		this.feedback = feedback;
		this.analytics = analytics;
		this.config = config;
		this.accounts = accounts;
	}

	@GetMapping("/tickets")
	@PreAuthorize("hasAnyAuthority('SUPPORT_TICKET_VIEW', 'FINANCE_TICKET_HANDLE')")
	public PageResponse<TicketResponse> search(AuthenticatedUser viewer, @RequestParam(required = false) @Size(max = 100) String q,
			@RequestParam(required = false) TicketStatus status, @RequestParam(required = false) TicketPriority priority,
			@RequestParam(required = false) String assignee, @RequestParam(defaultValue = "false") boolean overdue,
			@RequestParam(required = false) SupportTeam team, @RequestParam(required = false) TicketCategory category,
			@RequestParam(required = false) UUID subcategoryId, @RequestParam(required = false) TicketChannel channel,
			@RequestParam(required = false) @Size(max = 20) String view, @RequestParam(required = false) UUID customerId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@PageableDefault(sort = "lastMessageAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return PageResponse.from(support.search(viewer.userId(), financeOnly(viewer, "SUPPORT_TICKET_VIEW"),
				new TicketSearchCriteria(q, status, priority, assignee, overdue, team, category, subcategoryId, channel, view,
						customerId, from, to), pageable));
	}

	/** Opens a ticket for a customer (phone call, email); the message is public. */
	@PostMapping("/tickets")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAnyAuthority('SUPPORT_TICKET_MANAGE', 'SUPPORT_CREATE')")
	public TicketDetail create(AuthenticatedUser agent, @Valid @RequestBody StaffTicketRequest request) {
		return support.openForCustomer(agent.userId(), request);
	}

	/** Customers to open a ticket for, by name, email or customer code. */
	@GetMapping("/customers")
	@PreAuthorize("hasAnyAuthority('SUPPORT_TICKET_MANAGE', 'SUPPORT_CREATE')")
	public List<CustomerOption> customers(AuthenticatedUser agent, @RequestParam @NotBlank @Size(min = 2, max = 100) String q) {
		boolean contact = agent.hasPermission("INVESTOR_CONTACT_VIEW") || agent.hasPermission("USER_VIEW");
		return accounts.getAccounts(accounts.searchIds(q.strip(), 20)).values().stream()
			.filter(a -> !a.id().equals(agent.userId()))
			.map(a -> new CustomerOption(a.id(), a.firstName() + " " + a.lastName(), a.userCode(), contact ? a.email() : null))
			.sorted(java.util.Comparator.comparing(CustomerOption::name))
			.toList();
	}

	public record CustomerOption(UUID id, String name, String userCode, String email) {
	}

	/** Workspace side panel: SLA, escalations, feedback and the customer's context (as far as the viewer may see). */
	@GetMapping("/tickets/{ticketId}/context")
	@PreAuthorize("hasAnyAuthority('SUPPORT_TICKET_VIEW', 'FINANCE_TICKET_HANDLE')")
	public SupportWorkspaceService.Context context(AuthenticatedUser viewer, @PathVariable UUID ticketId) {
		return workspace.context(ticketId, financeOnly(viewer, "SUPPORT_TICKET_VIEW"), viewer::hasPermission);
	}

	@PostMapping("/tickets/{ticketId}/category")
	@PreAuthorize("hasAuthority('SUPPORT_TICKET_MANAGE')")
	public TicketDetail category(AuthenticatedUser agent, @PathVariable UUID ticketId, @Valid @RequestBody CategoryRequest request) {
		return support.setCategory(agent.userId(), ticketId, request.category(), request.subcategoryId());
	}

	@PostMapping("/tickets/{ticketId}/escalate")
	@PreAuthorize("hasAnyAuthority('SUPPORT_TICKET_MANAGE', 'SUPPORT_ESCALATE')")
	public TicketDetail escalate(AuthenticatedUser agent, @PathVariable UUID ticketId, @Valid @RequestBody EscalateRequest request) {
		return support.escalate(agent.userId(), false, ticketId, request.level(), request.reason(), request.ownerId());
	}

	@PostMapping("/tickets/bulk-assign")
	@PreAuthorize("hasAnyAuthority('SUPPORT_TICKET_MANAGE', 'SUPPORT_ASSIGN')")
	public List<BulkAssignRequest.Result> bulkAssign(AuthenticatedUser agent, @Valid @RequestBody BulkAssignRequest request) {
		return support.bulkAssign(agent.userId(), request);
	}

	@GetMapping("/workload")
	@PreAuthorize("hasAnyAuthority('SUPPORT_TICKET_VIEW', 'SUPPORT_ANALYTICS_VIEW')")
	public List<AssignmentService.Workload> workload() {
		return assignment.workload();
	}

	@GetMapping("/escalations")
	@PreAuthorize("hasAnyAuthority('SUPPORT_TICKET_VIEW', 'SUPPORT_ANALYTICS_VIEW')")
	public List<EscalationService.Escalation> escalations(@RequestParam(defaultValue = "true") boolean openOnly,
			@RequestParam(defaultValue = "100") int limit) {
		return escalations.recent(openOnly, limit);
	}

	@GetMapping("/escalation-levels")
	@PreAuthorize("hasAnyAuthority('SUPPORT_TICKET_VIEW', 'SUPPORT_CONFIG_MANAGE', 'SYSTEM_SETTINGS_MANAGE')")
	public List<EscalationService.Level> escalationLevels() {
		return escalations.levels();
	}

	@GetMapping("/dashboard")
	@PreAuthorize("hasAnyAuthority('SUPPORT_TICKET_VIEW', 'SUPPORT_ANALYTICS_VIEW', 'REPORT_VIEW')")
	public SupportAnalyticsService.Dashboard dashboard(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return analytics.dashboard(from, to);
	}

	@GetMapping("/feedback")
	@PreAuthorize("hasAnyAuthority('SUPPORT_TICKET_VIEW', 'SUPPORT_ANALYTICS_VIEW', 'REPORT_VIEW')")
	public List<FeedbackService.FeedbackRow> feedback(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) Integer maxRating, @RequestParam(defaultValue = "200") int limit) {
		var zone = java.time.ZoneId.of(analytics.dashboardZone());
		java.time.Instant start = from == null ? java.time.Instant.EPOCH : from.atStartOfDay(zone).toInstant();
		java.time.Instant end = to == null ? java.time.Instant.now().plusSeconds(86400) : to.plusDays(1).atStartOfDay(zone).toInstant();
		return feedback.list(start, end, maxRating, limit);
	}

	/** Active categories and subcategories, for the ticket forms. */
	@GetMapping("/categories")
	@PreAuthorize("hasAnyAuthority('SUPPORT_TICKET_VIEW', 'FINANCE_TICKET_HANDLE')")
	public List<SupportConfigService.Category> categories() {
		return config.categories(true);
	}

	@GetMapping("/tickets/{ticketId}")
	@PreAuthorize("hasAnyAuthority('SUPPORT_TICKET_VIEW', 'FINANCE_TICKET_HANDLE')")
	public TicketDetail detail(AuthenticatedUser viewer, @PathVariable UUID ticketId) {
		return support.staffDetail(financeOnly(viewer, "SUPPORT_TICKET_VIEW"), ticketId);
	}

	@GetMapping("/tickets/{ticketId}/attachments/{attachmentId}")
	@PreAuthorize("hasAnyAuthority('SUPPORT_TICKET_VIEW', 'FINANCE_TICKET_HANDLE')")
	public ResponseEntity<byte[]> attachment(AuthenticatedUser viewer, @PathVariable UUID ticketId,
			@PathVariable UUID attachmentId) {
		return DocumentResponses.inline(support.staffAttachment(viewer.userId(), financeOnly(viewer, "SUPPORT_TICKET_VIEW"),
				ticketId, attachmentId));
	}

	@GetMapping("/agents")
	@PreAuthorize("hasAuthority('SUPPORT_TICKET_MANAGE')")
	public List<Agent> agents() {
		return support.agents();
	}

	@PostMapping(path = "/tickets/{ticketId}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasAnyAuthority('SUPPORT_TICKET_MANAGE', 'FINANCE_TICKET_HANDLE')")
	public TicketDetail reply(AuthenticatedUser agent, @PathVariable UUID ticketId,
			@RequestParam @NotBlank @Size(max = 8000) String body, @RequestParam(defaultValue = "false") boolean internal,
			@RequestPart(value = "files", required = false) List<MultipartFile> files) {
		return support.staffReply(agent.userId(), financeOnly(agent, "SUPPORT_TICKET_MANAGE"), ticketId, body, internal, files);
	}

	@PostMapping("/tickets/{ticketId}/status")
	@PreAuthorize("hasAnyAuthority('SUPPORT_TICKET_MANAGE', 'FINANCE_TICKET_HANDLE')")
	public TicketDetail status(AuthenticatedUser agent, @PathVariable UUID ticketId, @Valid @RequestBody StatusRequest request) {
		return support.setStatus(agent.userId(), financeOnly(agent, "SUPPORT_TICKET_MANAGE"), ticketId, request.status());
	}

	@PostMapping("/tickets/{ticketId}/priority")
	@PreAuthorize("hasAnyAuthority('SUPPORT_TICKET_MANAGE', 'FINANCE_TICKET_HANDLE')")
	public TicketDetail priority(AuthenticatedUser agent, @PathVariable UUID ticketId,
			@Valid @RequestBody PriorityRequest request) {
		return support.setPriority(agent.userId(), financeOnly(agent, "SUPPORT_TICKET_MANAGE"), ticketId,
				request.priority());
	}

	@PostMapping("/tickets/{ticketId}/assign")
	@PreAuthorize("hasAnyAuthority('SUPPORT_TICKET_MANAGE', 'FINANCE_TICKET_HANDLE')")
	public TicketDetail assign(AuthenticatedUser agent, @PathVariable UUID ticketId, @RequestBody AssignTicketRequest request) {
		return support.assign(agent.userId(), financeOnly(agent, "SUPPORT_TICKET_MANAGE"), ticketId,
				request.assigneeId());
	}

	/** Hands the ticket to finance, or back to support; finance only hands back a ticket it is working on. */
	@PostMapping("/tickets/{ticketId}/team")
	@PreAuthorize("hasAnyAuthority('SUPPORT_TICKET_MANAGE', 'FINANCE_TICKET_HANDLE')")
	public TicketDetail team(AuthenticatedUser agent, @PathVariable UUID ticketId, @Valid @RequestBody TeamRequest request) {
		return support.handTo(agent.userId(), financeOnly(agent, "SUPPORT_TICKET_MANAGE"), ticketId, request.team());
	}

}
