package com.sealease.backend.container.entity;

public enum ContainerStatus {
	AVAILABLE,
	ON_LEASE,
	MAINTENANCE,
	/** Permanently out of service; cannot back new offerings. */
	RETIRED
}
