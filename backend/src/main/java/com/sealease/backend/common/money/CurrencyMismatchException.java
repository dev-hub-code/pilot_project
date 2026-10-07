package com.sealease.backend.common.money;

import java.util.Currency;

/**
 * A programming error, not a client error: callers must never mix currencies. Deliberately not a
 * {@link com.sealease.backend.common.exception.BusinessException} so it surfaces as a 500.
 */
public class CurrencyMismatchException extends IllegalArgumentException {

	public CurrencyMismatchException(Currency left, Currency right) {
		super("Currency mismatch: " + left.getCurrencyCode() + " vs " + right.getCurrencyCode());
	}

}
