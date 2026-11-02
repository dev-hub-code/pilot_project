package com.sealease.backend.role.dto;

import java.time.Instant;
import java.util.List;

public record RoleRevisionResponse(long revision, String description, List<String> permissions, String changedBy,
		Instant changedAt) {
}
