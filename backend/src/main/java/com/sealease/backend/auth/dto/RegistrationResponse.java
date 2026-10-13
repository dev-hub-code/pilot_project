package com.sealease.backend.auth.dto;

/**
 * Result of signing up when the email address must be confirmed first: no session is opened until
 * the user follows the link emailed to {@code email}.
 */
public record RegistrationResponse(boolean emailVerificationRequired, String email) {
}
