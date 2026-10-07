package com.sealease.backend.container.entity;

/** Industry grading of a container's condition. */
public enum ContainerCondition {
	/** One-trip / new build. */
	NEW,
	/** Fit for ocean shipping (CW). */
	CARGO_WORTHY,
	/** Wind and watertight (WWT): storage use, not certified for shipping. */
	WIND_WATERTIGHT
}
