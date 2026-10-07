package com.sealease.backend.user.service;

import java.security.SecureRandom;

/** Random temporary passwords, e.g. {@code K7QM-2XWP-9RTA-HB4N}: 80 bits, no look-alike characters. */
final class TemporaryPasswords {

	private static final char[] ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();
	private static final SecureRandom RANDOM = new SecureRandom();

	private TemporaryPasswords() {
	}

	static String generate() {
		StringBuilder out = new StringBuilder(19);
		for (int i = 0; i < 16; i++) {
			if (i > 0 && i % 4 == 0) {
				out.append('-');
			}
			out.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
		}
		return out.toString();
	}

}
