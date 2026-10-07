package com.sealease.backend.helpdesk.dto;

import com.sealease.backend.helpdesk.entity.RelatedType;
import com.sealease.backend.helpdesk.entity.TicketCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * A new ticket (multipart form fields; files are sent alongside as {@code files}).
 *
 * @param relatedId with {@code relatedType}: one of the investor's own orders, withdrawals or holdings
 */
public record OpenTicketRequest(
		@NotBlank @Size(max = 200) String subject,
		@NotNull TicketCategory category,
		RelatedType relatedType,
		UUID relatedId,
		@NotBlank @Size(max = 8000) String message) {
}
