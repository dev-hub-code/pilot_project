package com.sealease.backend.helpdesk.entity;

import com.sealease.backend.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnTransformer;

import java.util.UUID;

/** A configurable support automation: a trigger, conditions (plain values) and one action. */
@Entity
@Table(name = "support_automation_rules")
public class AutomationRule extends BaseEntity {

	@Column(name = "name", nullable = false, length = 120)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "trigger", nullable = false, length = 30)
	private AutomationTrigger trigger;

	/** JSON object of plain values (see {@code AutomationConditions}). */
	@ColumnTransformer(write = "CAST(? AS jsonb)")
	@Column(name = "conditions", nullable = false, columnDefinition = "jsonb")
	private String conditions = "{}";

	@Enumerated(EnumType.STRING)
	@Column(name = "action", nullable = false, length = 30)
	private AutomationAction action;

	@ColumnTransformer(write = "CAST(? AS jsonb)")
	@Column(name = "parameters", nullable = false, columnDefinition = "jsonb")
	private String parameters = "{}";

	@Column(name = "enabled", nullable = false)
	private boolean enabled = true;

	@Column(name = "updated_by")
	private UUID updatedBy;

	protected AutomationRule() {
	}

	public AutomationRule(String name, AutomationTrigger trigger, AutomationAction action) {
		this.name = name;
		this.trigger = trigger;
		this.action = action;
	}

	public void update(String name, AutomationTrigger trigger, String conditions, AutomationAction action,
			String parameters, boolean enabled, UUID updatedBy) {
		this.name = name;
		this.trigger = trigger;
		this.conditions = conditions;
		this.action = action;
		this.parameters = parameters;
		this.enabled = enabled;
		this.updatedBy = updatedBy;
	}

	public String getName() {
		return name;
	}

	public AutomationTrigger getTrigger() {
		return trigger;
	}

	public String getConditions() {
		return conditions;
	}

	public AutomationAction getAction() {
		return action;
	}

	public String getParameters() {
		return parameters;
	}

	public boolean isEnabled() {
		return enabled;
	}

}
