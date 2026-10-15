package com.sealease.backend.payment.dto;

import com.sealease.backend.payment.entity.PaymentMethod;
import com.sealease.backend.payment.entity.PaymentStatus;

import java.util.UUID;

/**
 * @param q           a whole payment reference (bank, deposit or provider), a whole order number ({@code qOrderId}),
 *                    or an investor named exactly ({@code qInvestorId})
 * @param searchOnly  a search is required: without {@code q}, nothing
 */
public record PaymentSearchCriteria(PaymentStatus status, PaymentMethod method, String q, UUID qOrderId, UUID qInvestorId,
		boolean searchOnly) {

	public PaymentSearchCriteria(PaymentStatus status, PaymentMethod method) {
		this(status, method, null, null, null, false);
	}
}
