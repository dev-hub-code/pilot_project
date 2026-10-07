package com.sealease.backend.investment.service;

import java.util.List;

/** How many containers one purchase may cover. */
public final class PlanRules {

	/** Containers per plan in one cart line (and so one order). */
	public static final int MAX_CONTAINERS_PER_LINE = 50;

	private PlanRules() {
	}

	public static List<String> quantityProblems(int quantity, long available) {
		if (quantity < 1) {
			return List.of("Buy at least one container");
		}
		if (quantity > MAX_CONTAINERS_PER_LINE) {
			return List.of("At most " + MAX_CONTAINERS_PER_LINE + " containers per plan in one order");
		}
		if (available == 0) {
			return List.of("No containers are available under this plan right now");
		}
		if (quantity > available) {
			return List.of("Only " + available + " container(s) are available right now");
		}
		return List.of();
	}

}
