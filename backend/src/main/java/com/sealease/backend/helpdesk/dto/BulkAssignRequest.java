package com.sealease.backend.helpdesk.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * @param assigneeId the agent, or null to unassign; ignored with {@code strategy}
 * @param strategy   ROUND_ROBIN or LEAST_LOADED to spread the tickets automatically
 */
public record BulkAssignRequest(@NotEmpty @Size(max = 100) List<UUID> ticketIds, UUID assigneeId, String strategy) {

	/** Per ticket: assigned, or why not. */
	public record Result(UUID ticketId, String reference, boolean assigned, UUID assigneeId, String error) {
	}

}
