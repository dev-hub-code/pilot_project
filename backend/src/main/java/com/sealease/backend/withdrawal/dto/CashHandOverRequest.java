package com.sealease.backend.withdrawal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param receiptNumber the office's cash receipt (voucher) number, signed by the investor */
public record CashHandOverRequest(@NotBlank @Size(max = 100) String receiptNumber) {
}
