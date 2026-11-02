package com.sealease.backend.announcement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A new announcement. {@code link}, if given, is a page of the web app (e.g. {@code /invest}) that opening the
 * notification leads to; links to other sites are refused.
 */
public record AnnouncementRequest(
		@NotBlank @Size(max = 200) String title,
		@NotBlank @Size(max = 1000) String body,
		@Size(max = 300) @Pattern(regexp = "/(?![/\\\\])[^\\s\\\\]*", message = "must be a page of the site, starting with /")
		String link,
		@NotNull AnnouncementAudience audience) {
}
