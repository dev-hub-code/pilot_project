package com.sealease.backend.reporting.export;

import com.sealease.backend.common.csv.Csv;
import com.sealease.backend.common.time.DisplayTimeZone;
import com.sealease.backend.reporting.catalog.ReportResult;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.List;

/** Turns a report result into a CSV, XLSX or PDF file. */
@Component
public class ReportExporter {

	private final ZoneId zone;

	public ReportExporter(DisplayTimeZone display) {
		this.zone = display.zone();
	}

	public byte[] export(ReportResult result, ExportContext context, ExportFormat format) {
		return switch (format) {
			case CSV -> csv(result);
			case XLSX -> XlsxWriter.write(result, context, zone);
			case PDF -> TablePdfRenderer.render(result, context, zone);
		};
	}

	/**
	 * UTF-8 with a byte-order mark (so spreadsheets read ₹ correctly), one header row, plain decimal amounts, and
	 * values that could run as formulas defused by {@link Csv#cell}. A currency column is added when amounts appear.
	 */
	byte[] csv(ReportResult result) {
		List<ReportResult.Column> columns = result.columns();
		boolean money = columns.stream().anyMatch(c -> c.type() == com.sealease.backend.reporting.catalog.ColumnType.MONEY);
		String currency = result.totals().size() == 1 ? result.totals().getFirst().currency() : "";
		StringBuilder out = new StringBuilder("﻿");
		String[] header = new String[columns.size() + (money ? 1 : 0)];
		for (int i = 0; i < columns.size(); i++) {
			header[i] = columns.get(i).label();
		}
		if (money) {
			header[columns.size()] = "Currency";
		}
		out.append(Csv.line(header));
		for (List<Object> row : result.rows()) {
			String[] fields = new String[header.length];
			for (int i = 0; i < columns.size(); i++) {
				fields[i] = Cells.data(columns.get(i).type(), row.get(i), zone);
			}
			if (money) {
				fields[columns.size()] = currency;
			}
			out.append(Csv.line(fields));
		}
		return out.toString().getBytes(StandardCharsets.UTF_8);
	}

}
