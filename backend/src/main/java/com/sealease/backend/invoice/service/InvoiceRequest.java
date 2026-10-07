package com.sealease.backend.invoice.service;

import com.sealease.backend.common.money.Money;

import java.util.List;
import java.util.UUID;

/** What order confirmation asks to be invoiced. The total is the sum of the lines. */
public record InvoiceRequest(UUID orderId, String orderNumber, UUID userId, UUID paymentId, List<Line> lines) {

	public InvoiceRequest {
		lines = List.copyOf(lines);
		if (lines.isEmpty()) {
			throw new IllegalArgumentException("An invoice needs at least one line");
		}
	}

	public record Line(String description, Money amount) {
	}

	public Money total() {
		return lines.stream().map(Line::amount).reduce(Money::plus).orElseThrow();
	}

}
