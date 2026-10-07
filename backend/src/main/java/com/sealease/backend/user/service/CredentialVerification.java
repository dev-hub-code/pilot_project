package com.sealease.backend.user.service;

import com.sealease.backend.user.dto.UserAccount;

import java.time.Instant;

/** Outcome of a sign-in attempt. Returned rather than thrown so that failure counters commit. */
public sealed interface CredentialVerification {

	record Verified(UserAccount account) implements CredentialVerification {
	}

	record InvalidCredentials() implements CredentialVerification {
	}

	record Locked(Instant lockedUntil) implements CredentialVerification {
	}

	record Inactive() implements CredentialVerification {
	}

}
