package com.sealease.backend.payment.controller;

import com.sealease.backend.common.web.IdempotencyKey;
import com.sealease.backend.payment.dto.PaymentResponse;
import com.sealease.backend.payment.dto.StartPaymentRequest;
import com.sealease.backend.payment.dto.SubmitDepositRequest;
import com.sealease.backend.payment.service.PaymentService;
import com.sealease.backend.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@PreAuthorize("hasAuthority('INVESTOR_PORTAL')")
public class PaymentController {

	private final PaymentService payments;

	public PaymentController(PaymentService payments) {
		this.payments = payments;
	}

	@GetMapping("/api/v1/orders/{orderId}/payments")
	public List<PaymentResponse> list(AuthenticatedUser investor, @PathVariable UUID orderId) {
		return payments.forOrder(orderId, investor.userId());
	}

	@PostMapping("/api/v1/orders/{orderId}/payments")
	@ResponseStatus(HttpStatus.CREATED)
	public PaymentResponse start(AuthenticatedUser investor, @PathVariable UUID orderId,
			@RequestHeader(IdempotencyKey.HEADER) String idempotencyKey, @Valid @RequestBody StartPaymentRequest request) {
		return payments.start(investor.userId(), orderId, request.method(), idempotencyKey);
	}

	/** How the investor paid a bank payment: the company account and the transaction id, cheque or receipt number. */
	@PostMapping("/api/v1/payments/{paymentId}/deposit")
	public PaymentResponse submitDeposit(AuthenticatedUser investor, @PathVariable UUID paymentId,
			@Valid @RequestBody SubmitDepositRequest request) {
		return payments.submitDeposit(investor.userId(), paymentId, request);
	}


}
