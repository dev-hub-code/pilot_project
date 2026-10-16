package com.sealease.backend.reporting.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** The cards the role shows; empty shows none. */
public record RoleDashboardCardsRequest(@NotNull @Size(max = 100) List<@NotNull String> cards) {
}
