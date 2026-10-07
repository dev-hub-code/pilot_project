package com.sealease.backend.crm.entity;

/** NEW → CONTACTED → QUALIFIED → PROPOSAL → WON or LOST. WON is final; a LOST lead can be reopened. */
public enum LeadStage {
	NEW,
	CONTACTED,
	QUALIFIED,
	PROPOSAL,
	WON,
	LOST;

	public boolean isOpen() {
		return this != WON && this != LOST;
	}

}
