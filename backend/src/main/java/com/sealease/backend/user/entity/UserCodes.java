package com.sealease.backend.user.entity;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Users' readable IDs: "SL" and their number, at least five digits ("SL03500"). */
public final class UserCodes {

	private static final Pattern CODE = Pattern.compile("SL0*(\\d{1,18})");

	private UserCodes() {
	}

	public static String format(long number) {
		return "SL" + String.format(Locale.ROOT, "%05d", number);
	}

	/** The number in an ID such as "SL03500" (any case, spaces around ignored); empty if it is not one. */
	public static Optional<Long> parse(String code) {
		if (code == null) {
			return Optional.empty();
		}
		Matcher m = CODE.matcher(code.strip().toUpperCase(Locale.ROOT));
		return m.matches() ? Optional.of(Long.parseLong(m.group(1))) : Optional.empty();
	}

}
