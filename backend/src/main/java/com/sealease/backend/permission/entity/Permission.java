package com.sealease.backend.permission.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.util.UUID;

/** Catalogue entry; rows are managed by migrations only. */
@Entity
@Immutable
@Table(name = "permissions")
public class Permission {

	@Id
	private UUID id;

	@Column(name = "code", nullable = false, length = 64)
	private String code;

	@Column(name = "category", nullable = false, length = 32)
	private String category;

	@Column(name = "description", nullable = false)
	private String description;

	@Column(name = "sensitive", nullable = false)
	private boolean sensitive;

	/** Listed for future use: no endpoint checks it yet, so granting it changes nothing. */
	@Column(name = "reserved", nullable = false)
	private boolean reserved;

	protected Permission() {
	}

	public UUID getId() {
		return id;
	}

	public String getCode() {
		return code;
	}

	public String getCategory() {
		return category;
	}

	public boolean isSensitive() {
		return sensitive;
	}

	public boolean isReserved() {
		return reserved;
	}

	public String getDescription() {
		return description;
	}

	@Override
	public boolean equals(Object o) {
		return this == o || (o instanceof Permission other && id != null && id.equals(other.getId()));
	}

	@Override
	public int hashCode() {
		return Permission.class.hashCode();
	}

}
