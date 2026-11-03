package com.sealease.backend.auth.dto;

import java.time.Instant;

/** What the sign-up page for an invitation shows: the invited address (fixed) and the name to start from. */
public record InvitationDetails(String email, String firstName, String lastName, String invitedBy, Instant expiresAt) {
}
