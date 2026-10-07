package com.sealease.backend.helpdesk.dto;

import java.util.UUID;

/** A staff member who can handle tickets. */
public record Agent(UUID userId, String name, String email) {
}
