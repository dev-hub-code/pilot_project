package com.sealease.backend.helpdesk.dto;

import com.sealease.backend.helpdesk.entity.SupportTeam;
import jakarta.validation.constraints.NotNull;

/** Hands a ticket to a team. */
public record TeamRequest(@NotNull SupportTeam team) {
}
