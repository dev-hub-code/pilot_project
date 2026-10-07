package com.sealease.backend.payment.dto;

import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.payment.entity.Payment;
import com.sealease.backend.payment.entity.PaymentMethod;
import com.sealease.backend.payment.entity.PaymentStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * @param bankTransfer how to pay, while a bank transfer is pending
 * @param deposit      how the investor says they paid a bank payment, once submitted
 */
public record PaymentResponse(
		UUID id,
		UUID orderId,
		String orderNumber,
		UUID userId,
		PaymentMethod method,
		String provider,
		String providerReference,
		MoneyResponse amount,
		PaymentStatus status,
		String failureReason,
		String externalReference,
		Instant settledAt,
		Instant refundedAt,
		String refundReference,
		String refundReason,
		Instant createdAt,
		BankTransferInstructions bankTransfer,
		DepositDetails deposit) {

	public static PaymentResponse from(Payment p, String orderNumber, BankTransferInstructions bankTransfer,
			DepositDetails deposit) {
		return new PaymentResponse(p.getId(), p.getOrderId(), orderNumber, p.getUserId(), p.getMethod(), p.getProvider(),
				p.getProviderReference(), MoneyResponse.from(p.amount()), p.getStatus(), p.getFailureReason(),
				p.getExternalReference(), p.getSettledAt(), p.getRefundedAt(), p.getRefundReference(), p.getRefundReason(),
				p.getCreatedAt(), bankTransfer, deposit);
	}

}
