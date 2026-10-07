package com.sealease.backend.user.service;

import com.sealease.backend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

	private final PasswordPolicy policy = new PasswordPolicy();

	@Test
	void acceptsLongPassphrase() {
		assertThatCode(() -> policy.validate("correct horse battery staple", "alice@example.com"))
			.doesNotThrowAnyException();
	}

	@ParameterizedTest
	@ValueSource(strings = { "short", "aaaaaaaaaaaaaaaa", "abababababababab", "            " })
	void rejectsShortOrTrivial(String password) {
		assertThatThrownBy(() -> policy.validate(password, "alice@example.com"))
			.isInstanceOf(BusinessException.class);
	}

	@Test
	void rejectsPasswordContainingEmailName() {
		assertThatThrownBy(() -> policy.validate("my-alicesmith-2026!", "alicesmith@example.com"))
			.isInstanceOf(BusinessException.class)
			.hasMessageContaining("email");
	}

	@Test
	void rejectsOverlongPassword() {
		assertThatThrownBy(() -> policy.validate("x1y2z3".repeat(30), "alice@example.com"))
			.isInstanceOf(BusinessException.class);
	}

}
