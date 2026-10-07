package com.sealease.backend.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/** A signed-in device/browser. Its id is the {@code sid} claim of every access token it receives. */
@Entity
@Table(name = "auth_sessions")
public class AuthSession {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.TIME)
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	@Column(name = "last_used_at", nullable = false)
	private Instant lastUsedAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "revoked_reason", length = 40)
	private SessionRevocationReason revokedReason;

	@Column(name = "ip_address", length = 45, updatable = false)
	private String ipAddress;

	@Column(name = "user_agent", length = 512, updatable = false)
	private String userAgent;

	@Version
	private long version;

	protected AuthSession() {
	}

	public AuthSession(UUID userId, Instant now, Instant expiresAt, String ipAddress, String userAgent) {
		this.userId = userId;
		this.createdAt = now;
		this.expiresAt = expiresAt;
		this.lastUsedAt = now;
		this.ipAddress = ipAddress;
		this.userAgent = userAgent;
	}

	public boolean isActiveAt(Instant now) {
		return revokedAt == null && now.isBefore(expiresAt);
	}

	public void revoke(SessionRevocationReason reason, Instant now) {
		if (revokedAt == null) {
			revokedAt = now;
			revokedReason = reason;
		}
	}

	public void touch(Instant now) {
		lastUsedAt = now;
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

	public Instant getRevokedAt() {
		return revokedAt;
	}

	public SessionRevocationReason getRevokedReason() {
		return revokedReason;
	}

}
