package com.sealease.backend.bankaccount.service;

import java.math.BigInteger;
import java.util.Locale;

/** Normalisation and IBAN checksum validation for bank identifiers. */
final class AccountNumbers {

	private static final BigInteger NINETY_SEVEN = BigInteger.valueOf(97);

	private AccountNumbers() {
	}

	static String normalize(String value) {
		return value.replace(" ", "").replace("-", "").toUpperCase(Locale.ROOT);
	}

	static boolean looksLikeIban(String normalized) {
		return normalized.matches("[A-Z]{2}\\d{2}[A-Z0-9]{10,30}");
	}

	/** ISO 13616 mod-97 check. */
	static boolean isValidIban(String normalized) {
		String rearranged = normalized.substring(4) + normalized.substring(0, 4);
		StringBuilder digits = new StringBuilder(rearranged.length() * 2);
		for (char c : rearranged.toCharArray()) {
			digits.append(Character.isLetter(c) ? Integer.toString(c - 'A' + 10) : c);
		}
		return new BigInteger(digits.toString()).mod(NINETY_SEVEN).intValue() == 1;
	}

}
