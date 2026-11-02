package com.sealease.backend.department.entity;

import com.sealease.backend.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/** A part of the organisation. Belonging to one organises staff; it never grants a permission. */
@Entity
@Table(name = "departments")
public class Department extends BaseEntity {

	@Column(name = "code", nullable = false, length = 64, updatable = false)
	private String code;

	@Column(name = "name", nullable = false, length = 120)
	private String name;

	@Column(name = "description", nullable = false)
	private String description;

	@Column(name = "active", nullable = false)
	private boolean active = true;

	@Column(name = "head_user_id")
	private UUID headUserId;

	protected Department() {
	}

	public Department(String code, String name, String description) {
		this.code = code;
		this.name = name;
		this.description = description;
	}

	public void update(String newName, String newDescription) {
		this.name = newName;
		this.description = newDescription;
	}

	public void setActive(boolean value) {
		this.active = value;
	}

	public void setHeadUserId(UUID value) {
		this.headUserId = value;
	}

	public String getCode() {
		return code;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public boolean isActive() {
		return active;
	}

	public UUID getHeadUserId() {
		return headUserId;
	}

}
