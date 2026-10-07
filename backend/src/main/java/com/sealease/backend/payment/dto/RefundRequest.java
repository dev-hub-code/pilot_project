package com.sealease.backend.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param reference the outgoing transfer's reference */
public record RefundRequest(@NotBlank @Size(max = 100) String reference, @NotBlank @Size(max = 500) String reason) {
}
