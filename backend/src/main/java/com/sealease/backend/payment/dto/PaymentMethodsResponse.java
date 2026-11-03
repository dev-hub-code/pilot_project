package com.sealease.backend.payment.dto;

import com.sealease.backend.payment.entity.PaymentMethod;

import java.util.List;

/** The ways this investor can pay: online investors through the gateway, network investors by bank or at the office. */
public record PaymentMethodsResponse(List<PaymentMethod> methods) {
}
