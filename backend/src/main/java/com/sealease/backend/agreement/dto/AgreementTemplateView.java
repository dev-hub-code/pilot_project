package com.sealease.backend.agreement.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The wording in force, the placeholders a template may use, and the latest versions.
 *
 * @param current {@code null} only if no template was ever saved
 */
public record AgreementTemplateView(Version current, List<PlaceholderInfo> placeholders, List<Version> history) {

	/**
	 * @param body          {@code null} in the history list
	 * @param createdByName {@code null} for the wording the platform shipped with
	 */
	public record Version(UUID id, int version, String title, String body, Instant createdAt, String createdByName) {
	}

	public record PlaceholderInfo(String key, String description, String example) {
	}

}
