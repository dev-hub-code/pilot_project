package com.sealease.backend.role.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * @param version the version the client last read; the update is rejected if the role changed since
 */
public record UpdateRoleRequest(
		@NotBlank @Size(max = 255) String description,
		@NotNull Set<@NotBlank String> permissions,
		@NotNull Long version) {
}
