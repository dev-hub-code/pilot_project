package com.sealease.backend.agreement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * The lease agreement issued for one holding when its container was assigned: the template in force then, filled in
 * with the investment's facts and kept word for word. Immutable, so later template changes never alter it.
 */
@Entity
@Immutable
@Table(name = "lease_agreements")
public class LeaseAgreement {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.TIME)
	private UUID id;

	@Column(name = "agreement_number", nullable = false, length = 30)
	private String agreementNumber;

	@Column(name = "holding_id", nullable = false)
	private UUID holdingId;

	@Column(name = "order_id", nullable = false)
	private UUID orderId;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "container_number", nullable = false, length = 20)
	private String containerNumber;

	@Column(name = "template_id", nullable = false)
	private UUID templateId;

	@Column(name = "title", nullable = false, length = 200)
	private String title;

	@Column(name = "body", nullable = false, columnDefinition = "TEXT")
	private String body;

	/** The investor must accept it before the lease starts; agreements for leases already running need not be. */
	@Column(name = "acceptance_required", nullable = false)
	private boolean acceptanceRequired;

	@Column(name = "generated_by")
	private UUID generatedBy;

	@Column(name = "generated_at", nullable = false)
	private Instant generatedAt;

	protected LeaseAgreement() {
	}

	public LeaseAgreement(String agreementNumber, UUID holdingId, UUID orderId, UUID userId, String containerNumber, UUID templateId, String title,
			String body, boolean acceptanceRequired, UUID generatedBy, Instant generatedAt) {
		this.agreementNumber = agreementNumber;
		this.holdingId = holdingId;
		this.orderId = orderId;
		this.userId = userId;
		this.containerNumber = containerNumber;
		this.templateId = templateId;
		this.title = title;
		this.body = body;
		this.acceptanceRequired = acceptanceRequired;
		this.generatedBy = generatedBy;
		this.generatedAt = generatedAt;
	}

	public UUID getId() {
		return id;
	}

	public String getAgreementNumber() {
		return agreementNumber;
	}

	public UUID getHoldingId() {
		return holdingId;
	}

	public UUID getOrderId() {
		return orderId;
	}

	public UUID getUserId() {
		return userId;
	}

	public String getContainerNumber() {
		return containerNumber;
	}

	public UUID getTemplateId() {
		return templateId;
	}

	public String getTitle() {
		return title;
	}

	public String getBody() {
		return body;
	}

	public boolean isAcceptanceRequired() {
		return acceptanceRequired;
	}

	public UUID getGeneratedBy() {
		return generatedBy;
	}

	public Instant getGeneratedAt() {
		return generatedAt;
	}

}
