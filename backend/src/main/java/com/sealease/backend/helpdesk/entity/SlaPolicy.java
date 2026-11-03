package com.sealease.backend.helpdesk.entity;

import com.sealease.backend.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * SLA targets for the tickets it matches. Each criterion left empty matches anything; the most specific active policy
 * applies. Targets count business time (the support calendar) or calendar time.
 */
@Entity
@Table(name = "support_sla_policies")
public class SlaPolicy extends BaseEntity {

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "priority", length = 10)
	private TicketPriority priority;

	@Enumerated(EnumType.STRING)
	@Column(name = "category", length = 20)
	private TicketCategory category;

	/** NETWORK or ONLINE investors. */
	@Column(name = "customer_type", length = 20)
	private String customerType;

	@Column(name = "first_response_minutes", nullable = false)
	private int firstResponseMinutes;

	@Column(name = "next_response_minutes")
	private Integer nextResponseMinutes;

	@Column(name = "resolution_minutes", nullable = false)
	private int resolutionMinutes;

	@Column(name = "business_hours", nullable = false)
	private boolean businessHours;

	@Column(name = "pause_while_waiting", nullable = false)
	private boolean pauseWhileWaiting;

	@Column(name = "active", nullable = false)
	private boolean active = true;

	@Column(name = "updated_by")
	private UUID updatedBy;

	protected SlaPolicy() {
	}

	public SlaPolicy(String name) {
		this.name = name;
	}

	public void update(String name, TicketPriority priority, TicketCategory category, String customerType,
			int firstResponseMinutes, Integer nextResponseMinutes, int resolutionMinutes, boolean businessHours,
			boolean pauseWhileWaiting, boolean active, UUID updatedBy) {
		this.name = name;
		this.priority = priority;
		this.category = category;
		this.customerType = customerType;
		this.firstResponseMinutes = firstResponseMinutes;
		this.nextResponseMinutes = nextResponseMinutes;
		this.resolutionMinutes = resolutionMinutes;
		this.businessHours = businessHours;
		this.pauseWhileWaiting = pauseWhileWaiting;
		this.active = active;
		this.updatedBy = updatedBy;
	}

	/** How many criteria it sets (more: more specific); -1 when it does not match. */
	public int specificity(TicketPriority p, TicketCategory c, String type) {
		if ((priority != null && priority != p) || (category != null && category != c)
				|| (customerType != null && !customerType.equals(type))) {
			return -1;
		}
		return (priority != null ? 1 : 0) + (category != null ? 1 : 0) + (customerType != null ? 1 : 0);
	}

	public String getName() {
		return name;
	}

	public TicketPriority getPriority() {
		return priority;
	}

	public TicketCategory getCategory() {
		return category;
	}

	public String getCustomerType() {
		return customerType;
	}

	public int getFirstResponseMinutes() {
		return firstResponseMinutes;
	}

	public Integer getNextResponseMinutes() {
		return nextResponseMinutes;
	}

	public int getResolutionMinutes() {
		return resolutionMinutes;
	}

	public boolean isBusinessHours() {
		return businessHours;
	}

	public boolean isPauseWhileWaiting() {
		return pauseWhileWaiting;
	}

	public boolean isActive() {
		return active;
	}

	public UUID getUpdatedBy() {
		return updatedBy;
	}

}
