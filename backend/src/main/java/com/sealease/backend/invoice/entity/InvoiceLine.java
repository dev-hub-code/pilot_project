package com.sealease.backend.invoice.entity;

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

	@Column(name = "amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal amount;

	protected InvoiceLine() {
	}

	public InvoiceLine(UUID invoiceId, int lineNumber, String description, BigDecimal amount) {
		this.invoiceId = invoiceId;
		this.lineNumber = lineNumber;
		this.description = description;
		this.amount = amount;
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
