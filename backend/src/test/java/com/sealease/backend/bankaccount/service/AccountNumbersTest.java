package com.sealease.backend.bankaccount.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccountNumbersTest {

	@Test
	void normalizesRoutingCodes() {
		assertThat(AccountNumbers.normalize("hdfc-0001 234")).isEqualTo("HDFC0001234");
	}

	@Test
	void leavesAccountNumbersAsDigits() {
		assertThat(AccountNumbers.normalize("50100012345678")).isEqualTo("50100012345678");
	}

}
