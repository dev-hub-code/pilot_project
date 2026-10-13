package com.sealease.backend.common.csv;

import java.util.ArrayList;
import java.util.List;
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

	/**
	 * Parses CSV (RFC 4180: quoted fields, doubled quotes, CR/LF or LF line ends, a leading BOM).
	 * Blank lines are dropped; the apostrophe {@link #cell} adds before formula characters is removed.
	 */
	public static List<List<String>> parse(String text) {
		List<List<String>> rows = new ArrayList<>();
		List<String> row = new ArrayList<>();
		StringBuilder field = new StringBuilder();
		boolean quoted = false;
		boolean wasQuoted = false;
		String input = text.startsWith("\uFEFF") ? text.substring(1) : text;
		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);
			if (quoted) {
				if (c == '"' && i + 1 < input.length() && input.charAt(i + 1) == '"') {
					field.append('"');
					i++;
				}
				else if (c == '"') {
					quoted = false;
				}
				else {
					field.append(c);
				}
			}
			else if (c == '"' && field.isEmpty()) {
				quoted = true;
				wasQuoted = true;
			}
			else if (c == ',') {
				row.add(unescape(field.toString(), wasQuoted));
				field.setLength(0);
				wasQuoted = false;
			}
			else if (c == '\n' || c == '\r') {
				if (c == '\r' && i + 1 < input.length() && input.charAt(i + 1) == '\n') {
					i++;
				}
				row.add(unescape(field.toString(), wasQuoted));
				addUnlessBlank(rows, row);
				row = new ArrayList<>();
				field.setLength(0);
				wasQuoted = false;
			}
			else {
				field.append(c);
			}
		}
		if (!field.isEmpty() || !row.isEmpty() || wasQuoted) {
			row.add(unescape(field.toString(), wasQuoted));
			addUnlessBlank(rows, row);
		}
		return rows;
	}

	private static String unescape(String value, boolean quoted) {
		String v = quoted ? value : value.strip();
		return v.length() > 1 && v.charAt(0) == '\'' && "=+-@\t\r".indexOf(v.charAt(1)) >= 0 ? v.substring(1) : v;
	}

	private static void addUnlessBlank(List<List<String>> rows, List<String> row) {
		if (row.stream().anyMatch(f -> !f.isBlank())) {
			rows.add(row);
		}
	}

}
