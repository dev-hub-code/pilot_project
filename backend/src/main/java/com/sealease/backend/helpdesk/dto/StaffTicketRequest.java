package com.sealease.backend.helpdesk.dto;

import com.sealease.backend.helpdesk.entity.TicketCategory;
import com.sealease.backend.helpdesk.entity.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * A ticket staff open for a customer (e.g. after a phone call). The message is public: the customer sees it.
 *
 * @param priority   null: the category's default
 * @param assigneeId null: unassigned (or the category's automatic assignment)
 */
public record StaffTicketRequest(
		@NotNull UUID customerId,
		@NotBlank @Size(max = 200) String subject,
		@NotNull TicketCategory category,
		UUID subcategoryId,
		TicketPriority priority,
		UUID assigneeId,
		@NotBlank @Size(max = 8000) String message) {
}
