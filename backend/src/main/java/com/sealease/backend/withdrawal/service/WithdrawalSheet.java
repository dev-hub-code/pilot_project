package com.sealease.backend.withdrawal.service;

import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Excel (.xlsx) withdrawal file finance pays by hand: one row per withdrawal with the payee's bank details, and
 * Status and Remarks columns finance fills in after paying, before uploading it back.
 */
final class WithdrawalSheet {

	static final String CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
	static final List<String> HEADER = List.of("Serial No", "User ID", "Name", "Account Number", "IFSC Code", "Amount",
			"Status", "Remarks");
	private static final double[] WIDTHS = { 10, 40, 30, 22, 14, 14, 12, 40 };

	/** One payee. */
	record Line(String userId, String name, String accountNumber, String ifsc, BigDecimal amount) {
	}

	/** One row read back: its spreadsheet row number (1-based), and its cells by normalised column name. */
	record Entry(int row, Map<String, String> cells) {

		String get(String column) {
			return cells.getOrDefault(column, "").strip();
		}

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
				sheet.value(r, 0, r);
				sheet.value(r, 1, line.userId());
				sheet.value(r, 2, line.name());
				// Text, so leading zeros survive.
				sheet.value(r, 3, line.accountNumber());
				sheet.value(r, 4, line.ifsc());
				sheet.value(r, 5, line.amount());
				sheet.style(r, 5).format("#,##0.00").set();
			}
			sheet.freezePane(0, 1);
			workbook.finish();
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return out.toByteArray();
	}

	/**
	 * The rows of an uploaded file, keyed by column name lower-cased without spaces or underscores ("User ID" and
	 * "user_id" are both "userid"). Blank rows are left out.
	 */
	static List<Entry> read(byte[] content) {
		if (!ReadableWorkbook.isOOXMLZipHeader(content)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Upload the withdrawal file as an Excel (.xlsx) file");
		}
		try (ReadableWorkbook workbook = new ReadableWorkbook(new ByteArrayInputStream(content))) {
			List<Row> rows = workbook.getFirstSheet().read();
			if (rows.isEmpty()) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED, "The file is empty");
			}
			Row header = rows.getFirst();
			Map<Integer, String> columns = new HashMap<>();
			for (int c = 0; c < header.getCellCount(); c++) {
				String name = normalise(text(header, c));
				if (!name.isEmpty()) {
					columns.putIfAbsent(c, name);
				}
			}
			List<Entry> entries = new ArrayList<>();
			for (Row row : rows.subList(1, rows.size())) {
				Map<String, String> cells = new HashMap<>();
				columns.forEach((c, name) -> cells.put(name, text(row, c)));
				if (cells.values().stream().anyMatch(v -> !v.isBlank())) {
					entries.add(new Entry(row.getRowNum(), cells));
				}
			}
			return entries;
		}
		catch (IOException e) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "The file could not be read as an Excel workbook");
		}
	}

	static boolean hasColumn(List<Entry> entries, String column) {
		return entries.isEmpty() || entries.getFirst().cells().containsKey(column);
	}

	static String normalise(String header) {
		return header.toLowerCase(Locale.ROOT).replaceAll("[\\s_.-]", "");
	}

	private static String text(Row row, int column) {
		if (column >= row.getCellCount() || row.getCell(column) == null) {
			return "";
		}
		String text = row.getCellText(column);
		return text == null ? "" : text.strip();
	}

}
