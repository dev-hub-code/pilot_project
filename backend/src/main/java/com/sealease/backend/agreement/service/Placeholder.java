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
	COMPANY_NAME("companyName", "SeaLease's legal name, as on invoices", "SeaLease Private Limited"),
	COMPANY_ADDRESS("companyAddress", "SeaLease's registered address, as on invoices", "Nariman Point, Mumbai 400021"),
	INVESTOR_NAME("investorName", "The investor's full name", "Asha Rao"),
	INVESTOR_CODE("investorCode", "The investor's readable ID", "SL03500"),
	INVESTOR_EMAIL("investorEmail", "The investor's email address", "asha.rao@example.com"),
	INVESTOR_ADDRESS("investorAddress", "The investor's address from their profile", "12 MG Road, 560001 Bengaluru, Karnataka, IN"),
	ORDER_NUMBER("orderNumber", "The order the container was bought in", "ORD-100042"),
	PLAN_CODE("planCode", "The container listing's code", "LST-10001"),
	PLAN_TITLE("planTitle", "The container listing's name", "20ft Dry container"),
	CONTAINER_NUMBER("containerNumber", "The container assigned", "SLSU1234565"),
	CONTAINER_TYPE("containerType", "The container's type", "20ft dry"),
	CONTAINER_LOCATION("containerLocation", "Where the container is, as recorded when assigned", "Mundra Port, Gujarat"),
	PURCHASE_PRICE("purchasePrice", "The price paid for the container", "₹2,50,000.00"),
	MONTHLY_RENT_PERCENT("monthlyRentPercent", "The monthly rent as a % of the price", "1.5%"),
	MONTHLY_RENT("monthlyRent", "The rent for a full month", "₹3,750.00"),
	TOTAL_RENT("totalRent", "The rent over the whole lease", "₹2,25,000.00"),
	TENURE_MONTHS("tenureMonths", "The lease in months", "60"),
	TENURE_YEARS("tenureYears", "The lease in years", "5"),
	LEASE_START_DATE("leaseStartDate",
			"The lease's first day; until the investor accepts, \"the day this Agreement is accepted\"", "17 October 2026"),
	LEASE_END_DATE("leaseEndDate",
			"The lease's last day; until the investor accepts, the day before the same date at the end of the term",
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
