package com.sealease.backend.crm.dto;

import com.sealease.backend.crm.entity.ActivityType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @param editable whether the viewer may change this lead (its owner, or a LEAD_ASSIGN holder)
 * @param activities newest first, at most 100
 */
public record LeadDetail(LeadResponse lead, boolean editable, List<Activity> activities) {

	/** @param actorName {@code null} for platform entries */
	public record Activity(UUID id, ActivityType type, String body, String actorName, Instant createdAt) {
	}

}
