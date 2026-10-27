package com.sealease.backend.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** @param receiptNumber the number on the receipt the office gave the investor for their cash */
public record SubmitReceiptRequest(
		@NotBlank @Pattern(regexp = "^[A-Za-z0-9/ -]{3,100}$", message = "must be 3-100 letters, digits, spaces, dashes or slashes")
		String receiptNumber) {
}
