package com.sealease.backend.investment.dto;

import com.sealease.backend.investment.entity.PlanPhoto;

import java.time.Instant;
import java.util.UUID;

/** @param documentId the image, as served at the plan's photo URLs */
public record PlanPhotoResponse(UUID id, UUID documentId, Instant createdAt) {

	public static PlanPhotoResponse from(PlanPhoto photo) {
		return new PlanPhotoResponse(photo.getId(), photo.getDocumentId(), photo.getCreatedAt());
	}

}
