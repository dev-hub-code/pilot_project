package com.sealease.backend.investment.dto;

import com.sealease.backend.investment.entity.CapacityMovement;
import com.sealease.backend.investment.entity.CapacityMovementType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CapacityMovementResponse(UUID id, CapacityMovementType type, BigDecimal amount, String reference,
		UUID investorUserId, BigDecimal reservedAfter, BigDecimal committedAfter, Instant createdAt) {

	public static CapacityMovementResponse from(CapacityMovement m) {
		return new CapacityMovementResponse(m.getId(), m.getMovementType(), m.getAmount(), m.getReference(),
				m.getInvestorUserId(), m.getReservedAfter(), m.getCommittedAfter(), m.getCreatedAt());
	}

}
