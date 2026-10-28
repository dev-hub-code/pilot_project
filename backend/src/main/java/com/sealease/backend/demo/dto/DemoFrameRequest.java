package com.sealease.backend.demo.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/** A demo account's time frame, both days included. */
public record DemoFrameRequest(@NotNull LocalDate startsOn, @NotNull LocalDate endsOn) {
}
