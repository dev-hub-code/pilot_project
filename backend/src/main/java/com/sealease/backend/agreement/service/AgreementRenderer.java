package com.sealease.backend.agreement.service;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders an agreement's wording as a PDF (JasperReports, fonts embedded). Paragraphs are separated by a blank line;
 * a line starting with "# " is a heading. Single line breaks inside a paragraph are kept. The template is compiled once.
 */
@Component
public class AgreementRenderer {

	static final String TEMPLATE = "reports/agreement.jrxml";

	private volatile JasperReport template;

	/** @param subtitle e.g. the agreement number and the day it was issued */
	public byte[] pdf(String title, String subtitle, String body, String footnote) {
		Map<String, Object> parameters = new HashMap<>();
		parameters.put("title", title);
		parameters.put("subtitle", subtitle);
		parameters.put("footnote", footnote);
		try {
			JasperPrint print = JasperFillManager.fillReport(template(), parameters, new JRMapCollectionDataSource(paragraphs(body)));
			return JasperExportManager.exportReportToPdf(print);
		}
		catch (JRException ex) {
			throw new IllegalStateException("Could not render agreement " + title, ex);
		}
	}

	static List<Map<String, ?>> paragraphs(String body) {
		List<Map<String, ?>> rows = new ArrayList<>();
		for (String block : body.replace("\r\n", "\n").split("\\n\\s*\\n")) {
			String text = block.strip();
			if (text.isEmpty()) {
				continue;
			}
			// "# Heading" is its own line: what follows it in the block is a paragraph.
			if (text.startsWith("# ")) {
				int end = text.indexOf('\n');
				rows.add(Map.of("text", (end < 0 ? text : text.substring(0, end)).substring(2).strip(), "heading", true));
				text = end < 0 ? "" : text.substring(end + 1).strip();
				if (text.isEmpty()) {
					continue;
				}
			}
			rows.add(Map.of("text", text, "heading", false));
		}
		return rows;
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
						throw new IllegalStateException("Agreement template missing: " + TEMPLATE, ex);
					}
					template = compiled;
				}
			}
		}
		return compiled;
	}

}
