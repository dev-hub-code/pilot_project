package com.sealease.backend.crm.entity;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

/** A prospective investor in the sales pipeline. */
@Entity
@Table(name = "leads")
public class Lead extends BaseEntity {

	@Column(name = "reference", nullable = false, updatable = false, length = 20)
	private String reference;

	@Column(name = "first_name", nullable = false, length = 100)
	private String firstName;

	@Column(name = "last_name", length = 100)
	private String lastName;

	@Column(name = "email", length = 254)
	private String email;

	@Column(name = "phone", length = 40)
	private String phone;

	@Column(name = "country", length = 2)
	private String country;

	@Enumerated(EnumType.STRING)
	@Column(name = "source", nullable = false, updatable = false, length = 20)
	private LeadSource source;

	@Enumerated(EnumType.STRING)
	@Column(name = "interest", nullable = false, length = 20)
	private LeadInterest interest;

	@Column(name = "estimated_amount", precision = 19, scale = 4)
	private BigDecimal estimatedAmount;

	@Column(name = "estimated_currency", length = 3)
	private String estimatedCurrency;

	@Column(name = "message", updatable = false, length = 2000)
	private String message;

	@Enumerated(EnumType.STRING)
	@Column(name = "stage", nullable = false, length = 20)
	private LeadStage stage;

	@Column(name = "lost_reason", length = 500)
	private String lostReason;

	@Column(name = "owner_id")
	private UUID ownerId;

	@Column(name = "user_id")
	private UUID userId;

	@Column(name = "won_amount", precision = 19, scale = 4)
	private BigDecimal wonAmount;

	@Column(name = "won_currency", length = 3)
	private String wonCurrency;

	@Column(name = "won_order_id")
	private UUID wonOrderId;

	@Column(name = "closed_at")
	private Instant closedAt;

	@Column(name = "next_follow_up_at")
	private Instant nextFollowUpAt;

	@Column(name = "consent_at", updatable = false)
	private Instant consentAt;

	@Column(name = "created_by", updatable = false)
	private UUID createdBy;

	protected Lead() {
	}

	public Lead(String reference, LeadSource source, Contact contact, String message, UUID createdBy,
			Instant consentAt) {
		this.reference = reference;
		this.source = source;
		this.message = message;
		this.createdBy = createdBy;
		this.consentAt = consentAt;
		this.stage = LeadStage.NEW;
		update(contact);
	}

	/** Contact and qualification details, as entered. {@code email} must already be normalised. */
	public record Contact(String firstName, String lastName, String email, String phone, String country,
			LeadInterest interest, Money estimate) {
	}

	public void update(Contact c) {
		this.firstName = c.firstName();
		this.lastName = c.lastName();
		this.email = c.email();
		this.phone = c.phone();
		this.country = c.country();
		this.interest = c.interest();
		this.estimatedAmount = c.estimate() == null ? null : c.estimate().amount();
		this.estimatedCurrency = c.estimate() == null ? null : c.estimate().currency().getCurrencyCode();
	}

	public void moveTo(LeadStage target, String lostReason, Instant now) {
		this.stage = target;
		this.lostReason = target == LeadStage.LOST ? lostReason : null;
		this.closedAt = target.isOpen() ? null : now;
	}

	/** The linked account's first investment was confirmed. */
	public void win(Money amount, UUID orderId, Instant now) {
		moveTo(LeadStage.WON, null, now);
		this.wonAmount = amount.amount();
		this.wonCurrency = amount.currency().getCurrencyCode();
		this.wonOrderId = orderId;
	}

	public void assignTo(UUID ownerId) {
		this.ownerId = ownerId;
	}

	public void linkAccount(UUID userId) {
		this.userId = userId;
	}

	public void scheduleFollowUp(Instant at) {
		this.nextFollowUpAt = at;
	}

	public Contact contact() {
		return new Contact(firstName, lastName, email, phone, country, interest, estimatedAmount == null ? null
				: Money.of(estimatedAmount, Currency.getInstance(estimatedCurrency)));
	}

	public Money won() {
		return wonAmount == null ? null : Money.of(wonAmount, Currency.getInstance(wonCurrency));
	}

	public String getReference() {
		return reference;
	}

	public String getEmail() {
		return email;
	}

	public LeadSource getSource() {
		return source;
	}

	public String getMessage() {
		return message;
	}

	public LeadStage getStage() {
		return stage;
	}

	public String getLostReason() {
		return lostReason;
	}

	public UUID getOwnerId() {
		return ownerId;
	}

	public UUID getUserId() {
		return userId;
	}

	public UUID getWonOrderId() {
		return wonOrderId;
	}

	public Instant getClosedAt() {
		return closedAt;
	}

	public Instant getNextFollowUpAt() {
		return nextFollowUpAt;
	}

	public Instant getConsentAt() {
		return consentAt;
	}

	public UUID getCreatedBy() {
		return createdBy;
	}

}
