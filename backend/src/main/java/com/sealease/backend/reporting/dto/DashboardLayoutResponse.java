package com.sealease.backend.reporting.dto;

import java.time.Instant;
import java.util.List;

/**
 * @param updatedAt null when the employee has not arranged their overview: the default layout applies
 * @param allowed   the cards the employee's roles show; null when every card is allowed
 */
public record DashboardLayoutResponse(List<String> cards, List<String> hidden, Instant updatedAt, List<String> allowed) {

	public DashboardLayoutResponse withAllowed(List<String> allowed) {
		return new DashboardLayoutResponse(cards, hidden, updatedAt, allowed);
	}

}
