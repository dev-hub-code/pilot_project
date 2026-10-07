package com.sealease.backend.user.entity;

import com.sealease.backend.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A platform account (investor or staff). Personal profile, KYC and bank details live in their
 * own tables (Phase 3); this entity holds only what authentication needs.
 */
@Entity
@Table(name = "users")
public class User extends BaseEntity {

	@Column(name = "email", nullable = false, length = 254)
	private String email;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(name = "first_name", nullable = false, length = 100)
	private String firstName;

	@Column(name = "last_name", nullable = false, length = 100)
	private String lastName;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private UserStatus status;

	@Column(name = "failed_login_attempts", nullable = false)
	private int failedLoginAttempts;

	@Column(name = "locked_until")
	private Instant lockedUntil;

	@Column(name = "last_login_at")
	private Instant lastLoginAt;

	@Column(name = "password_changed_at", nullable = false)
	private Instant passwordChangedAt;

	@Column(name = "email_verified_at")
	private Instant emailVerifiedAt;

	@Column(name = "status_reason", length = 500)
	private String statusReason;

	@Column(name = "status_changed_at")
	private Instant statusChangedAt;

	@Column(name = "status_changed_by")
	private UUID statusChangedBy;

	@Column(name = "mfa_enabled", nullable = false)
	private boolean mfaEnabled;

	protected User() {
	}

	public User(String email, String passwordHash, String firstName, String lastName, Instant now) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.firstName = firstName;
		this.lastName = lastName;
		this.status = UserStatus.ACTIVE;
		this.passwordChangedAt = now;
	}

	public boolean isLockedAt(Instant now) {
		return lockedUntil != null && now.isBefore(lockedUntil);
	}

	/**
	 * Counts a failed sign-in and locks the account once {@code maxAttempts} is reached.
	 * @return {@code true} if this failure caused the account to become locked
	 */
	public boolean registerFailedLogin(int maxAttempts, Instant lockUntil) {
		failedLoginAttempts++;
		if (failedLoginAttempts >= maxAttempts) {
			failedLoginAttempts = 0;
			lockedUntil = lockUntil;
			return true;
		}
		return false;
	}

	public void registerSuccessfulLogin(Instant now) {
		failedLoginAttempts = 0;
		lockedUntil = null;
		lastLoginAt = now;
	}

	public void changeStatus(UserStatus newStatus, String reason, UUID actorId, Instant now) {
		this.status = newStatus;
		this.statusReason = reason;
		this.statusChangedBy = actorId;
		this.statusChangedAt = now;
	}

	public void rename(String firstName, String lastName) {
		this.firstName = firstName;
		this.lastName = lastName;
	}

	public void changePasswordHash(String newHash, Instant now) {
		this.passwordHash = newHash;
		this.passwordChangedAt = now;
	}

	/** Transparent re-hash when the encoding algorithm or its parameters are upgraded. */
	public void upgradePasswordHash(String newHash) {
		this.passwordHash = newHash;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public String getFirstName() {
		return firstName;
	}

	public String getLastName() {
		return lastName;
	}

	public UserStatus getStatus() {
		return status;
	}

	public int getFailedLoginAttempts() {
		return failedLoginAttempts;
	}

	public Instant getLockedUntil() {
		return lockedUntil;
	}

	public Instant getLastLoginAt() {
		return lastLoginAt;
	}

	public Instant getPasswordChangedAt() {
		return passwordChangedAt;
	}

	public Instant getEmailVerifiedAt() {
		return emailVerifiedAt;
	}

	public String getStatusReason() {
		return statusReason;
	}

	public Instant getStatusChangedAt() {
		return statusChangedAt;
	}

	public boolean isMfaEnabled() {
		return mfaEnabled;
	}

}
