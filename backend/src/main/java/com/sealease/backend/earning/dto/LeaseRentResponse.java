package com.sealease.backend.earning.dto;

import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.earning.entity.RentAccrual;
import com.sealease.backend.investment.dto.HoldingLabel;

import java.time.Instant;
import java.util.UUID;

/**
 * The rent of one container's lease, credited every hour from {@code startsAt} to {@code endsAt}.
 *
 * @param userId        shown to staff only
 * @param creditedUntil credited up to this hour
 * @param totalRent     monthly rent × tenure: what the whole lease pays
 * @param capitalTotal  capital returned over the lease (containers sold before capital returns ended); zero otherwise
 * @param status        RUNNING (or about to start), COMPLETED once the whole lease is credited
 */
public record LeaseRentResponse(
		UUID holdingId,
		UUID userId,
		UUID productId,
		String productCode,
		String productTitle,
		String containerNumber,
		int tenureMonths,
		Instant startsAt,
		Instant endsAt,
		Instant creditedUntil,
		MoneyResponse monthlyRent,
		MoneyResponse totalRent,
		MoneyResponse rentCredited,
		MoneyResponse capitalTotal,
		MoneyResponse capitalCredited,
		LeaseRentStatus status,
		Instant completedAt) {

	public enum LeaseRentStatus {
		RUNNING, COMPLETED
	}

	public static LeaseRentResponse from(RentAccrual a, HoldingLabel label, boolean staff) {
		return new LeaseRentResponse(a.getHoldingId(), staff ? a.getUserId() : null, a.getProductId(),
				label == null ? null : label.productCode(), label == null ? null : label.productTitle(),
				label == null ? null : label.containerNumber(), a.getTenureMonths(), a.getStartsAt(), a.getEndsAt(),
				a.getCreditedUntil(), MoneyResponse.from(a.monthlyRent()), MoneyResponse.from(a.totalRent()),
				MoneyResponse.from(a.creditedRent()), MoneyResponse.from(a.capitalTotal()),
				MoneyResponse.from(a.creditedCapital()),
				a.isCompleted() ? LeaseRentStatus.COMPLETED : LeaseRentStatus.RUNNING, a.getCompletedAt());
	}

}
