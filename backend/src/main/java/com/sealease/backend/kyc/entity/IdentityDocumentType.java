package com.sealease.backend.kyc.entity;

public enum IdentityDocumentType {

	PASSPORT(false),
	NATIONAL_ID(true),
	DRIVING_LICENSE(true);

	private final boolean requiresBackImage;

	IdentityDocumentType(boolean requiresBackImage) {
		this.requiresBackImage = requiresBackImage;
	}

	public boolean requiresBackImage() {
		return requiresBackImage;
	}

}
