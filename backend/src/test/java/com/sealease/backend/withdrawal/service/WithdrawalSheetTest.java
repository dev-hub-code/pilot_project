package com.sealease.backend.withdrawal.service;

import com.sealease.backend.common.exception.BusinessException;
import org.dhatim.fastexcel.reader.CellType;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WithdrawalSheetTest {

	@Test
	void writesOneRowPerPayeeAndReadsItBackByColumnName() {
		byte[] file = WithdrawalSheet.write(List.of(
				new WithdrawalSheet.Line("u-1", "Asha Rao", "000123456789", "HDFC0001234", new BigDecimal("1000.50")),
				new WithdrawalSheet.Line("u-2", "Ravi Kumar", "987654321012", "SBIN0000001", new BigDecimal("75.00"))));

		List<WithdrawalSheet.Entry> rows = WithdrawalSheet.read(file);
		assertThat(rows).hasSize(2);
		assertThat(rows.getFirst().get("serialno")).isEqualTo("1");
		assertThat(rows.getFirst().get("userid")).isEqualTo("u-1");
		assertThat(rows.getFirst().get("name")).isEqualTo("Asha Rao");
		// Account numbers are text: leading zeros survive.
		assertThat(rows.getFirst().get("accountnumber")).isEqualTo("000123456789");
		assertThat(rows.getFirst().get("ifsccode")).isEqualTo("HDFC0001234");
		assertThat(new BigDecimal(rows.getFirst().get("amount"))).isEqualByComparingTo("1000.50");
		assertThat(rows.get(1).get("status")).isEmpty();
		assertThat(WithdrawalSheet.hasColumn(rows, "remarks")).isTrue();
	}

	@Test
	void textThatLooksLikeAFormulaStaysText() throws Exception {
		byte[] file = WithdrawalSheet.write(List.of(
				new WithdrawalSheet.Line("u-1", "=HYPERLINK(\"http://x\")", "+123", "@cmd", BigDecimal.ONE)));
		try (ReadableWorkbook workbook = new ReadableWorkbook(new ByteArrayInputStream(file))) {
			Row row = workbook.getFirstSheet().read().get(1);
			for (int c = 2; c <= 4; c++) {
				assertThat(row.getCell(c).getType()).isEqualTo(CellType.STRING);
			}
			assertThat(row.getCellText(2)).isEqualTo("=HYPERLINK(\"http://x\")");
		}
	}

	@Test
	void refusesFilesThatAreNotExcel() {
		assertThatThrownBy(() -> WithdrawalSheet.read("User ID,Status\nu-1,PAID".getBytes(StandardCharsets.UTF_8)))
			.isInstanceOf(BusinessException.class).hasMessageContaining(".xlsx");
	}

	@Test
	void headersAreMatchedLooselyAndBlankRowsSkipped() {
		assertThat(WithdrawalSheet.normalise(" User_ID ")).isEqualTo("userid");
		assertThat(WithdrawalSheet.normalise("IFSC Code")).isEqualTo("ifsccode");
	}

}
