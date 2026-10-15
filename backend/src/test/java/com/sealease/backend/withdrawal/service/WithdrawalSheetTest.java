package com.sealease.backend.withdrawal.service;

import org.dhatim.fastexcel.reader.CellType;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class WithdrawalSheetTest {

	@Test
	void writesOneRowPerInvestorWithExactlyTheBankColumns() throws Exception {
		byte[] file = WithdrawalSheet.write(List.of(
				new WithdrawalSheet.Line("SL03500", "Asha Rao", "000123456789", "HDFC0001234", new BigDecimal("1200.50")),
				new WithdrawalSheet.Line("SL03501", "Ravi Kumar", "987654321012", "SBIN0000001", new BigDecimal("75.00"))));

		List<List<String>> rows = rows(file);
		assertThat(rows).hasSize(3);
		assertThat(rows.getFirst())
			.containsExactly("Serial No", "User Code", "Name", "Account Number", "IFSC Code", "Amount", "Status");
		// Account numbers are text: leading zeros survive.
		assertThat(rows.get(1).subList(0, 5)).containsExactly("1", "SL03500", "Asha Rao", "000123456789", "HDFC0001234");
		assertThat(new BigDecimal(rows.get(1).get(5))).isEqualByComparingTo("1200.50");
		assertThat(rows.get(2).get(1)).isEqualTo("SL03501");
	}

	@Test
	void textThatLooksLikeAFormulaStaysText() throws Exception {
		byte[] file = WithdrawalSheet.write(List.of(
				new WithdrawalSheet.Line("SL03500", "=HYPERLINK(\"http://x\")", "+123", "@cmd", BigDecimal.ONE)));
		try (ReadableWorkbook workbook = new ReadableWorkbook(new ByteArrayInputStream(file))) {
			Row row = workbook.getFirstSheet().read().get(1);
			for (int c = 2; c <= 4; c++) {
				assertThat(row.getCell(c).getType()).isEqualTo(CellType.STRING);
			}
			assertThat(row.getCellText(2)).isEqualTo("=HYPERLINK(\"http://x\")");
		}
	}

	private static List<List<String>> rows(byte[] file) throws Exception {
		try (ReadableWorkbook workbook = new ReadableWorkbook(new ByteArrayInputStream(file))) {
			return workbook.getFirstSheet().read().stream()
				.map(row -> IntStream.range(0, row.getCellCount()).mapToObj(row::getCellText).toList())
				.toList();
		}
	}

}
