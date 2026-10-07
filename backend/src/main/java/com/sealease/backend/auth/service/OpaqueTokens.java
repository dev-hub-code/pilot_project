package com.sealease.backend.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Generation and hashing of opaque refresh tokens (256 bits of entropy, stored as SHA-256). */
final class OpaqueTokens {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final int TOKEN_BYTES = 32;

	private OpaqueTokens() {
	}

	static String generate() {
		byte[] bytes = new byte[TOKEN_BYTES];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/**
	 * A fast hash is appropriate here (unlike passwords): the input is 256 random bits, so it
	 * cannot be brute-forced, and lookups by hash must be cheap.
	 */
	static String hash(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 unavailable", ex);
		}
	}

}
