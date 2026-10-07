package com.sealease.backend.crm.dto;

import java.util.UUID;

/** @param ownerId {@code null} returns the lead to the unassigned pool */
public record AssignRequest(UUID ownerId) {
}
