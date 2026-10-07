package com.sealease.backend.role.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
public record UserRoleId(
		@Column(name = "user_id", nullable = false) UUID userId,
		@Column(name = "role_id", nullable = false) UUID roleId) implements Serializable {
}
