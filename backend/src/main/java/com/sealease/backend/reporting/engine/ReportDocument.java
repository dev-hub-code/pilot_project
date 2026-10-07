package com.sealease.backend.reporting.engine;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A report as data: a title and sections of four-column rows. The same document renders as a PDF
 * (one shared template) and as CSV, so both formats always show the same figures.
 *
 * @param subtitle e.g. the period and the person or scope the report covers
 */
public record ReportDocument(String title, String subtitle, String filename, Instant generatedAt, List<Section> sections,
		String footnote) {

	public ReportDocument {
		Objects.requireNonNull(title, "title");
		sections = List.copyOf(sections);
	}

	/** @param headers four column labels; the last column is right-aligned (amounts) */
	public record Section(String name, List<String> headers, List<Row> rows) {

		public Section {
			if (headers.size() != 4) {
				throw new IllegalArgumentException("Sections have exactly four columns");
			}
			headers = List.copyOf(headers);
			rows = List.copyOf(rows);
		}

	}

	/** @param emphasis totals and balances, printed bold */
	public record Row(String c1, String c2, String c3, String c4, boolean emphasis) {

		public static Row of(String c1, String c2, String c3, String c4) {
			return new Row(c1, c2, c3, c4, false);
		}

		public static Row total(String c1, String c2, String c3, String c4) {
			return new Row(c1, c2, c3, c4, true);
		}

	}

}
