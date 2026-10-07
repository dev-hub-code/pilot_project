package com.sealease.backend.container.service;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * ISO 6346 container identification: owner code (3 letters) + equipment category (U, J or Z) +
 * 6-digit serial number + check digit. The check digit catches typos before a container (and the
 * money invested in it) is registered under the wrong identity.
 */
public final class ContainerNumbers {

	private static final Pattern FORMAT = Pattern.compile("[A-Z]{3}[UJZ]\\d{7}");

	/** ISO 6346 letter values A..Z (10..38, skipping multiples of 11). */
	private static final int[] LETTER_VALUES = { 10, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 23, 24, 25, 26, 27, 28,
			29, 30, 31, 32, 34, 35, 36, 37, 38 };

	private ContainerNumbers() {
	}

	public static String normalize(String value) {
		return value == null ? null : value.replace(" ", "").replace("-", "").toUpperCase(Locale.ROOT);
	}

	public static boolean isValid(String normalized) {
		return normalized != null && FORMAT.matcher(normalized).matches()
				&& checkDigit(normalized) == normalized.charAt(10) - '0';
	}

	/** Completes an owner code + category + serial (10 characters) with its check digit. */
	public static String withCheckDigit(String first10) {
		String prefix = normalize(first10);
		if (prefix == null || !prefix.matches("[A-Z]{3}[UJZ]\\d{6}")) {
			throw new IllegalArgumentException("Expected 3 letters, U/J/Z and 6 digits");
		}
		return prefix + checkDigit(prefix);
	}

	/** Weighted sum of the first ten characters (weights 2^position) mod 11, with 10 mapped to 0. */
	static int checkDigit(String number) {
		int sum = 0;
		for (int i = 0; i < 10; i++) {
			sum += characterValue(number.charAt(i)) << i;
		}
		return sum % 11 % 10;
	}

	private static int characterValue(char c) {
		return Character.isDigit(c) ? c - '0' : LETTER_VALUES[c - 'A'];
	}

}
