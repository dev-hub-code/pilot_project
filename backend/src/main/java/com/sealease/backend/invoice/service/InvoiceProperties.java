package com.sealease.backend.invoice.service;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * The seller printed on every invoice. Copied onto each invoice when it is issued, so later
 * changes (a new address) never alter documents already sent.
 */
@Validated
@ConfigurationProperties(prefix = "app.invoice")
public record InvoiceProperties(
		@NotBlank String issuerName,
		@NotBlank String issuerAddress,
		String issuerTaxId,
		@DefaultValue("") String notes) {
}
