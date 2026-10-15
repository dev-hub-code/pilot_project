package com.sealease.backend.common.api;

import java.time.Instant;
import java.util.UUID;

/** The staff member a review task is assigned to, and since when. */
public record Assignee(UUID id, String name, Instant assignedAt) {
}
