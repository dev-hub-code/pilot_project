package com.sealease.backend.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/** A single-use refresh token. Only the SHA-256 hash of the token value is stored. */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.TIME)
	private UUID id;

	@Column(name = "session_id", nullable = false, updatable = false)
	private UUID sessionId;

	@Column(name = "token_hash", nullable = false, updatable = false, length = 64)
	private String tokenHash;

	@Column(name = "issued_at", nullable = false, updatable = false)
	private Instant issuedAt;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	@Column(name = "consumed_at")
	private Instant consumedAt;

	@Column(name = "replaced_by")
	private UUID replacedBy;

	@Version
	private long version;

	protected RefreshToken() {
	}

	public RefreshToken(UUID sessionId, String tokenHash, Instant issuedAt, Instant expiresAt) {
		this.sessionId = sessionId;
		this.tokenHash = tokenHash;
		this.issuedAt = issuedAt;
		this.expiresAt = expiresAt;
	}

	public boolean isConsumed() {
		return consumedAt != null;
	}

	public boolean isExpiredAt(Instant now) {
		return !now.isBefore(expiresAt);
	}

	public void consume(Instant now, UUID successorId) {
		this.consumedAt = now;
		this.replacedBy = successorId;
	}

	public UUID getId() {
		return id;
	}

	public UUID getSessionId() {
		return sessionId;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getConsumedAt() {
		return consumedAt;
	}

}
