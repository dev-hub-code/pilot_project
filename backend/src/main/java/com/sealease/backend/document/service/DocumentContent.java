package com.sealease.backend.document.service;

import com.sealease.backend.document.entity.DocumentPurpose;

import java.util.UUID;

public record DocumentContent(UUID id, UUID ownerUserId, DocumentPurpose purpose, String contentType, byte[] bytes) {

	public String fileExtension() {
		return switch (contentType) {
			case "application/pdf" -> "pdf";
			case "image/png" -> "png";
			default -> "jpg";
		};
	}

}
