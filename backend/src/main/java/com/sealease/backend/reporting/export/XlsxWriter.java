package com.sealease.backend.reporting.export;

import com.sealease.backend.reporting.catalog.ColumnType;
import com.sealease.backend.reporting.catalog.ReportQueryEngine;
import com.sealease.backend.reporting.catalog.ReportResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes a report as an Office Open XML workbook (.xlsx) without a spreadsheet library: a Summary sheet (title,
 * period, filters, totals) and a Data sheet with a bold frozen header, filters, ₹ formats with Indian grouping, real
 * dates and a totals row (SUBTOTAL, so it follows the filters). Text cells are inline strings, never formulas.
 */
final class XlsxWriter {

	// Style indexes into cellXfs below.
	private static final int HEADER = 1;
	private static final int MONEY = 2;
	private static final int DAY = 3;
	private static final int STAMP = 4;
	private static final int TOTAL_MONEY = 5;
	private static final int BOLD = 6;
	private static final int INTEGER = 7;
	private static final int DECIMAL = 8;
	private static final int TITLE = 9;
	private static final int MONTH = 10;
	private static final LocalDate EXCEL_EPOCH = LocalDate.of(1899, 12, 30);

	private XlsxWriter() {
	}

	static byte[] write(ReportResult result, ExportContext context, ZoneId zone) {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			entry(zip, "[Content_Types].xml", """
					<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
					<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
					<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
					<Default Extension="xml" ContentType="application/xml"/>
					<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
					<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
					<Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
					<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
					</Types>""");
			entry(zip, "_rels/.rels", """
					<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
					<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
					<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
					</Relationships>""");
			int lastRow = result.rows().size() + 1;
			String lastColumn = column(Math.max(result.columns().size(), 1) - 1);
			entry(zip, "xl/workbook.xml", """
					<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
					<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
					<sheets><sheet name="Summary" sheetId="1" r:id="rId1"/><sheet name="Data" sheetId="2" r:id="rId2"/></sheets>
					<definedNames><definedName name="_xlnm._FilterDatabase" localSheetId="1" hidden="1">Data!$A$1:$%s$%d</definedName></definedNames>
					</workbook>""".formatted(lastColumn, lastRow));
			entry(zip, "xl/_rels/workbook.xml.rels", """
					<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
					<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
					<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
					<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
					<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
					</Relationships>""");
			entry(zip, "xl/styles.xml", styles());
			entry(zip, "xl/worksheets/sheet1.xml", summary(result, context));
			entry(zip, "xl/worksheets/sheet2.xml", data(result, zone));
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		return bytes.toByteArray();
	}

	private static String styles() {
		// Indian grouping: 1,23,45,678.00 (crores and lakhs), with the rupee sign.
		String rupees = "[$₹-4009]\\ [>=10000000]##\\,##\\,##\\,##0.00;[$₹-4009]\\ [>=100000]##\\,##\\,##0.00;[$₹-4009]\\ ##,##0.00";
		return """
				<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
				<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
				<numFmts count="4">
				<numFmt numFmtId="164" formatCode="%s"/>
				<numFmt numFmtId="165" formatCode="d mmm yyyy"/>
				<numFmt numFmtId="166" formatCode="d mmm yyyy hh:mm"/>
				<numFmt numFmtId="167" formatCode="mmm yyyy"/>
				</numFmts>
				<fonts count="3"><font><sz val="10"/><name val="Calibri"/></font><font><b/><sz val="10"/><name val="Calibri"/></font><font><b/><sz val="14"/><name val="Calibri"/></font></fonts>
				<fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFE8EEF8"/></patternFill></fill></fills>
				<borders count="2"><border/><border><top style="thin"><color rgb="FF0F1F38"/></top></border></borders>
				<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
				<cellXfs count="11">
				<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
				<xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
				<xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
				<xf numFmtId="165" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
				<xf numFmtId="166" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
				<xf numFmtId="164" fontId="1" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyFont="1" applyBorder="1"/>
				<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>
				<xf numFmtId="1" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
				<xf numFmtId="2" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
				<xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"/>
				<xf numFmtId="167" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
				</cellXfs>
				</styleSheet>""".formatted(escape(rupees));
	}

	private static String summary(ReportResult result, ExportContext context) {
		StringBuilder rows = new StringBuilder();
		int r = 1;
		rows.append(row(r++, text("A", r - 1, context.title(), TITLE)));
		if (!context.description().isEmpty()) {
			rows.append(row(r++, text("A", r - 1, context.description(), 0)));
		}
		r++;
		rows.append(row(r++, text("A", r - 1, "Period", BOLD) + text("B", r - 1, context.period(), 0)));
		rows.append(row(r++, text("A", r - 1, "Generated", BOLD) + text("B", r - 1, context.generatedAt(), 0)));
		rows.append(row(r++, text("A", r - 1, "Generated by", BOLD) + text("B", r - 1, context.generatedBy(), 0)));
		rows.append(row(r++, text("A", r - 1, "Rows", BOLD) + number("B", r - 1, String.valueOf(result.rows().size()), INTEGER)));
		rows.append(row(r++, text("A", r - 1, "Filters", BOLD)
				+ text("B", r - 1, context.filters().isEmpty() ? "None" : String.join("; ", context.filters()), 0)));
		if (!result.totals().isEmpty()) {
			r++;
			rows.append(row(r++, text("A", r - 1, "Totals", BOLD)));
			for (ReportResult.Totals totals : result.totals()) {
				for (ReportResult.Column column : result.columns()) {
					Object value = totals.values().get(column.key());
					if (value == null) {
						continue;
					}
					String label = column.label() + (result.totals().size() > 1 ? " (" + totals.currency() + ")" : "");
					rows.append(row(r++, text("A", r - 1, label, 0) + number("B", r - 1, value.toString(),
							column.type() == ColumnType.MONEY ? MONEY : INTEGER)));
				}
			}
		}
		if (context.note() != null && !context.note().isEmpty()) {
			r++;
			rows.append(row(r, text("A", r, context.note(), 0)));
			r++;
		}
		r++;
		rows.append(row(r, text("A", r, "Confidential: SeaLease internal report. Do not share outside the company.", 0)));
		return sheet("<cols><col min=\"1\" max=\"1\" width=\"28\" customWidth=\"1\"/><col min=\"2\" max=\"2\" width=\"70\" customWidth=\"1\"/></cols>",
				rows.toString(), "", "");
	}

	private static String data(ReportResult result, ZoneId zone) {
		List<ReportResult.Column> columns = result.columns();
		StringBuilder rows = new StringBuilder();
		StringBuilder header = new StringBuilder();
		for (int c = 0; c < columns.size(); c++) {
			ReportResult.Column column = columns.get(c);
			String label = column.label() + (column.type() == ColumnType.MONEY ? " (₹)" : "");
			header.append(text(column(c), 1, label, HEADER));
		}
		rows.append(row(1, header.toString()));
		int r = 2;
		for (List<Object> values : result.rows()) {
			StringBuilder cells = new StringBuilder();
			for (int c = 0; c < columns.size(); c++) {
				cells.append(cell(columns.get(c).type(), column(c), r, values.get(c), zone));
			}
			rows.append(row(r++, cells.toString()));
		}
		// One currency: a SUBTOTAL row that follows the filters. Several: no total (the sheet would add currencies).
		if (!result.rows().isEmpty() && result.totals().size() == 1) {
			Map<String, Object> totals = result.totals().getFirst().values();
			StringBuilder cells = new StringBuilder(text("A", r, "Total", BOLD));
			for (int c = 1; c < columns.size(); c++) {
				ReportResult.Column column = columns.get(c);
				if (totals.containsKey(column.key())) {
					String ref = column(c) + "2:" + column(c) + (r - 1);
					cells.append("<c r=\"").append(column(c)).append(r).append("\" s=\"")
						.append(column.type() == ColumnType.MONEY ? TOTAL_MONEY : BOLD)
						.append("\"><f>SUBTOTAL(9,").append(ref).append(")</f><v>").append(totals.get(column.key()))
						.append("</v></c>");
				}
			}
			rows.append(row(r, cells.toString()));
		}
		StringBuilder cols = new StringBuilder("<cols>");
		for (int c = 0; c < columns.size(); c++) {
			cols.append("<col min=\"").append(c + 1).append("\" max=\"").append(c + 1).append("\" width=\"")
				.append(width(columns.get(c))).append("\" customWidth=\"1\"/>");
		}
		cols.append("</cols>");
		String pane = "<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>";
		String filter = columns.isEmpty() ? "" : "<autoFilter ref=\"A1:" + column(columns.size() - 1) + (result.rows().size() + 1) + "\"/>";
		return sheet(cols.toString(), rows.toString(), pane, filter);
	}

	private static String cell(ColumnType type, String col, int r, Object value, ZoneId zone) {
		if (value == null) {
			return "";
		}
		String s = value.toString();
		if ("Restricted".equals(s)) {
			return text(col, r, s, 0);
		}
		return switch (type) {
			case MONEY -> number(col, r, s, MONEY);
			case NUMBER -> number(col, r, s, s.contains(".") ? DECIMAL : INTEGER);
			case PERCENT -> number(col, r, s, DECIMAL);
			case DATE -> number(col, r, String.valueOf(ChronoUnit.DAYS.between(EXCEL_EPOCH, LocalDate.parse(s))), DAY);
			case MONTH -> number(col, r, String.valueOf(ChronoUnit.DAYS.between(EXCEL_EPOCH, LocalDate.parse(s))), MONTH);
			case DATETIME -> number(col, r, serial(LocalDateTime.ofInstant(Instant.parse(s), zone)), STAMP);
			case STATUS -> text(col, r, ReportQueryEngine.label(s), 0);
			case TEXT -> text(col, r, s, 0);
		};
	}

	private static String serial(LocalDateTime at) {
		long days = ChronoUnit.DAYS.between(EXCEL_EPOCH, at.toLocalDate());
		double fraction = at.toLocalTime().toSecondOfDay() / 86400.0;
		return java.math.BigDecimal.valueOf(days + fraction).setScale(6, java.math.RoundingMode.HALF_UP).toPlainString();
	}

	private static int width(ReportResult.Column column) {
		int byType = switch (column.type()) {
			case MONEY -> 18;
			case DATETIME -> 18;
			case DATE, MONTH -> 13;
			case NUMBER, PERCENT -> 11;
			default -> 20;
		};
		return Math.max(byType, Math.min(column.label().length() + 4, 40));
	}

	private static String sheet(String cols, String rows, String views, String filter) {
		return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
				+ "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" + views + cols
				+ "<sheetData>" + rows + "</sheetData>" + filter + "</worksheet>";
	}

	private static String row(int r, String cells) {
		return "<row r=\"" + r + "\">" + cells + "</row>";
	}

	private static String text(String col, int r, String value, int style) {
		return "<c r=\"" + col + r + "\" t=\"inlineStr\"" + (style == 0 ? "" : " s=\"" + style + "\"") + "><is><t xml:space=\"preserve\">"
				+ escape(value) + "</t></is></c>";
	}

	private static String number(String col, int r, String value, int style) {
		return "<c r=\"" + col + r + "\" s=\"" + style + "\"><v>" + value + "</v></c>";
	}

	/** 0 → A, 25 → Z, 26 → AA. */
	static String column(int index) {
		StringBuilder name = new StringBuilder();
		int n = index + 1;
		while (n > 0) {
			int rem = (n - 1) % 26;
			name.insert(0, (char) ('A' + rem));
			n = (n - 1) / 26;
		}
		return name.toString();
	}

	private static String escape(String value) {
		if (value == null) {
			return "";
		}
		StringBuilder out = new StringBuilder(value.length());
		for (int i = 0; i < value.length(); i++) {
			char ch = value.charAt(i);
			switch (ch) {
				case '&' -> out.append("&amp;");
				case '<' -> out.append("&lt;");
				case '>' -> out.append("&gt;");
				case '"' -> out.append("&quot;");
				default -> {
					// XML 1.0 forbids most control characters.
					if (ch >= 0x20 || ch == '\t' || ch == '\n' || ch == '\r') {
						out.append(ch);
					}
				}
			}
		}
		return out.toString();
	}

	private static void entry(ZipOutputStream zip, String name, String content) throws IOException {
		zip.putNextEntry(new ZipEntry(name));
		zip.write(content.strip().getBytes(StandardCharsets.UTF_8));
		zip.closeEntry();
	}

}
