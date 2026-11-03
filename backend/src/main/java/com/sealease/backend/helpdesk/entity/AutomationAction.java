package com.sealease.backend.helpdesk.entity;

/** What an automation rule does: a fixed set of operations, never code or SQL. */
public enum AutomationAction {
	NOTIFY_ASSIGNEE,
	NOTIFY_TEAM,
	NOTIFY_ESCALATION_CONTACTS,
	AUTO_ASSIGN,
	ESCALATE,
	/** parameters: {"priority": "HIGH"} */
	SET_PRIORITY,
	REQUEST_FEEDBACK,
	RECORD_RESOLUTION_METRICS
}
