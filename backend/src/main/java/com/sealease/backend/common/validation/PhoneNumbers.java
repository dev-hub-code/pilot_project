package com.sealease.backend.common.validation;

import java.util.Optional;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

/**
 * Phone numbers as they are stored: E.164 ("+919876543210") with the country they belong to, checked against that
 * country's numbering plan (libphonenumber; the frontend applies the same rules with libphonenumber-js).
 */
public final class PhoneNumbers {

	/** The country a number without one is read in. */
	public static final String DEFAULT_COUNTRY = "IN";

	private static final PhoneNumberUtil UTIL = PhoneNumberUtil.getInstance();

	private PhoneNumbers() {
	}

	/**
	 * A valid number.
	 *
	 * @param e164    the number in international form, e.g. "+919876543210"
	 * @param country the ISO 3166-1 alpha-2 country it belongs to, e.g. "IN"
	 * @param mobile  whether it is (or may be) a mobile number
	 */
	public record Phone(String e164, String country, boolean mobile) {

		/** The number in comparable form, digits only with the country code ("919876543210"); unique across accounts. */
		public String key() {
			return e164.substring(1);
		}

	}

	/**
	 * Reads a number however it was typed: nationally ("98765 43210", "098765 43210") in {@code country} (India when
	 * null), or in international form ("+91 98765 43210"), which may belong to another country. Empty when it is not a
	 * valid number.
	 */
	public static Optional<Phone> parse(String number, String country) {
		if (number == null || number.isBlank()) {
			return Optional.empty();
		}
		String chosen = country == null || country.isBlank() ? DEFAULT_COUNTRY : country;
		try {
			PhoneNumber parsed = UTIL.parse(number, chosen);
			// Countries can share a dialling code (+44 is also Jersey's): the chosen one wins when the number is valid there.
			String region = UTIL.isValidNumberForRegion(parsed, chosen) ? chosen : UTIL.getRegionCodeForNumber(parsed);
			if (region == null || !UTIL.isValidNumberForRegion(parsed, region)) {
				return Optional.empty();
			}
			PhoneNumberType type = UTIL.getNumberType(parsed);
			boolean mobile = type == PhoneNumberType.MOBILE || type == PhoneNumberType.FIXED_LINE_OR_MOBILE;
			return Optional.of(new Phone(UTIL.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164), region, mobile));
		}
		catch (NumberParseException ex) {
			return Optional.empty();
		}
	}

	/** {@link #parse} for a number already validated (e.g. by {@link ValidPhone}); null for no number. */
	public static Phone require(String number, String country) {
		if (number == null || number.isBlank()) {
			return null;
		}
		return parse(number, country).orElseThrow(() -> new IllegalArgumentException("Not a valid phone number"));
	}

}
