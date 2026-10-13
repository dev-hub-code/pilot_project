package com.sealease.backend.kyc;

import com.sealease.backend.kyc.service.IndianIds;
import com.sealease.backend.support.IndianIdFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class IndianIdsTest {

	@ParameterizedTest
	@ValueSource(strings = { "999941057058", "2345 6789 0124", "2345-6789-0125", "123456789012", "012345678901" })
	void acceptsAnyTwelveDigitAadhaarNumber(String number) {
		assertThat(IndianIds.isValidAadhaar(IndianIds.normalizeAadhaar(number))).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "23456789012", "2345678901245", "23456789012A", "" })
	void rejectsAadhaarNumbersThatAreNotTwelveDigits(String number) {
		assertThat(IndianIds.isValidAadhaar(IndianIds.normalizeAadhaar(number))).isFalse();
	}

	@Test
	void testFixturesProduceValidNumbers() {
		for (int i = 0; i < 1000; i++) {
			assertThat(IndianIds.isValidAadhaar(IndianIdFixtures.aadhaar())).isTrue();
			assertThat(IndianIds.isValidPan(IndianIdFixtures.pan())).isTrue();
		}
	}

	@Test
	void panIsFiveLettersFourDigitsAndALetter() {
		assertThat(IndianIds.isValidPan(IndianIds.normalizePan("abcde 1234 f"))).isTrue();
		assertThat(IndianIds.isValidPan(IndianIds.normalizePan("ABCD1234EF"))).isFalse();
		assertThat(IndianIds.isValidPan(IndianIds.normalizePan("ABCDE12345"))).isFalse();
		assertThat(IndianIds.isValidPan(IndianIds.normalizePan("ABCDE1234"))).isFalse();
	}

}
