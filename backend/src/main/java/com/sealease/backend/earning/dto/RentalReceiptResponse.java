package com.sealease.backend.earning.dto;

import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.earning.entity.ReceiptStatus;
import com.sealease.backend.earning.entity.RentalReceipt;
import com.sealease.backend.investment.dto.Lease;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** @param periodEndsOn exclusive: the first day of the next period, when this period's rent fell due */
public record RentalReceiptResponse(
		UUID id,
		UUID productId,
		String productCode,
		String productTitle,
		int periodNumber,
		int periodCount,
		LocalDate periodStartsOn,
		LocalDate periodEndsOn,
		MoneyResponse amount,
		MoneyResponse expectedAmount,
		LocalDate receivedOn,
		String externalReference,
		String note,
		ReceiptStatus status,
		UUID recordedBy,
		UUID decidedBy,
		Instant decidedAt,
		String rejectionReason,
		BigDecimal managementFeePercent,
		Instant createdAt) {

	public static RentalReceiptResponse from(RentalReceipt r, Lease lease) {
		return new RentalReceiptResponse(r.getId(), r.getProductId(), lease.code(), lease.title(), r.getPeriodNumber(),
				lease.periodCount(), r.getPeriodStartsOn(), r.getPeriodEndsOn(), MoneyResponse.from(r.amount()),
				MoneyResponse.from(r.expectedAmount()), r.getReceivedOn(), r.getExternalReference(), r.getNote(),
				r.getStatus(), r.getRecordedBy(), r.getDecidedBy(), r.getDecidedAt(), r.getRejectionReason(),
				r.getManagementFeePercent() != null ? r.getManagementFeePercent() : lease.managementFeePercent(),
				r.getCreatedAt());
	}

}
