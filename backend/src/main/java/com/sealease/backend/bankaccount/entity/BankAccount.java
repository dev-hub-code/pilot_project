package com.sealease.backend.bankaccount.entity;

import com.sealease.backend.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A payout destination. Account number and routing code are stored encrypted only. */
@Entity
@Table(name = "bank_accounts")
public class BankAccount extends BaseEntity {

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "account_holder_name", nullable = false, length = 140, updatable = false)
	private String accountHolderName;

	@Column(name = "bank_name", nullable = false, length = 140, updatable = false)
	private String bankName;

	@Column(name = "country", nullable = false, length = 2, updatable = false)
	private String country;

	@Column(name = "currency", nullable = false, length = 3, updatable = false)
	private String currency;

	@Column(name = "account_number_encrypted", nullable = false, updatable = false)
	private String accountNumberEncrypted;

	@Column(name = "account_number_last4", nullable = false, length = 4, updatable = false)
	private String accountNumberLast4;

	@Column(name = "account_fingerprint", nullable = false, length = 64, updatable = false)
	private String accountFingerprint;

	@Column(name = "routing_code_encrypted", nullable = false, updatable = false)
	private String routingCodeEncrypted;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 25)
	private BankAccountStatus status;

	@Column(name = "is_primary", nullable = false)
	private boolean primary;

	@Column(name = "verified_by")
	private UUID verifiedBy;

	@Column(name = "verified_at")
	private Instant verifiedAt;

	@Column(name = "rejection_reason", length = 500)
	private String rejectionReason;

	@Column(name = "removed_at")
	private Instant removedAt;

	protected BankAccount() {
	}

	public BankAccount(UUID userId, String accountHolderName, String bankName, String country, String currency,
			String accountNumberEncrypted, String accountNumberLast4, String accountFingerprint,
			String routingCodeEncrypted) {
		this.userId = userId;
		this.accountHolderName = accountHolderName;
		this.bankName = bankName;
		this.country = country;
		this.currency = currency;
		this.accountNumberEncrypted = accountNumberEncrypted;
		this.accountNumberLast4 = accountNumberLast4;
		this.accountFingerprint = accountFingerprint;
		this.routingCodeEncrypted = routingCodeEncrypted;
		this.status = BankAccountStatus.PENDING_VERIFICATION;
	}

	public boolean isActive() {
		return status != BankAccountStatus.REMOVED;
	}

	public void verify(UUID verifier, Instant now) {
		this.status = BankAccountStatus.VERIFIED;
		this.verifiedBy = verifier;
		this.verifiedAt = now;
		this.rejectionReason = null;
	}

	public void reject(UUID reviewer, String reason) {
		this.status = BankAccountStatus.REJECTED;
		this.verifiedBy = reviewer;
		this.rejectionReason = reason;
		this.primary = false;
	}

	public void remove(Instant now) {
		this.status = BankAccountStatus.REMOVED;
		this.removedAt = now;
		this.primary = false;
	}

	public void setPrimary(boolean primary) {
		this.primary = primary;
	}

	public UUID getUserId() {
		return userId;
	}

	public String getAccountHolderName() {
		return accountHolderName;
	}

	public String getBankName() {
		return bankName;
	}

	public String getCountry() {
		return country;
	}

	public String getCurrency() {
		return currency;
	}

	public String getAccountNumberEncrypted() {
		return accountNumberEncrypted;
	}

	public String getRoutingCodeEncrypted() {
		return routingCodeEncrypted;
	}

	public String getAccountNumberLast4() {
		return accountNumberLast4;
	}

	public String getAccountFingerprint() {
		return accountFingerprint;
	}

	public BankAccountStatus getStatus() {
		return status;
	}

	public boolean isPrimary() {
		return primary;
	}

	public UUID getVerifiedBy() {
		return verifiedBy;
	}

	public Instant getVerifiedAt() {
		return verifiedAt;
	}

	public String getRejectionReason() {
		return rejectionReason;
	}

}
