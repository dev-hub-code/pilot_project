package com.sealease.backend.reporting.catalog;

/** How a report column's values are typed, aligned and formatted (money and numbers right-aligned). */
public enum ColumnType {
	TEXT,
	/** An enum constant, shown as a label ("PENDING_APPROVAL" → "Pending approval"). */
	STATUS,
	/** A decimal amount in the row's currency; never floating point. */
	MONEY,
	NUMBER,
	PERCENT,
	/** A calendar day. */
	DATE,
	/** A month, as its first day; shown as "Nov 2026". */
	MONTH,
	/** An instant, shown in the display time zone. */
	DATETIME;

	public boolean numeric() {
		return this == MONEY || this == NUMBER || this == PERCENT;
	}

}
