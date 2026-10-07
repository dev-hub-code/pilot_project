package com.sealease.backend.helpdesk.dto;

import com.sealease.backend.helpdesk.entity.TicketPriority;
import jakarta.validation.constraints.NotNull;

public record PriorityRequest(@NotNull TicketPriority priority) {
}
