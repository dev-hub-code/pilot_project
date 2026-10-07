package com.sealease.backend.withdrawal.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PayoutFileCellTest {

	@Test
	void quotesAndEscapesEveryField() {
		assertThat(PayoutBatchService.cell("Plain")).isEqualTo("\"Plain\"");
		assertThat(PayoutBatchService.cell("Smith, \"Jo\"")).isEqualTo("\"Smith, \"\"Jo\"\"\"");
		assertThat(PayoutBatchService.cell(null)).isEqualTo("\"\"");
	}

	@Test
	void neutralisesSpreadsheetFormulas() {
		for (String formula : new String[] { "=SUM(A1)", "+1", "-1", "@cmd", "\tx" }) {
			assertThat(PayoutBatchService.cell(formula)).startsWith("\"'");
		}
		assertThat(PayoutBatchService.cell("O'Brien")).isEqualTo("\"O'Brien\"");
	}

}
