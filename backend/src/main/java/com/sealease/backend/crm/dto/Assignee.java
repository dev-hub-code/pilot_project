package com.sealease.backend.crm.dto;

import java.util.UUID;

/** A staff member who can work leads. */
public record Assignee(UUID userId, String name, String email) {
}
