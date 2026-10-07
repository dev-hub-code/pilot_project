package com.sealease.backend.crm.controller;

import com.sealease.backend.common.api.PageResponse;
import com.sealease.backend.crm.dto.ActivityRequest;
import com.sealease.backend.crm.dto.AssignRequest;
import com.sealease.backend.crm.dto.Assignee;
import com.sealease.backend.crm.dto.LeadDetail;
import com.sealease.backend.crm.dto.LeadRequest;
import com.sealease.backend.crm.dto.LeadResponse;
import com.sealease.backend.crm.dto.LeadSearchCriteria;
import com.sealease.backend.crm.dto.PipelineStage;
import com.sealease.backend.crm.dto.StageChangeRequest;
import com.sealease.backend.crm.entity.LeadSource;
import com.sealease.backend.crm.entity.LeadStage;
import com.sealease.backend.crm.service.LeadService;
import com.sealease.backend.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/leads")
public class AdminLeadController {

	private final LeadService leads;

	public AdminLeadController(LeadService leads) {
		this.leads = leads;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('LEAD_VIEW')")
	public PageResponse<LeadResponse> search(AuthenticatedUser viewer, @RequestParam(required = false) @Size(max = 100) String q,
			@RequestParam(required = false) LeadStage stage, @RequestParam(required = false) LeadSource source,
			@RequestParam(required = false) String owner, @RequestParam(defaultValue = "false") boolean due,
			@PageableDefault(sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return PageResponse.from(leads.search(viewer(viewer), new LeadSearchCriteria(q, stage, source, owner, due), pageable));
	}

	@GetMapping("/pipeline")
	@PreAuthorize("hasAuthority('LEAD_VIEW')")
	public List<PipelineStage> pipeline(AuthenticatedUser viewer, @RequestParam(required = false) String owner) {
		return leads.pipeline(viewer(viewer), owner);
	}

	@GetMapping("/assignees")
	@PreAuthorize("hasAuthority('LEAD_ASSIGN')")
	public List<Assignee> assignees() {
		return leads.assignees();
	}

	@GetMapping("/{leadId}")
	@PreAuthorize("hasAuthority('LEAD_VIEW')")
	public LeadDetail detail(AuthenticatedUser viewer, @PathVariable UUID leadId) {
		return leads.detail(viewer(viewer), leadId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAuthority('LEAD_CREATE')")
	public LeadDetail create(AuthenticatedUser viewer, @Valid @RequestBody LeadRequest request) {
		return leads.create(viewer(viewer), request);
	}

	@PutMapping("/{leadId}")
	@PreAuthorize("hasAuthority('LEAD_UPDATE')")
	public LeadDetail update(AuthenticatedUser viewer, @PathVariable UUID leadId, @Valid @RequestBody LeadRequest request) {
		return leads.update(viewer(viewer), leadId, request);
	}

	@PostMapping("/{leadId}/stage")
	@PreAuthorize("hasAuthority('LEAD_UPDATE')")
	public LeadDetail stage(AuthenticatedUser viewer, @PathVariable UUID leadId,
			@Valid @RequestBody StageChangeRequest request) {
		return leads.changeStage(viewer(viewer), leadId, request.stage(), request.reason());
	}

	@PostMapping("/{leadId}/activities")
	@PreAuthorize("hasAuthority('LEAD_UPDATE')")
	public LeadDetail activity(AuthenticatedUser viewer, @PathVariable UUID leadId,
			@Valid @RequestBody ActivityRequest request) {
		return leads.logActivity(viewer(viewer), leadId, request);
	}

	@PostMapping("/{leadId}/claim")
	@PreAuthorize("hasAuthority('LEAD_UPDATE')")
	public LeadDetail claim(AuthenticatedUser viewer, @PathVariable UUID leadId) {
		return leads.claim(viewer(viewer), leadId);
	}

	@PostMapping("/{leadId}/assign")
	@PreAuthorize("hasAuthority('LEAD_ASSIGN')")
	public LeadDetail assign(AuthenticatedUser viewer, @PathVariable UUID leadId, @RequestBody AssignRequest request) {
		return leads.assign(viewer(viewer), leadId, request.ownerId());
	}

	private static LeadService.Viewer viewer(AuthenticatedUser user) {
		return new LeadService.Viewer(user.userId(), user.hasPermission("LEAD_ASSIGN"));
	}

}
