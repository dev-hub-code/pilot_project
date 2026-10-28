package com.sealease.backend.demo.dto;

import java.time.LocalDate;
import java.util.UUID;

/** A demo account in the list. */
public record DemoAccountSummary(UUID userId, String userCode, String name, String email, LocalDate startsOn,
		LocalDate endsOn, long orders, long members) {
}
