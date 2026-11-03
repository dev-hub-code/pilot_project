package com.sealease.backend.reporting.export;

import java.util.List;

/**
 * What an export says about itself beside the rows: the period, the filters applied and the summary figures.
 *
 * @param filters human-readable, e.g. "Status: Paid, Failed"
 */
public record ExportContext(String title, String description, String period, List<String> filters, String generatedAt,
		String generatedBy, String note) {

	public ExportContext {
		filters = List.copyOf(filters);
	}

}
