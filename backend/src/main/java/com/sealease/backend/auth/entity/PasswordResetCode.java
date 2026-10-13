package com.sealease.backend.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/** A "forgot password" code emailed to an account's address. Only a hash of the code is stored. */
@Entity
@Table(name = "password_reset_codes")
public class PasswordResetCode {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.TIME)
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "code_hash", nullable = false, updatable = false, length = 64)
	private String codeHash;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	@Column(name = "used_at")
	private Instant usedAt;

	@Column(name = "failed_attempts", nullable = false)
	private int failedAttempts;

	protected PasswordResetCode() {
	}

	public PasswordResetCode(UUID userId, String codeHash, Instant createdAt, Instant expiresAt) {
		this.userId = userId;
		this.codeHash = codeHash;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
	}

	public boolean isUsableAt(Instant now) {
		return usedAt == null && now.isBefore(expiresAt);
	}

	public boolean hasCodeHash(String hash) {
		return codeHash.equals(hash);
	}

	public void markUsed(Instant now) {
		usedAt = now;
	}

	/** @return {@code true} if this wrong guess used up the last attempt (the code then stops working) */
	public boolean registerFailedAttempt(int maxAttempts, Instant now) {
		failedAttempts++;
		if (failedAttempts >= maxAttempts) {
			usedAt = now;
			return true;
		}
		return false;
	}

	public UUID getUserId() {
		return userId;
	}

}
