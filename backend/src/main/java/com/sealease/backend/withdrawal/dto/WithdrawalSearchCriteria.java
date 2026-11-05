package com.sealease.backend.withdrawal.dto;

import com.sealease.backend.withdrawal.entity.WithdrawalOrigin;
import com.sealease.backend.withdrawal.entity.WithdrawalStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * @param q           a whole withdrawal reference ("WD-100001"), or an investor named exactly ({@code qInvestorId})
 * @param qInvestorId the investor {@code q} names exactly, if any: their withdrawals match too
 * @param from        requested at or after; {@code to} before
 * @param minAmount   amount at least; {@code maxAmount} at most
 * @param reviewerId  being reviewed by this staff member
 */
public record WithdrawalSearchCriteria(WithdrawalStatus status, String currency, UUID userId, String q, UUID qInvestorId,
		Instant from, Instant to, BigDecimal minAmount, BigDecimal maxAmount, WithdrawalOrigin origin, UUID reviewerId) {

	public WithdrawalSearchCriteria(WithdrawalStatus status, String currency, UUID userId) {
		this(status, currency, userId, null, null);
	}

	public WithdrawalSearchCriteria(WithdrawalStatus status, String currency, UUID userId, String q, UUID qInvestorId) {
		this(status, currency, userId, q, qInvestorId, null, null, null, null, null, null);
	}

}
