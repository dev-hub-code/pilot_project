package com.sealease.backend.user.event;

import java.util.UUID;

/**
 * Published in-process when an administrator replaces a user's password with a temporary one,
 * inside that transaction: every existing session of the user must end.
 */
public record CredentialsResetEvent(UUID userId) {
}
