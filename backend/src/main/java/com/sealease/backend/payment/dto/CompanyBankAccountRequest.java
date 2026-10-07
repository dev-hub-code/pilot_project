package com.sealease.backend.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param accountNumber digits only; spaces are ignored
 * @param ifscCode      Indian Financial System Code, e.g. HDFC0001234
 * @param upiId         optional, e.g. sealease@hdfcbank
 */
public record CompanyBankAccountRequest(
		@NotBlank @Size(max = 140) String accountName,
		@NotBlank @Size(max = 140) String bankName,
		@Size(max = 140) String branch,
		@NotBlank @Pattern(regexp = "^[0-9 ]{6,24}$", message = "must be 6-18 digits") String accountNumber,
		@NotBlank @Pattern(regexp = "^[A-Za-z]{4}0[A-Za-z0-9]{6}$", message = "must be a valid IFSC, e.g. HDFC0001234")
		String ifscCode,
		@Size(max = 100) @Pattern(regexp = "^$|^[A-Za-z0-9._-]{2,256}@[A-Za-z]{2,64}$", message = "must be a UPI id, e.g. name@bank")
		String upiId) {
}
