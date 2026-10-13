package com.sealease.backend.bankaccount.service;

import java.util.Locale;

/** Normalisation of bank identifiers. */
final class AccountNumbers {

	private AccountNumbers() {
	}

	/** Without spaces or dashes, upper case: {@code "hdfc-0001 234"} becomes {@code "HDFC0001234"}. */
	static String normalize(String value) {
		return value.replace(" ", "").replace("-", "").toUpperCase(Locale.ROOT);
	}

}
