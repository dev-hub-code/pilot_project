package com.sealease.backend.user.service;

import java.util.Locale;

/** Canonical email form used for storage and lookups (matches the users_email_normalised_chk constraint). */
public final class EmailNormalizer {

	private EmailNormalizer() {
	}

	public static String normalize(String email) {
		return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
	}

}
