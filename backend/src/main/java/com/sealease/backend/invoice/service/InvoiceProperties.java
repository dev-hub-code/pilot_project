package com.sealease.backend.invoice.service;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * The seller printed on every invoice. Copied onto each invoice when it is issued, so later
 * changes (a new address) never alter documents already sent.
 *
 * @param issuerTaxId the seller's GSTIN; its first two digits give the seller's state
 * @param issuerState the seller's state (name or GST code), when {@code issuerTaxId} is not a GSTIN; buyers in
 *                    the same state are charged CGST + SGST, others IGST
 */
@Validated
@ConfigurationProperties(prefix = "app.invoice")
public record InvoiceProperties(
		@NotBlank String issuerName,
		@NotBlank String issuerAddress,
		String issuerTaxId,
		@DefaultValue("") String issuerState,
		@DefaultValue("") String notes) {
}
