package com.sealease.backend.auth.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * An invitation just sent.
 *
 * @param emailed whether the email was handed to the mail server; when false the invitation exists but the client
 *                has no link, and the employee should try again
 */
public record InvitationResponse(UUID id, String email, Instant expiresAt, boolean emailed) {
}
