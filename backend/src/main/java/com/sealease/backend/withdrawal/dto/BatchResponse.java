package com.sealease.backend.withdrawal.dto;

import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.withdrawal.entity.BatchStatus;
import com.sealease.backend.withdrawal.entity.WithdrawalBatch;
import com.sealease.backend.withdrawal.entity.WithdrawalKind;
import com.sealease.backend.withdrawal.entity.WithdrawalPayoutMethod;

import java.time.Instant;
import java.util.UUID;

/**
 * @param payoutMethod BANK_TRANSFER (the bank sheet) or OFFICE_CASH (the cash sheet)
 * @param kind        RENT or REFERRAL: which monthly run made it, for batches from before admin runs; otherwise null
 * @param period      the month ("yyyy-MM") of a batch made by a monthly run; otherwise null
 * @param outstanding items sent to the bank and not yet reconciled as paid or failed
 */
public record BatchResponse(UUID id, String reference, WithdrawalPayoutMethod payoutMethod, WithdrawalKind kind, String period, String currency, BatchStatus status, int itemCount,
		MoneyResponse total, long paid, long failed, long outstanding, UUID createdBy, Instant createdAt,
		Instant sentAt, Instant closedAt) {

	public static BatchResponse of(WithdrawalBatch b, long paid, long failed, long outstanding) {
		return new BatchResponse(b.getId(), b.getReference(), b.getPayoutMethod(), b.getKind(), b.getPeriod(), b.getCurrency(), b.getStatus(), b.getItemCount(),
				MoneyResponse.from(b.total()), paid, failed, outstanding, b.getCreatedBy(), b.getCreatedAt(),
				b.getSentAt(), b.getClosedAt());
	}

}
