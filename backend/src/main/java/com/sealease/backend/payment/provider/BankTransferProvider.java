package com.sealease.backend.payment.provider;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.payment.entity.PaymentMethod;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Manual bank transfer. The provider reference is the payment reference the investor quotes on
 * the transfer, so finance can match incoming money to the payment.
 */
@Component
public class BankTransferProvider implements PaymentProvider {

	public static final String NAME = "BANK";

	/** No 0/O or 1/I: references are typed by people. */
	private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

	private final SecureRandom random = new SecureRandom();

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public PaymentMethod method() {
		return PaymentMethod.BANK_TRANSFER;
	}

	@Override
	public String open(String orderNumber, Money amount) {
		StringBuilder reference = new StringBuilder("SL-");
		for (int i = 0; i < 10; i++) {
			reference.append(ALPHABET[random.nextInt(ALPHABET.length)]);
		}
		return reference.toString();
	}

}
