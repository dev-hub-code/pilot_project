package com.sealease.backend.withdrawal.service;

import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.List;

/**
 * The Excel (.xlsx) sheet of a bank withdrawal batch, which finance pays from the bank: one row per investor with their
 * account and the amount, and a Status column for finance's own use. Each withdrawal is then marked paid or failed
 * on the Finance tasks page. Cash at the office has no sheet.
 */
final class WithdrawalSheet {

	static final String CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

	static final List<String> HEADER = List.of("Serial No", "User Code", "Name", "Account Number", "IFSC Code", "Amount",
			"Status");
	private static final double[] WIDTHS = { 10, 12, 30, 22, 14, 14, 12 };

	/** One investor's row: who, which account, how much. */
	record Line(String userCode, String name, String accountNumber, String ifsc, BigDecimal amount) {
	}

	private WithdrawalSheet() {
	}

	static byte[] write(List<Line> lines) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (Workbook workbook = new Workbook(out, "SeaLease", "1.0")) {
			Worksheet sheet = workbook.newWorksheet("Withdrawals");
			for (int c = 0; c < HEADER.size(); c++) {
				sheet.value(0, c, HEADER.get(c));
				sheet.width(c, WIDTHS[c]);
			}
			sheet.range(0, 0, 0, HEADER.size() - 1).style().bold().fillColor("E8E2D4").set();
			for (int i = 0; i < lines.size(); i++) {
				Line line = lines.get(i);
				int r = i + 1;
				int c = 0;
				sheet.value(r, c++, r);
				sheet.value(r, c++, line.userCode());
				sheet.value(r, c++, line.name());
				// Text, so leading zeros survive.
				sheet.value(r, c++, line.accountNumber());
				sheet.value(r, c++, line.ifsc());
				sheet.value(r, c, line.amount());
				sheet.style(r, c).format("#,##0.00").set();
			}
			sheet.freezePane(0, 1);
			workbook.finish();
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return out.toByteArray();
	}

}
