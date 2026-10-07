package com.sealease.backend.payment.dto;

import com.sealease.backend.payment.provider.ProviderEvent;
import jakarta.validation.constraints.NotNull;

public record SimulatePaymentRequest(@NotNull ProviderEvent.Outcome outcome) {
}
