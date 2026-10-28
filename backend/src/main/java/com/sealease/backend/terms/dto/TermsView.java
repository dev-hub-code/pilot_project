package com.sealease.backend.terms.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The terms in force and the latest versions.
 *
 * @param accepted accounts that accepted the version in force
 */
public record TermsView(Version current, long accepted, List<Version> history) {

	/**
	 * @param body          {@code null} in the history list
	 * @param createdByName {@code null} for the wording the platform shipped with
	 */
	public record Version(UUID id, int version, String title, String body, Instant createdAt, String createdByName) {
	}

}
