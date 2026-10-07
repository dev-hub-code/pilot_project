package com.sealease.backend.helpdesk.entity;

/** The kind of record a ticket may point at; always one of the requester's own. */
public enum RelatedType {
	ORDER,
	WITHDRAWAL,
	HOLDING
}
