package com.sealease.backend.payment.provider;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.payment.entity.PaymentMethod;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Cash handed over at the company's office. The provider reference is the payment reference the investor quotes
 * at the counter, so the cashier can find the payment; finance then confirms it with the receipt number.
 */
@Component
public class OfficeCashProvider implements PaymentProvider {

	public static final String NAME = "OFFICE";

	/** No 0/O or 1/I: references are read out and typed by people. */
	private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

	private final SecureRandom random = new SecureRandom();

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public PaymentMethod method() {
		return PaymentMethod.OFFICE_CASH;
	}

	@Override
	public String open(String orderNumber, Money amount) {
		StringBuilder reference = new StringBuilder("OF-");
		for (int i = 0; i < 8; i++) {
			reference.append(ALPHABET[random.nextInt(ALPHABET.length)]);
		}
		return reference.toString();
	}

}
