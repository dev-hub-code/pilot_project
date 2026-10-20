package com.sealease.backend.agreement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * New wording for the lease agreement: a title and a body with {{placeholders}}. In the body, a blank line separates
 * paragraphs and a line starting with "# " is a heading.
 */
public record AgreementTemplateRequest(
		@NotBlank @Size(max = 200) String title,
		@NotBlank @Size(max = 50000) String body) {
}
