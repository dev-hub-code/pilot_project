package com.sealease.backend.withdrawal.entity;

/** CREATED (file downloadable, may be cancelled) → SENT (with the bank) → CLOSED (all reconciled); or CANCELLED. */
public enum BatchStatus {
	CREATED,
	SENT,
	CLOSED,
	CANCELLED
}
