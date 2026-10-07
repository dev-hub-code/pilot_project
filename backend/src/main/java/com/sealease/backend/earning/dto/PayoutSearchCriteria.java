package com.sealease.backend.earning.dto;

import com.sealease.backend.earning.entity.PayoutStatus;

import java.time.LocalDate;
import java.util.UUID;

/** @param dueBy only payouts due on or before this day */
public record PayoutSearchCriteria(PayoutStatus status, UUID userId, UUID holdingId, LocalDate dueBy) {
}
