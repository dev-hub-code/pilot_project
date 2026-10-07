package com.sealease.backend.container.dto;

import com.sealease.backend.container.entity.ContainerDocument;
import com.sealease.backend.document.entity.DocumentPurpose;

import java.time.Instant;
import java.util.UUID;

public record ContainerDocumentResponse(UUID documentId, DocumentPurpose purpose, String title,
		boolean visibleToInvestors, Instant uploadedAt) {

	public static ContainerDocumentResponse from(ContainerDocument d) {
		return new ContainerDocumentResponse(d.getDocumentId(), d.getPurpose(), d.getTitle(), d.isVisibleToInvestors(),
				d.getCreatedAt());
	}

}
