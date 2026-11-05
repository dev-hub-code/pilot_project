package com.sealease.backend.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.payment.entity.PaymentMethod;

import java.util.List;

/**
 * The ways this investor can pay: online investors through the gateway or from their available balance, network
 * investors by bank or at the office.
 *
 * @param availableBalance what an online investor can pay from their balance now (null for anyone who cannot)
 */
public record PaymentMethodsResponse(List<PaymentMethod> methods,
		@JsonInclude(JsonInclude.Include.NON_NULL) MoneyResponse availableBalance) {
}
