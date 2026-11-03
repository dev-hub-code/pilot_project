package com.sealease.backend.helpdesk.dto;

import com.sealease.backend.helpdesk.entity.TicketCategory;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CategoryRequest(@NotNull TicketCategory category, UUID subcategoryId) {
}
