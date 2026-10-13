package com.sealease.backend.common.tax;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Indian states and union territories with their GST state codes (the first two digits of a GSTIN), used to
 * decide the place of supply: in the seller's state CGST + SGST is charged, elsewhere IGST.
 */
public final class GstStates {

	public record State(String code, String name) {

		@Override
		public String toString() {
			return name + " (" + code + ")";
		}

	}

	private static final Map<String, State> BY_CODE = new LinkedHashMap<>();
	private static final Map<String, State> BY_NAME = new LinkedHashMap<>();

	static {
		add("01", "Jammu and Kashmir", "jk", "j&k", "jammu & kashmir");
		add("02", "Himachal Pradesh", "hp");
		add("03", "Punjab", "pb");
		add("04", "Chandigarh", "ch");
		add("05", "Uttarakhand", "uk", "uttaranchal");
		add("06", "Haryana", "hr");
		add("07", "Delhi", "dl", "new delhi", "nct of delhi");
		add("08", "Rajasthan", "rj");
		add("09", "Uttar Pradesh", "up");
		add("10", "Bihar", "br");
		add("11", "Sikkim", "sk");
		add("12", "Arunachal Pradesh", "ar");
		add("13", "Nagaland", "nl");
		add("14", "Manipur", "mn");
		add("15", "Mizoram", "mz");
		add("16", "Tripura", "tr");
		add("17", "Meghalaya", "ml");
		add("18", "Assam", "as");
		add("19", "West Bengal", "wb");
		add("20", "Jharkhand", "jh");
		add("21", "Odisha", "od", "or", "orissa");
		add("22", "Chhattisgarh", "cg", "chattisgarh");
		add("23", "Madhya Pradesh", "mp");
		add("24", "Gujarat", "gj");
		add("26", "Dadra and Nagar Haveli and Daman and Diu", "dnh", "dd", "daman and diu", "dadra and nagar haveli");
		add("27", "Maharashtra", "mh");
		add("29", "Karnataka", "ka");
		add("30", "Goa", "ga");
		add("31", "Lakshadweep", "ld");
		add("32", "Kerala", "kl");
		add("33", "Tamil Nadu", "tn");
		add("34", "Puducherry", "py", "pondicherry");
		add("35", "Andaman and Nicobar Islands", "an", "andaman & nicobar islands");
		add("36", "Telangana", "ts", "tg");
		add("37", "Andhra Pradesh", "ap");
		add("38", "Ladakh", "la");
	}

	private GstStates() {
	}

	private static void add(String code, String name, String... aliases) {
		State state = new State(code, name);
		BY_CODE.put(code, state);
		BY_NAME.put(key(name), state);
		for (String alias : aliases) {
			BY_NAME.put(key(alias), state);
		}
	}

	private static String key(String text) {
		return text.toLowerCase(Locale.ROOT).replace("&", "and").replaceAll("[^a-z0-9]", "");
	}

	public static Optional<State> byCode(String code) {
		return Optional.ofNullable(code == null ? null : BY_CODE.get(code.strip()));
	}

	/** A state from its name, a common short form ("MH") or its GST code ("27"). */
	public static Optional<State> byName(String text) {
		if (text == null || text.isBlank()) {
			return Optional.empty();
		}
		return byCode(text).or(() -> Optional.ofNullable(BY_NAME.get(key(text))));
	}

	/** The state a GSTIN is registered in (its first two digits). */
	public static Optional<State> ofGstin(String gstin) {
		return gstin == null || gstin.strip().length() < 2 ? Optional.empty() : byCode(gstin.strip().substring(0, 2));
	}

}
