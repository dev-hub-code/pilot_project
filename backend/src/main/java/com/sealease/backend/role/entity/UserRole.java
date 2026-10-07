package com.sealease.backend.role.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Grant of a role to a user. Rows are inserted or deleted, never updated. */
@Entity
@Table(name = "user_roles")
public class UserRole {

	@EmbeddedId
	private UserRoleId id;

	@MapsId("roleId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "role_id")
	private Role role;

	@Column(name = "assigned_by", updatable = false)
	private UUID assignedBy;

	@Column(name = "assigned_at", nullable = false, updatable = false)
	private Instant assignedAt;

	protected UserRole() {
	}

	public UserRole(UUID userId, Role role, UUID assignedBy, Instant assignedAt) {
		this.id = new UserRoleId(userId, role.getId());
		this.role = role;
		this.assignedBy = assignedBy;
		this.assignedAt = assignedAt;
	}

	public UserRoleId getId() {
		return id;
	}

	public Role getRole() {
		return role;
	}

	public UUID getAssignedBy() {
		return assignedBy;
	}

	public Instant getAssignedAt() {
		return assignedAt;
	}

}
