package com.sealease.backend.invoice.dto;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.invoice.entity.Invoice;
import com.sealease.backend.invoice.entity.InvoiceLine;

import java.math.BigDecimal;
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
		MoneyResponse taxable,
		MoneyResponse cgst,
		MoneyResponse sgst,
		MoneyResponse igst,
		String placeOfSupply,
		MoneyResponse total,
		String notes) {

	public record Party(String name, String address, String taxId, String email) {
	}

	/**
	 * @param amount the taxable value; {@code total} adds its GST
	 */
	public record Line(int lineNumber, String description, MoneyResponse amount, BigDecimal gstRatePercent,
			MoneyResponse cgst, MoneyResponse sgst, MoneyResponse igst, MoneyResponse total) {
	}

	public static InvoiceResponse from(Invoice invoice, String orderNumber, List<InvoiceLine> lines) {
		var currency = invoice.total().currency();
		Invoice.Tax tax = invoice.tax();
		return new InvoiceResponse(invoice.getId(), invoice.getInvoiceNumber(), invoice.getOrderId(), orderNumber,
				invoice.getIssuedAt(),
				new Party(invoice.issuer().name(), invoice.issuer().address(), invoice.getIssuerTaxId(), null),
				new Party(invoice.buyer().name(), invoice.buyer().address(), tax.buyerGstin(), invoice.getBuyerEmail()),
				lines.stream()
					.map(l -> {
						Money amount = Money.of(l.getAmount(), currency);
						Money cgst = Money.of(l.getCgstAmount(), currency);
						Money sgst = Money.of(l.getSgstAmount(), currency);
						Money igst = Money.of(l.getIgstAmount(), currency);
						return new Line(l.getLineNumber(), l.getDescription(), MoneyResponse.from(amount),
								l.getGstRatePercent(), MoneyResponse.from(cgst), MoneyResponse.from(sgst),
								MoneyResponse.from(igst), MoneyResponse.from(amount.plus(cgst).plus(sgst).plus(igst)));
					})
					.toList(),
				MoneyResponse.from(tax.taxable()), MoneyResponse.from(tax.cgst()), MoneyResponse.from(tax.sgst()),
				MoneyResponse.from(tax.igst()), tax.placeOfSupply(), MoneyResponse.from(invoice.total()), invoice.getNotes());
	}

}
