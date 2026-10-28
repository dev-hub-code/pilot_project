package com.sealease.backend.demo.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** Marks an investor as a demo account, with the time frame its dummy figures cover. */
public record DemoAccountRequest(@NotNull UUID userId, @NotNull LocalDate startsOn, @NotNull LocalDate endsOn) {
}
