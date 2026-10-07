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
		for (String formula : new String[] { "=SUM(A1)", "+cmd", "-1+1", "@cmd", "\tx" }) {
			assertThat(PayoutBatchService.cell(formula)).startsWith("\"'");
		}
		assertThat(PayoutBatchService.cell("O'Brien")).isEqualTo("\"O'Brien\"");
		// Plain numbers stay numbers (negative amounts in reports).
		assertThat(PayoutBatchService.cell("-12.50")).isEqualTo("\"-12.50\"");
		assertThat(PayoutBatchService.cell("-1,234.50 USD")).isEqualTo("\"-1,234.50 USD\"");
		assertThat(PayoutBatchService.cell("-1+cmd|' /C calc'!A0")).startsWith("\"'");
	}

}
