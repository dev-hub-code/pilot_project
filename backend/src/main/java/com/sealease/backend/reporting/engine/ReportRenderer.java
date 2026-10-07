package com.sealease.backend.reporting.engine;

import com.sealease.backend.common.csv.Csv;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders a {@link ReportDocument} as PDF (JasperReports, one shared template, fonts embedded) or CSV.
 * The template is compiled once, on first use.
 */
@Component
public class ReportRenderer {

	static final String TEMPLATE = "reports/document.jrxml";
	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm 'UTC'").withZone(ZoneOffset.UTC);

	private volatile JasperReport template;

	public byte[] pdf(ReportDocument document) {
		Map<String, Object> parameters = new HashMap<>();
		parameters.put("title", document.title());
		parameters.put("subtitle", document.subtitle());
		parameters.put("generatedAt", STAMP.format(document.generatedAt()));
		parameters.put("footnote", document.footnote());
		List<Map<String, ?>> rows = new ArrayList<>();
		int index = 0;
		for (ReportDocument.Section section : document.sections()) {
			// The group key includes the position, so two sections with the same name stay apart.
			String key = (index++) + ":" + section.name();
			List<ReportDocument.Row> sectionRows = section.rows().isEmpty()
					? List.of(ReportDocument.Row.of("Nothing in this period", "", "", "")) : section.rows();
			for (ReportDocument.Row row : sectionRows) {
				Map<String, Object> r = new HashMap<>();
				r.put("sectionKey", key);
				r.put("section", section.name());
				r.put("h1", section.headers().get(0));
				r.put("h2", section.headers().get(1));
				r.put("h3", section.headers().get(2));
				r.put("h4", section.headers().get(3));
				r.put("c1", nullToEmpty(row.c1()));
				r.put("c2", nullToEmpty(row.c2()));
				r.put("c3", nullToEmpty(row.c3()));
				r.put("c4", nullToEmpty(row.c4()));
				r.put("emphasis", row.emphasis());
				rows.add(r);
			}
		}
		try {
			JasperPrint print = JasperFillManager.fillReport(template(), parameters, new JRMapCollectionDataSource(rows));
			return JasperExportManager.exportReportToPdf(print);
		}
		catch (JRException ex) {
			throw new IllegalStateException("Could not render report " + document.title(), ex);
		}
	}

	/** One line per row, with the section as the first column; spreadsheet-safe (formulas neutralised). */
	public byte[] csv(ReportDocument document) {
		StringBuilder out = new StringBuilder("﻿");
		out.append(Csv.line("section", "column_1", "column_2", "column_3", "column_4"));
		for (ReportDocument.Section section : document.sections()) {
			out.append(Csv.line(section.name(), section.headers().get(0), section.headers().get(1),
					section.headers().get(2), section.headers().get(3)));
			for (ReportDocument.Row row : section.rows()) {
				out.append(Csv.line(section.name(), row.c1(), row.c2(), row.c3(), row.c4()));
			}
		}
		return out.toString().getBytes(StandardCharsets.UTF_8);
	}

	private JasperReport template() throws JRException {
		JasperReport compiled = template;
		if (compiled == null) {
			synchronized (this) {
				compiled = template;
				if (compiled == null) {
					try (InputStream in = new ClassPathResource(TEMPLATE).getInputStream()) {
						compiled = JasperCompileManager.compileReport(in);
					}
					catch (IOException ex) {
						throw new IllegalStateException("Report template missing: " + TEMPLATE, ex);
					}
					template = compiled;
				}
			}
		}
		return compiled;
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

}
