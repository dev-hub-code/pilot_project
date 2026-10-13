package com.sealease.backend.user.service;

import com.sealease.backend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

	private final PasswordPolicy policy = new PasswordPolicy();

	@ParameterizedTest
	@ValueSource(strings = { "K7#pq2", "Correct-Horse-9", "Eighteen-chars-1k!", "Sea Lease 2026!", "Ünïcode€pw1" })
	void acceptsSixToEighteenCharactersWithUppercaseNumberAndSpecial(String password) {
		assertThatCode(() -> policy.validate(password, "alice@example.com")).doesNotThrowAnyException();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"K7#pq", // too short
			"Nineteen-chars-long", // too long
			"k7#pq2xy", // no uppercase
			"K7pq2xyZ", // no special character
			"No-Digits-Here!", // no number
			"K7 pq2xyZ", // a space is not a special character
			"AAAAAA!!", // too few different characters
	})
	void rejectsPasswordsOutsideTheRules(String password) {
		assertThatThrownBy(() -> policy.validate(password, "alice@example.com"))
			.isInstanceOf(BusinessException.class);
	}

	@Test
	void explainsTheRules() {
		assertThatThrownBy(() -> policy.validate("lowercase-only", "alice@example.com"))
			.hasMessageContaining("6 to 18 characters")
			.hasMessageContaining("uppercase")
			.hasMessageContaining("one number")
			.hasMessageContaining("special character");
	}

	@Test
	void rejectsPasswordContainingEmailName() {
		assertThatThrownBy(() -> policy.validate("My-AliceSmith-26!", "alicesmith@example.com"))
			.isInstanceOf(BusinessException.class)
			.hasMessageContaining("email");
	}

}
