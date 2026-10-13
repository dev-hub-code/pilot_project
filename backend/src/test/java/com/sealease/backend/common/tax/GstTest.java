package com.sealease.backend.common.tax;

import com.sealease.backend.common.money.Money;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Place of supply and the CGST/SGST/IGST split. */
class GstTest {

	@Test
	void statesAreFoundByNameShortFormCodeOrGstin() {
		assertThat(GstStates.byName("Maharashtra")).map(GstStates.State::code).contains("27");
		assertThat(GstStates.byName(" maharashtra ")).map(GstStates.State::code).contains("27");
		assertThat(GstStates.byName("MH")).map(GstStates.State::code).contains("27");
		assertThat(GstStates.byName("Tamil Nadu")).map(GstStates.State::code).contains("33");
		assertThat(GstStates.byName("Jammu & Kashmir")).map(GstStates.State::code).contains("01");
		assertThat(GstStates.byName("Orissa")).map(GstStates.State::name).contains("Odisha");
		assertThat(GstStates.byName("29")).map(GstStates.State::name).contains("Karnataka");
		assertThat(GstStates.ofGstin("27ABCDE1234F1Z5")).map(GstStates.State::name).contains("Maharashtra");
		assertThat(GstStates.byName("Bavaria")).isEmpty();
		assertThat(GstStates.byName(" ")).isEmpty();
		assertThat(GstStates.byName("Maharashtra").orElseThrow()).hasToString("Maharashtra (27)");
	}

	@Test
	void withinTheStateGstIsHalfCgstHalfSgstAcrossStatesAllIgst() {
		GstSplit intra = GstSplit.of(Money.of("72000", "INR"), true);
		assertThat(intra.cgst()).isEqualTo(Money.of("36000", "INR"));
		assertThat(intra.sgst()).isEqualTo(Money.of("36000", "INR"));
		assertThat(intra.igst()).isEqualTo(Money.zero(intra.igst().currency()));

		// An odd paisa: CGST takes the half rounded up, SGST the rest, so they add up exactly.
		GstSplit odd = GstSplit.of(Money.of("0.05", "INR"), true);
		assertThat(odd.cgst()).isEqualTo(Money.of("0.03", "INR"));
		assertThat(odd.sgst()).isEqualTo(Money.of("0.02", "INR"));
		assertThat(odd.total()).isEqualTo(Money.of("0.05", "INR"));

		GstSplit inter = GstSplit.of(Money.of("72000", "INR"), false);
		assertThat(inter.igst()).isEqualTo(Money.of("72000", "INR"));
		assertThat(inter.cgst().plus(inter.sgst())).isEqualTo(Money.zero(inter.igst().currency()));
	}

}
