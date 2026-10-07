package com.sealease.backend.role.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

/** @param roles at least one staff role; the creator must hold every permission they grant */
public record CreateStaffRequest(
		@NotBlank @Email @Size(max = 254) String email,
		@NotBlank @Size(max = 100) String firstName,
		@NotBlank @Size(max = 100) String lastName,
		@NotEmpty @Size(max = 20) Set<@NotBlank String> roles) {
}
