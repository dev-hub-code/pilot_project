package com.sealease.backend.payment.dto;

import com.sealease.backend.payment.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;

public record StartPaymentRequest(@NotNull PaymentMethod method) {
}
