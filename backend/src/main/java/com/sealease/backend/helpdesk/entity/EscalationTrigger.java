package com.sealease.backend.helpdesk.entity;

/** Why a ticket was escalated. */
public enum EscalationTrigger {
	MANUAL,
	FIRST_RESPONSE_AT_RISK,
	FIRST_RESPONSE_BREACHED,
	RESOLUTION_BREACHED,
	CRITICAL_UNACKNOWLEDGED,
	REPEATED_FOLLOW_UPS,
	REPEATED_REOPENS,
	AUTOMATION
}
