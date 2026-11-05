package com.sealease.backend.withdrawal.dto;

import java.time.Instant;
import java.util.UUID;

/** A staff member's internal note on a withdrawal. Never shown to the investor. */
public record WithdrawalNoteResponse(UUID id, UUID authorId, String authorName, String body, Instant createdAt) {
}
