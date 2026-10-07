package com.sealease.backend.helpdesk.dto;

import com.sealease.backend.helpdesk.entity.TicketStatus;
import jakarta.validation.constraints.NotNull;

/** Staff resolve or close a ticket. */
public record StatusRequest(@NotNull TicketStatus status) {
}
