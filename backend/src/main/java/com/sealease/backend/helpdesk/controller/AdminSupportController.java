package com.sealease.backend.helpdesk.controller;

import com.sealease.backend.common.api.PageResponse;
import com.sealease.backend.document.web.DocumentResponses;
import com.sealease.backend.security.AuthenticatedUser;
import com.sealease.backend.helpdesk.dto.Agent;
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

	public AdminSupportController(SupportService support) {
		this.support = support;
	}

	@GetMapping("/tickets")
	@PreAuthorize("hasAnyAuthority('SUPPORT_TICKET_VIEW', 'FINANCE_TICKET_HANDLE')")
	public PageResponse<TicketResponse> search(AuthenticatedUser viewer, @RequestParam(required = false) @Size(max = 100) String q,
			@RequestParam(required = false) TicketStatus status, @RequestParam(required = false) TicketPriority priority,
			@RequestParam(required = false) String assignee, @RequestParam(defaultValue = "false") boolean overdue,
			@RequestParam(required = false) SupportTeam team,
			@PageableDefault(sort = "lastMessageAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return PageResponse.from(support.search(viewer.userId(), financeOnly(viewer, "SUPPORT_TICKET_VIEW"),
				new TicketSearchCriteria(q, status, priority, assignee, overdue, team), pageable));
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
