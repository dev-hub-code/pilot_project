package com.sealease.backend.invoice.service;

import com.sealease.backend.common.money.Money;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** What order confirmation asks to be invoiced. The total is the sum of the lines with their GST. */
public record InvoiceRequest(UUID orderId, String orderNumber, UUID userId, UUID paymentId, List<Line> lines) {

	public InvoiceRequest {
		lines = List.copyOf(lines);
		if (lines.isEmpty()) {
			throw new IllegalArgumentException("An invoice needs at least one line");
		}
	}

	/**
	 * @param amount the taxable value (the container's price)
	 * @param gst    GST charged on it at {@code gstRatePercent}; split into CGST/SGST or IGST on the invoice
	 */
	public record Line(String description, Money amount, BigDecimal gstRatePercent, Money gst) {
	}

	/** The taxable value: the lines without GST. */
	public Money taxable() {
		return lines.stream().map(Line::amount).reduce(Money::plus).orElseThrow();
	}

	public Money total() {
		return lines.stream().map(l -> l.amount().plus(l.gst())).reduce(Money::plus).orElseThrow();
	}

}
