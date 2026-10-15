package com.sealease.backend.withdrawal.dto;

import com.sealease.backend.withdrawal.entity.WithdrawalStatus;

import java.util.UUID;

/**
 * @param q           a whole withdrawal reference ("WD-100001"), or an investor named exactly ({@code qInvestorId})
 * @param qInvestorId the investor {@code q} names exactly, if any: their withdrawals match too
 */
public record WithdrawalSearchCriteria(WithdrawalStatus status, String currency, UUID userId, String q, UUID qInvestorId) {

	public WithdrawalSearchCriteria(WithdrawalStatus status, String currency, UUID userId) {
		this(status, currency, userId, null, null);
	}

}
