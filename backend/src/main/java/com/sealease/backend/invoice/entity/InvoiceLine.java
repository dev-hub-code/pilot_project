package com.sealease.backend.invoice.entity;

import com.sealease.backend.common.tax.GstSplit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "invoice_lines")
public class InvoiceLine {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.TIME)
	private UUID id;

	@Column(name = "invoice_id", nullable = false)
	private UUID invoiceId;

	@Column(name = "line_number", nullable = false)
	private int lineNumber;

	@Column(name = "description", nullable = false, length = 300)
	private String description;

	/** The taxable value of the line, without GST. */
	@Column(name = "amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal amount;

	@Column(name = "gst_rate_percent", nullable = false, precision = 5, scale = 2)
	private BigDecimal gstRatePercent;

	@Column(name = "cgst_amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal cgstAmount;

	@Column(name = "sgst_amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal sgstAmount;

	@Column(name = "igst_amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal igstAmount;

	protected InvoiceLine() {
	}

	public InvoiceLine(UUID invoiceId, int lineNumber, String description, BigDecimal amount, BigDecimal gstRatePercent,
			GstSplit gst) {
		this.invoiceId = invoiceId;
		this.lineNumber = lineNumber;
		this.description = description;
		this.amount = amount;
		this.gstRatePercent = gstRatePercent;
		this.cgstAmount = gst.cgst().amount();
		this.sgstAmount = gst.sgst().amount();
		this.igstAmount = gst.igst().amount();
	}

	public BigDecimal getGstRatePercent() {
		return gstRatePercent;
	}

	public BigDecimal getCgstAmount() {
		return cgstAmount;
	}

	public BigDecimal getSgstAmount() {
		return sgstAmount;
	}

	public BigDecimal getIgstAmount() {
		return igstAmount;
	}

	public int getLineNumber() {
		return lineNumber;
	}

	public String getDescription() {
		return description;
	}

	public BigDecimal getAmount() {
		return amount;
	}

}
