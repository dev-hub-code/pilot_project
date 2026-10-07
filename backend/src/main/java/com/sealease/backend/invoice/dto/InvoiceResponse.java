package com.sealease.backend.invoice.dto;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.invoice.entity.Invoice;
import com.sealease.backend.invoice.entity.InvoiceLine;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InvoiceResponse(
		UUID id,
		String invoiceNumber,
		UUID orderId,
		String orderNumber,
		Instant issuedAt,
		Party issuer,
		Party buyer,
		List<Line> lines,
		MoneyResponse total,
		String notes) {

	public record Party(String name, String address, String taxId, String email) {
	}

	public record Line(int lineNumber, String description, MoneyResponse amount) {
	}

	public static InvoiceResponse from(Invoice invoice, String orderNumber, List<InvoiceLine> lines) {
		var currency = invoice.total().currency();
		return new InvoiceResponse(invoice.getId(), invoice.getInvoiceNumber(), invoice.getOrderId(), orderNumber,
				invoice.getIssuedAt(),
				new Party(invoice.issuer().name(), invoice.issuer().address(), invoice.getIssuerTaxId(), null),
				new Party(invoice.buyer().name(), invoice.buyer().address(), null, invoice.getBuyerEmail()),
				lines.stream()
					.map(l -> new Line(l.getLineNumber(), l.getDescription(),
							MoneyResponse.from(Money.of(l.getAmount(), currency))))
					.toList(),
				MoneyResponse.from(invoice.total()), invoice.getNotes());
	}

}
