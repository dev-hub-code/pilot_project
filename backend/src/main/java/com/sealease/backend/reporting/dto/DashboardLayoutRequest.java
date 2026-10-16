package com.sealease.backend.reporting.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** The cards to show, in order, and the ones taken off; keys like "investors" or "support-pending". */
public record DashboardLayoutRequest(
		@NotNull @Size(max = 60) List<@NotNull @Pattern(regexp = "[a-z0-9-]{1,40}") String> cards,
		@NotNull @Size(max = 60) List<@NotNull @Pattern(regexp = "[a-z0-9-]{1,40}") String> hidden) {
}
