package com.sealease.backend.bankaccount.dto;

import com.sealease.backend.common.validation.IsoCountry;
import com.sealease.backend.common.validation.PlatformCurrency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param accountNumber local account number or IBAN (IBANs are checksum-validated)
 * @param routingCode   bank identifier: SWIFT/BIC, IFSC, ABA routing number, sort code, ...
 */
public record AddBankAccountRequest(
		@NotBlank @Size(max = 140) String accountHolderName,
		@NotBlank @Size(max = 140) String bankName,
		@NotBlank @IsoCountry String country,
		@NotBlank @PlatformCurrency String currency,
		@NotBlank @Pattern(regexp = "^[A-Za-z0-9 -]{4,40}$", message = "must be 4-40 letters, digits, spaces or dashes")
		String accountNumber,
		@NotBlank @Pattern(regexp = "^[A-Za-z0-9 -]{4,20}$", message = "must be 4-20 letters, digits, spaces or dashes")
		String routingCode) {

	@Override
	public String toString() {
		return "AddBankAccountRequest[bankName=" + bankName + ", country=" + country + ", accountNumber=***]";
	}

}
