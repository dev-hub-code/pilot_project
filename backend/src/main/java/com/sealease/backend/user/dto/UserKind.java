package com.sealease.backend.user.dto;

/** Who an account belongs to: an investor (a role grants the investor portal) or an employee (no such role). */
public enum UserKind {
	INVESTOR,
	EMPLOYEE
}
