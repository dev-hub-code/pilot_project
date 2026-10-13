package com.sealease.backend.auth.service;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * 6-digit email verification codes. Guessing is bounded online (a few attempts per email, per-IP
 * limits); the stored hash is salted with the user id so equal codes of different users differ.
 */
final class VerificationCodes {

	private static final SecureRandom RANDOM = new SecureRandom();

	private VerificationCodes() {
	}

	static String generate() {
		return "%06d".formatted(RANDOM.nextInt(1_000_000));
	}

	static String hash(UUID userId, String code) {
		return OpaqueTokens.hash(userId + ":" + code);
	}

}
