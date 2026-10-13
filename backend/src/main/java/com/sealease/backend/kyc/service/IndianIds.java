package com.sealease.backend.kyc.service;

import java.util.Locale;
import java.util.regex.Pattern;

/** Format checks for Indian identity numbers; the documents themselves are reviewed. */
public final class IndianIds {

	private static final Pattern AADHAAR = Pattern.compile("[0-9]{12}");
	private static final Pattern PAN = Pattern.compile("[A-Z]{5}[0-9]{4}[A-Z]");

	private IndianIds() {
	}

	/** Digits only, e.g. "2345 6789 0124" becomes "234567890124". */
	public static String normalizeAadhaar(String value) {
		return value == null ? "" : value.replaceAll("[\\s-]", "");
	}

	/** Upper case without spaces, e.g. "abcde 1234 f" becomes "ABCDE1234F". */
	public static String normalizePan(String value) {
		return value == null ? "" : value.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
	}

	/** Exactly 12 digits. */
	public static boolean isValidAadhaar(String normalized) {
		return AADHAAR.matcher(normalized).matches();
	}

	/** Five letters, four digits and a letter, e.g. ABCDE1234F. */
	public static boolean isValidPan(String normalized) {
		return PAN.matcher(normalized).matches();
	}

}
