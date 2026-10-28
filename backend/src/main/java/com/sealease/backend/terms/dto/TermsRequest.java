package com.sealease.backend.terms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * New terms and conditions: a title and a body. In the body, a blank line separates paragraphs and a line starting with
 * "# " is a heading.
 */
public record TermsRequest(
		@NotBlank @Size(max = 200) String title,
		@NotBlank @Size(max = 100_000) String body) {
}
