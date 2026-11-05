package com.sealease.backend.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.payment.entity.PaymentMethod;

import java.util.List;

/**
 * The ways this investor can pay: online investors through the gateway, network investors by bank or at the office.
 *
 * @param checkoutLimit the most one checkout payment can be (null when there is no checkout or no limit): a larger order
 *                      is paid another way
 */
public record PaymentMethodsResponse(List<PaymentMethod> methods,
		@JsonInclude(JsonInclude.Include.NON_NULL) MoneyResponse checkoutLimit) {
}
