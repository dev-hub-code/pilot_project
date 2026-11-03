package com.sealease.backend.helpdesk.entity;

/** How new tickets in a category find an agent. */
public enum AssignmentStrategy {
	/** Agents pick tickets up (or are given them) by hand. */
	MANUAL,
	/** Agents in turn. */
	ROUND_ROBIN,
	/** The agent with the fewest open tickets. */
	LEAST_LOADED
}
