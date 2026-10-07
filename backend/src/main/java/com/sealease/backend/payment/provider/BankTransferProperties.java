package com.sealease.backend.payment.provider;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** The platform's collection account, shown to investors paying by bank transfer. */
@Validated
@ConfigurationProperties(prefix = "app.payments.bank-transfer")
public record BankTransferProperties(
		@NotBlank String beneficiaryName,
		@NotBlank String iban,
		@NotBlank String bic,
		@NotBlank String bankName) {
}
