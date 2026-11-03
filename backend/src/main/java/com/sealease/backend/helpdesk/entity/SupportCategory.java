package com.sealease.backend.helpdesk.entity;

import com.sealease.backend.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * A ticket category (top level: one per {@link TicketCategory}) or a configurable subcategory below one, with the
 * priority and team new tickets in it start with.
 */
@Entity
@Table(name = "support_categories")
public class SupportCategory extends BaseEntity {

	@Column(name = "parent_id", updatable = false)
	private UUID parentId;

	@Enumerated(EnumType.STRING)
	@Column(name = "category", nullable = false, updatable = false, length = 20)
	private TicketCategory category;

	@Column(name = "code", nullable = false, updatable = false, length = 40)
	private String code;

	@Column(name = "name", nullable = false, length = 80)
	private String name;

	@Column(name = "description", length = 255)
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(name = "default_priority", length = 10)
	private TicketPriority defaultPriority;

	@Enumerated(EnumType.STRING)
	@Column(name = "default_team", length = 20)
	private SupportTeam defaultTeam;

	@Column(name = "active", nullable = false)
	private boolean active = true;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	protected SupportCategory() {
	}

	public SupportCategory(UUID parentId, TicketCategory category, String code, String name) {
		this.parentId = parentId;
		this.category = category;
		this.code = code;
		this.name = name;
	}

	public void update(String name, String description, TicketPriority defaultPriority, SupportTeam defaultTeam,
			boolean active, int sortOrder) {
		this.name = name;
		this.description = description;
		this.defaultPriority = defaultPriority;
		this.defaultTeam = defaultTeam;
		this.active = active;
		this.sortOrder = sortOrder;
	}

	public boolean isTopLevel() {
		return parentId == null;
	}

	public UUID getParentId() {
		return parentId;
	}

	public TicketCategory getCategory() {
		return category;
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

	public TicketPriority getDefaultPriority() {
		return defaultPriority;
	}

	public SupportTeam getDefaultTeam() {
		return defaultTeam;
	}

	public boolean isActive() {
		return active;
	}

	public int getSortOrder() {
		return sortOrder;
	}

}
