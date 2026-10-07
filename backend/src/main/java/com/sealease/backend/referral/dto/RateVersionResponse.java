package com.sealease.backend.referral.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** @param state IN_FORCE, SCHEDULED, SUPERSEDED or CANCELLED, as of now */
public record RateVersionResponse(UUID id, Instant effectiveFrom, List<BigDecimal> percents, String reason,
		UUID createdBy, Instant createdAt, Instant cancelledAt, String state) {
}
