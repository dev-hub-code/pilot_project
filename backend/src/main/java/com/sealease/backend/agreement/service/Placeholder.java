package com.sealease.backend.agreement.service;

import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What an agreement template may say about the investment, written {@code {{investorName}}}. Templates may use only
 * these, so a typo is caught when the template is saved rather than printed on an investor's agreement.
 */
public enum Placeholder {

	AGREEMENT_NUMBER("agreementNumber", "The agreement's number", "AGR-2026-000042"),
	AGREEMENT_DATE("agreementDate", "The day the agreement was issued", "17 October 2026"),
	AGREEMENT_VERSION("agreementVersion", "The version of this wording the agreement was issued with", "3"),
	CURRENT_YEAR("currentYear", "The year the agreement was issued", "2026"),
	ACCEPTANCE_STATUS("acceptanceStatus",
			"Whether the investor has accepted: \"Accepted\", \"Awaiting acceptance\" or \"Not required\"", "Accepted"),
	ACCEPTED_AT("acceptedAt", "When the investor accepted; until then, \"Not yet accepted\"", "17 October 2026, 14:05 IST"),
	ACCEPTANCE_REFERENCE("acceptanceReference",
			"The acceptance's reference (the agreement number with ACC); until accepted, \"Pending\"", "ACC-2026-000042"),
	COMPANY_NAME("companyName", "The company's legal name, as on invoices", "Leasefleet Solutions Pvt. Ltd."),
	COMPANY_ADDRESS("companyAddress", "The company's registered address, as on invoices", "Akhade, Humgaon, Satara 415514"),
	INVESTOR_NAME("investorName", "The investor's full name", "Asha Rao"),
	INVESTOR_CODE("investorCode", "The investor's readable ID", "SL03500"),
	INVESTOR_EMAIL("investorEmail", "The investor's email address", "asha.rao@example.com"),
	INVESTOR_ADDRESS("investorAddress", "The investor's address from their profile", "12 MG Road, 560001 Bengaluru, Karnataka, IN"),
	ORDER_NUMBER("orderNumber", "The order the investment was made in", "ORD-100042"),
	PLAN_CODE("planCode", "The plan's code", "LST-10001"),
	PLAN_TITLE("planTitle", "The plan's name", "Growth plan"),
	PURCHASE_PRICE("investmentAmount", "The amount invested", "₹2,50,000.00"),
	ANNUAL_RETURNS_PERCENT("annualReturnsPercent", "The returns a year as a % of the investment", "15%"),
	MONTHLY_RENT_PERCENT("monthlyReturnsPercent", "The monthly returns as a % of the investment", "1.25%"),
	MONTHLY_RENT("monthlyReturns", "The returns for a full month", "₹3,750.00"),
	TOTAL_RENT("totalReturns", "The returns over the whole term", "₹2,25,000.00"),
	TENURE_MONTHS("tenureMonths", "The term in months", "60"),
	TENURE_YEARS("tenureYears", "The term in years", "5"),
	LEASE_START_DATE("startDate",
			"The term's first day; until the investor accepts, \"the day this Agreement is accepted\"", "17 October 2026"),
	LEASE_END_DATE("endDate",
			"The term's last day; until the investor accepts, the day before the same date at the end of the term",
			"16 October 2031");

	private static final Pattern TOKEN = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_]+)\\s*}}");

	private final String key;
	private final String description;
	private final String example;

	Placeholder(String key, String description, String example) {
		this.key = key;
		this.description = description;
		this.example = example;
	}

	public String key() {
		return key;
	}

	public String description() {
		return description;
	}

	public String example() {
		return example;
	}

	public static Optional<Placeholder> byKey(String key) {
		return Arrays.stream(values()).filter(p -> p.key.equals(key)).findFirst();
	}

	/** Rejects placeholders that are not in the catalogue, naming them. */
	static void requireKnown(String text) {
		Set<String> unknown = new TreeSet<>();
		Matcher m = TOKEN.matcher(text);
		while (m.find()) {
			if (byKey(m.group(1)).isEmpty()) {
				unknown.add("{{" + m.group(1) + "}}");
			}
		}
		if (!unknown.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Unknown placeholder" + (unknown.size() == 1 ? " " : "s ") + String.join(", ", unknown)
							+ ": use only the placeholders listed");
		}
	}

	/** Replaces every placeholder with its value; one without a value is left as written. */
	static String fill(String text, Map<Placeholder, String> values) {
		return TOKEN.matcher(text).replaceAll(m -> Matcher.quoteReplacement(byKey(m.group(1))
			.map(values::get)
			.orElse(m.group())));
	}

	/** Every placeholder with its example value, for previews. */
	static Map<Placeholder, String> examples() {
		Map<Placeholder, String> values = new java.util.EnumMap<>(Placeholder.class);
		for (Placeholder p : values()) {
			values.put(p, p.example);
		}
		return values;
	}

}
