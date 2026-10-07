package com.sealease.backend.crm.dto;

import com.sealease.backend.crm.entity.ActivityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A note, call, email or meeting logged by staff. Other types are written by the platform. */
public record ActivityRequest(@NotNull ActivityType type, @NotBlank @Size(max = 4000) String body) {
}
