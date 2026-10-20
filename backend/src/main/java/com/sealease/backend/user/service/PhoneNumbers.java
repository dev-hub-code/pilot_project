package com.sealease.backend.user.service;

/** Compares mobile numbers however they were typed (the key stored in user_profiles.phone_key). */
public final class PhoneNumbers {

	private PhoneNumbers() {
	}

	/**
	 * The number reduced to digits, without India's country code (+91) or trunk prefix (0), so that
	 * "98765 43210", "+91 98765 43210" and "098765 43210" give the same key. {@code null} for no number.
	 */
	public static String key(String phone) {
		if (phone == null) {
			return null;
		}
		String digits = phone.replaceAll("[^0-9]", "");
		if (digits.length() == 12 && digits.startsWith("91")) {
			return digits.substring(2);
		}
		if (digits.length() == 11 && digits.startsWith("0")) {
			return digits.substring(1);
		}
		return digits.isEmpty() ? null : digits;
	}

}
