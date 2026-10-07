package com.sealease.backend.container.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContainerNumbersTest {

	@ParameterizedTest
	@ValueSource(strings = { "CSQU3054383", "csqu 305438 3", "CSQU-305438-3" })
	void acceptsTheIsoReferenceExampleInAnyFormatting(String raw) {
		assertThat(ContainerNumbers.isValid(ContainerNumbers.normalize(raw))).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "CSQU3054384", "CSQX3054383", "CSQU305438", "C5QU3054383", "" })
	void rejectsWrongCheckDigitCategoryOrFormat(String raw) {
		assertThat(ContainerNumbers.isValid(ContainerNumbers.normalize(raw))).isFalse();
	}

	@Test
	void computesCheckDigits() {
		assertThat(ContainerNumbers.withCheckDigit("CSQU305438")).isEqualTo("CSQU3054383");
		assertThat(ContainerNumbers.isValid(ContainerNumbers.withCheckDigit("SLSU123456"))).isTrue();
		assertThatThrownBy(() -> ContainerNumbers.withCheckDigit("SLS123456")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void everySerialHasExactlyOneValidCheckDigit() {
		for (int serial = 100000; serial < 100500; serial++) {
			String prefix = "SLSU" + serial;
			long valid = java.util.stream.IntStream.rangeClosed(0, 9)
				.filter(d -> ContainerNumbers.isValid(prefix + d))
				.count();
			assertThat(valid).as(prefix).isEqualTo(1);
		}
	}

}
