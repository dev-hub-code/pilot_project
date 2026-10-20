package com.sealease.backend.agreement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** The investor's acceptance of their lease agreement, and the lease it started. Immutable, one per agreement. */
@Entity
@Immutable
@Table(name = "lease_agreement_acceptances")
public class AgreementAcceptance {

	@Id
	@Column(name = "agreement_id")
	private UUID agreementId;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "accepted_at", nullable = false)
	private Instant acceptedAt;

	@Column(name = "lease_starts_on", nullable = false)
	private LocalDate leaseStartsOn;

	/** The day after the lease's last day (exclusive), as holdings record it. */
	@Column(name = "lease_ends_on", nullable = false)
	private LocalDate leaseEndsOn;

	@Column(name = "ip_address", length = 45)
	private String ipAddress;

	@Column(name = "user_agent", length = 512)
	private String userAgent;

	protected AgreementAcceptance() {
	}

	public AgreementAcceptance(UUID agreementId, UUID userId, Instant acceptedAt, LocalDate leaseStartsOn,
			LocalDate leaseEndsOn, String ipAddress, String userAgent) {
		this.agreementId = agreementId;
		this.userId = userId;
		this.acceptedAt = acceptedAt;
		this.leaseStartsOn = leaseStartsOn;
		this.leaseEndsOn = leaseEndsOn;
		this.ipAddress = ipAddress;
		this.userAgent = userAgent;
	}

	public UUID getAgreementId() {
		return agreementId;
	}

	public UUID getUserId() {
		return userId;
	}

	public Instant getAcceptedAt() {
		return acceptedAt;
	}

	public LocalDate getLeaseStartsOn() {
		return leaseStartsOn;
	}

	public LocalDate getLeaseEndsOn() {
		return leaseEndsOn;
	}

	public String getIpAddress() {
		return ipAddress;
	}

	public String getUserAgent() {
		return userAgent;
	}

}
