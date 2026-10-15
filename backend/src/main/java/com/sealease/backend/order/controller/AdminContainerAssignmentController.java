package com.sealease.backend.order.controller;

import com.sealease.backend.order.dto.AssignContainerRequest;
import com.sealease.backend.order.dto.ContainerAssignmentQueue;
import com.sealease.backend.order.service.ContainerAssignmentService;
import com.sealease.backend.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Paid investments awaiting a container, and assigning one (which starts the lease and payouts). */
@RestController
@RequestMapping("/api/v1/admin/container-assignments")
@PreAuthorize("hasAuthority('CONTAINER_ASSIGN')")
public class AdminContainerAssignmentController {

	private final ContainerAssignmentService assignments;

	public AdminContainerAssignmentController(ContainerAssignmentService assignments) {
		this.assignments = assignments;
	}

	@GetMapping
	public ContainerAssignmentQueue queue(@PageableDefault(size = 20) Pageable pageable) {
		return assignments.queue(pageable);
	}

	/** Takes the task: only the finance member it is assigned to may assign the container. */
	@PostMapping("/{holdingId}/take")
	public ContainerAssignmentQueue.Pending take(AuthenticatedUser actor, @PathVariable UUID holdingId) {
		return assignments.take(actor.userId(), holdingId);
	}

	@PostMapping("/{holdingId}/release")
	public ContainerAssignmentQueue.Pending release(AuthenticatedUser actor, @PathVariable UUID holdingId) {
		return assignments.release(actor.userId(), holdingId);
	}

	@PostMapping("/{holdingId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void assign(AuthenticatedUser actor, @PathVariable UUID holdingId, @Valid @RequestBody AssignContainerRequest request) {
		assignments.assign(actor.userId(), holdingId, request.containerNumber(), request.currentLocation());
	}

}
