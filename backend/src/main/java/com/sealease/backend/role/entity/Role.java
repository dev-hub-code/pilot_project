package com.sealease.backend.role.entity;

import com.sealease.backend.common.persistence.BaseEntity;
import com.sealease.backend.permission.entity.Permission;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "roles")
public class Role extends BaseEntity {

	@Column(name = "name", nullable = false, length = 64, updatable = false)
	private String name;

	@Column(name = "description", nullable = false)
	private String description;

	@Column(name = "system", nullable = false, updatable = false)
	private boolean system;

	@ManyToMany
	@JoinTable(name = "role_permissions",
			joinColumns = @JoinColumn(name = "role_id"),
			inverseJoinColumns = @JoinColumn(name = "permission_id"))
	private Set<Permission> permissions = new HashSet<>();

	protected Role() {
	}

	public Role(String name, String description, Set<Permission> permissions) {
		this.name = name;
		this.description = description;
		this.system = false;
		this.permissions = new HashSet<>(permissions);
	}

	public void update(String description, Set<Permission> permissions) {
		this.description = description;
		this.permissions.clear();
		this.permissions.addAll(permissions);
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public boolean isSystem() {
		return system;
	}

	public Set<Permission> getPermissions() {
		return Set.copyOf(permissions);
	}

}
