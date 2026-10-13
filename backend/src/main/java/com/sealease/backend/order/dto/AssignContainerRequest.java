package com.sealease.backend.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param containerNumber the number of the container finance assigns, in any format (e.g. SLSU1005013)
 * @param currentLocation optional: where the container is, e.g. "Nhava Sheva (JNPT), Mumbai"
 */
public record AssignContainerRequest(@NotBlank @Size(max = 20) String containerNumber, @Size(max = 120) String currentLocation) {
}
