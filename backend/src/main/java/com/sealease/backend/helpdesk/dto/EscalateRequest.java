package com.sealease.backend.helpdesk.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * @param level   1-4, or null for the next level
 * @param ownerId optional: the staff member who should own it from here
 */
public record EscalateRequest(@Min(1) @Max(4) Integer level, @NotBlank @Size(max = 500) String reason, UUID ownerId) {
}
