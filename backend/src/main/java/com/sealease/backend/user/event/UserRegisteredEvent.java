package com.sealease.backend.user.event;

import java.util.UUID;

/**
 * Published in-process when an account is self-registered, inside the registration transaction.
 * Listeners that must not be able to fail a sign-up use {@code @TransactionalEventListener}.
 *
 * @param email normalised (lower-case)
 */
public record UserRegisteredEvent(UUID userId, String email) {
}
