package com.sealease.backend.earning.dto;

import com.sealease.backend.earning.dto.LeaseRentResponse.LeaseRentStatus;

import java.util.UUID;

/** Each filter is optional. */
public record LeaseSearchCriteria(LeaseRentStatus status, UUID userId, UUID holdingId) {
}
