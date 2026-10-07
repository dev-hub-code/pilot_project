package com.sealease.backend.role.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

/** The complete set of roles the user should hold afterwards. */
public record AssignRolesRequest(@NotNull @Size(max = 20) Set<@NotNull String> roles) {
}
