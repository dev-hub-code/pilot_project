package com.sealease.backend.investment.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** @param leaseStartsOn first day of the first rental period */
public record ActivateLeaseRequest(@NotNull LocalDate leaseStartsOn) {
}
