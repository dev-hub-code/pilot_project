package com.sealease.backend.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Checkout of the whole cart. The investor confirms, per offering, the version of the terms they
 * read; checkout fails if any of them changed since.
 */
public record CheckoutRequest(@NotEmpty @Size(max = 20) List<@Valid @NotNull AcceptedTerms> acceptedTerms) {

	public record AcceptedTerms(@NotNull UUID productId, @NotBlank @Size(max = 20) String termsVersion) {
	}

}
