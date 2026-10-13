package com.sealease.backend.common.tax;

import com.sealease.backend.common.money.Money;

/**
 * The GST on an amount, split by place of supply: within the seller's state half is CGST and half SGST (the
 * odd paisa goes to CGST); across states it is all IGST.
 */
public record GstSplit(Money cgst, Money sgst, Money igst) {

	public static GstSplit of(Money gst, boolean intraState) {
		Money zero = Money.zero(gst.currency());
		if (!intraState) {
			return new GstSplit(zero, zero, gst);
		}
		Money half = Money.of(gst.amount().divide(java.math.BigDecimal.TWO, gst.currency().getDefaultFractionDigits(),
				java.math.RoundingMode.HALF_UP), gst.currency());
		return new GstSplit(half, gst.minus(half), zero);
	}

	public Money total() {
		return cgst.plus(sgst).plus(igst);
	}

}
