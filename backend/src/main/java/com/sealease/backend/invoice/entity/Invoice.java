package com.sealease.backend.invoice.entity;

import com.sealease.backend.common.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

/** An issued invoice. Immutable: a mistake is corrected by a credit note, never by an edit. */
@Entity
@Immutable
@Table(name = "invoices")
public class Invoice {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.TIME)
	private UUID id;

	@Column(name = "invoice_number", nullable = false, length = 30)
	private String invoiceNumber;

	@Column(name = "order_id", nullable = false)
	private UUID orderId;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "payment_id", nullable = false)
	private UUID paymentId;

	@Column(name = "currency", nullable = false, length = 3)
	private String currency;

	@Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal totalAmount;

	@Column(name = "issuer_name", nullable = false, length = 200)
	private String issuerName;

	@Column(name = "issuer_address", nullable = false, length = 500)
	private String issuerAddress;

	@Column(name = "issuer_tax_id", length = 50)
	private String issuerTaxId;

	@Column(name = "buyer_name", nullable = false, length = 200)
	private String buyerName;

	@Column(name = "buyer_email", nullable = false, length = 320)
	private String buyerEmail;

	@Column(name = "buyer_address", length = 500)
	private String buyerAddress;

	@Column(name = "notes", length = 1000)
	private String notes;

	@Column(name = "issued_at", nullable = false)
	private Instant issuedAt;

	protected Invoice() {
	}

	public Invoice(String invoiceNumber, UUID orderId, UUID userId, UUID paymentId, Money total, Party issuer,
			String issuerTaxId, Party buyer, String buyerEmail, String notes, Instant issuedAt) {
		this.invoiceNumber = invoiceNumber;
		this.orderId = orderId;
		this.userId = userId;
		this.paymentId = paymentId;
		this.currency = total.currency().getCurrencyCode();
		this.totalAmount = total.amount();
		this.issuerName = issuer.name();
		this.issuerAddress = issuer.address();
		this.issuerTaxId = issuerTaxId;
		this.buyerName = buyer.name();
		this.buyerEmail = buyerEmail;
		this.buyerAddress = buyer.address();
		this.notes = notes;
		this.issuedAt = issuedAt;
	}

	public record Party(String name, String address) {
	}

	public UUID getId() {
		return id;
	}

	public String getInvoiceNumber() {
		return invoiceNumber;
	}

	public UUID getOrderId() {
		return orderId;
	}

	public UUID getUserId() {
		return userId;
	}

	public UUID getPaymentId() {
		return paymentId;
	}

	public Money total() {
		return Money.of(totalAmount, Currency.getInstance(currency));
	}

	public Party issuer() {
		return new Party(issuerName, issuerAddress);
	}

	public String getIssuerTaxId() {
		return issuerTaxId;
	}

	public Party buyer() {
		return new Party(buyerName, buyerAddress);
	}

	public String getBuyerEmail() {
		return buyerEmail;
	}

	public String getNotes() {
		return notes;
	}

	public Instant getIssuedAt() {
		return issuedAt;
	}

}
