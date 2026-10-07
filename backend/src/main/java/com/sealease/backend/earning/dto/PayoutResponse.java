package com.sealease.backend.earning.dto;

import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.earning.entity.PayoutInstallment;
import com.sealease.backend.earning.entity.PayoutStatus;
import com.sealease.backend.investment.dto.HoldingLabel;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One monthly payout of one container: rent plus capital returned.
 *
 * @param userId shown to staff only
 */
public record PayoutResponse(
		UUID id,
		UUID holdingId,
		UUID userId,
		UUID productId,
		String productCode,
		String productTitle,
		String containerNumber,
		int installmentNumber,
		int installmentCount,
		LocalDate dueOn,
		MoneyResponse rent,
		MoneyResponse capital,
		MoneyResponse total,
		PayoutStatus status,
		Instant paidAt) {

	public static PayoutResponse from(PayoutInstallment p, HoldingLabel label, boolean staff) {
		return new PayoutResponse(p.getId(), p.getHoldingId(), staff ? p.getUserId() : null, p.getProductId(),
				label.productCode(), label.productTitle(), label.containerNumber(), p.getInstallmentNumber(),
				label.tenureMonths(), p.getDueOn(), MoneyResponse.from(p.rent()), MoneyResponse.from(p.capital()),
				MoneyResponse.from(p.total()), p.getStatus(), p.getPaidAt());
	}

}
