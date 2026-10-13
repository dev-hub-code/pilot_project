package com.sealease.backend.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/** A single-use link emailed to confirm an address. Only the SHA-256 hash of the token value is stored. */
@Entity
@Table(name = "email_verification_tokens")
public class EmailVerificationToken {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.TIME)
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "token_hash", nullable = false, updatable = false, length = 64)
	private String tokenHash;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	@Column(name = "used_at")
	private Instant usedAt;

	/** Hash of the 6-digit code in the same email; {@code null} for links sent before codes existed. */
	@Column(name = "code_hash", updatable = false, length = 64)
	private String codeHash;

	@Column(name = "failed_attempts", nullable = false)
	private int failedAttempts;

	protected EmailVerificationToken() {
	}

	public EmailVerificationToken(UUID userId, String tokenHash, String codeHash, Instant createdAt, Instant expiresAt) {
		this.userId = userId;
		this.tokenHash = tokenHash;
		this.codeHash = codeHash;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
	}

	public boolean isUsableAt(Instant now) {
		return usedAt == null && now.isBefore(expiresAt);
	}

	public void markUsed(Instant now) {
		usedAt = now;
	}

	public boolean hasCodeHash(String hash) {
		return codeHash != null && codeHash.equals(hash);
	}

	/**
	 * Counts a wrong code; after {@code maxAttempts} the email stops working (link included).
	 *
	 * @return {@code true} if this guess used up the last attempt
	 */
	public boolean registerFailedCode(int maxAttempts, Instant now) {
		failedAttempts++;
		if (failedAttempts >= maxAttempts) {
			usedAt = now;
			return true;
		}
		return false;
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getUsedAt() {
		return usedAt;
	}

}
