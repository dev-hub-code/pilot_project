package com.sealease.backend.bankaccount.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class AccountNumbersTest {

	@ParameterizedTest
	@ValueSource(strings = { "GB82 WEST 1234 5698 7654 32", "DE89-3704-0044-0532-0130-00", "fr1420041010050500013m02606" })
	void acceptsValidIbans(String iban) {
		String normalized = AccountNumbers.normalize(iban);
		assertThat(AccountNumbers.looksLikeIban(normalized)).isTrue();
		assertThat(AccountNumbers.isValidIban(normalized)).isTrue();
	}

	@Test
	void rejectsIbanWithWrongChecksum() {
		String normalized = AccountNumbers.normalize("GB82WEST12345698765433");
		assertThat(AccountNumbers.isValidIban(normalized)).isFalse();
	}

	@Test
	void plainAccountNumbersAreNotTreatedAsIbans() {
		assertThat(AccountNumbers.looksLikeIban("123456789012")).isFalse();
		assertThat(AccountNumbers.normalize(" 1234-5678 ")).isEqualTo("12345678");
	}

}
