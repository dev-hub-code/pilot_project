package com.sealease.backend.role.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateRoleRequest(
		@NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$",
				message = "must be UPPER_SNAKE_CASE, 2-64 characters") String name,
		@NotBlank @Size(max = 255) String description,
		@NotNull Set<@NotBlank String> permissions) {
}
