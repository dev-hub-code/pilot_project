package com.sealease.backend.payment.dto;

import com.sealease.backend.payment.entity.DepositMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

/**
 * How the investor paid a bank payment.
 *
 * @param companyBankAccountId the company account the money went to
 * @param reference            transaction id (online), cheque number (cheque) or deposit receipt number (cash)
 */
public record SubmitDepositRequest(
		@NotNull UUID companyBankAccountId,
		@NotNull DepositMode mode,
		@NotBlank @Pattern(regexp = "^[A-Za-z0-9/ -]{3,100}$", message = "must be 3-100 letters, digits, spaces, dashes or slashes")
		String reference) {
}
