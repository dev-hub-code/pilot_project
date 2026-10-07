package com.sealease.backend.crm.dto;

import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.crm.entity.Lead;
import com.sealease.backend.crm.entity.LeadInterest;
import com.sealease.backend.crm.entity.LeadSource;
import com.sealease.backend.crm.entity.LeadStage;

import java.time.Instant;
import java.util.UUID;

/** @param userId the registered account this lead became, if any */
public record LeadResponse(
		UUID id,
		String reference,
		String firstName,
		String lastName,
		String email,
		String phone,
		String country,
		LeadSource source,
		LeadInterest interest,
		MoneyResponse estimate,
		String message,
		LeadStage stage,
		String lostReason,
		UUID ownerId,
		String ownerName,
		UUID userId,
		MoneyResponse won,
		Instant nextFollowUpAt,
		Instant closedAt,
		Instant createdAt,
		Instant updatedAt) {

	public static LeadResponse of(Lead l, String ownerName) {
		Lead.Contact c = l.contact();
		return new LeadResponse(l.getId(), l.getReference(), c.firstName(), c.lastName(), c.email(), c.phone(),
				c.country(), l.getSource(), c.interest(), c.estimate() == null ? null : MoneyResponse.from(c.estimate()),
				l.getMessage(), l.getStage(), l.getLostReason(), l.getOwnerId(), ownerName, l.getUserId(),
				l.won() == null ? null : MoneyResponse.from(l.won()), l.getNextFollowUpAt(), l.getClosedAt(),
				l.getCreatedAt(), l.getUpdatedAt());
	}

}
