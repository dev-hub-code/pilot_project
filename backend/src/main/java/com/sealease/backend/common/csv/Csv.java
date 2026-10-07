package com.sealease.backend.common.csv;

import java.util.regex.Pattern;

/** CSV for files people open in spreadsheets: every field quoted, leading formula characters neutralised. */
public final class Csv {

	/**
	 * Plain amounts (e.g. "-12.50", "-₹1,23,450.50" or "-1,234.50 USD") are data, not formulas: they hold no operators
	 * or references, so they are left as they are.
	 */
	private static final Pattern NUMBER = Pattern.compile("[-+]?₹?\\d[\\d,]*(\\.\\d+)?( [A-Z]{3})?");

	private Csv() {
	}

	/** One CRLF-terminated line. */
	public static String line(String... fields) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < fields.length; i++) {
			if (i > 0) {
				out.append(',');
			}
			out.append(cell(fields[i]));
		}
		return out.append("\r\n").toString();
	}

	/**
	 * A quoted field. A value starting with {@code = + - @ TAB CR} gets a leading apostrophe so a
	 * spreadsheet does not run it as a formula (CSV injection) - unless it is a plain number.
	 */
	public static String cell(String value) {
		String v = value == null ? "" : value;
		if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0 && !NUMBER.matcher(v).matches()) {
			v = "'" + v;
		}
		return "\"" + v.replace("\"", "\"\"") + "\"";
	}

}
