package com.sealease.backend.payment.controller;

import com.sealease.backend.common.api.PageResponse;
import com.sealease.backend.payment.dto.ConfirmTransferRequest;
import com.sealease.backend.payment.dto.PaymentResponse;
import com.sealease.backend.payment.dto.PaymentSearchCriteria;
import com.sealease.backend.payment.dto.RefundRequest;
import com.sealease.backend.payment.entity.PaymentMethod;
import com.sealease.backend.payment.entity.PaymentStatus;
import com.sealease.backend.payment.service.PaymentService;
import com.sealease.backend.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class AdminPaymentController {

	private final PaymentService payments;

	public AdminPaymentController(PaymentService payments) {
		this.payments = payments;
	}

	@GetMapping("/api/v1/admin/payments")
	@PreAuthorize("hasAuthority('FINANCE_VIEW')")
	public PageResponse<PaymentResponse> search(@RequestParam(required = false) PaymentStatus status,
			@RequestParam(required = false) PaymentMethod method,
			@PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return PageResponse.from(payments.search(new PaymentSearchCriteria(status, method), pageable));
	}

	@GetMapping("/api/v1/admin/orders/{orderId}/payments")
	@PreAuthorize("hasAnyAuthority('FINANCE_VIEW', 'ORDER_VIEW')")
	public List<PaymentResponse> forOrder(@PathVariable UUID orderId) {
		return payments.forOrder(orderId, null);
	}

	@PostMapping("/api/v1/admin/payments/{paymentId}/confirm")
	@PreAuthorize("hasAuthority('PAYMENT_CONFIRM')")
	public PaymentResponse confirm(AuthenticatedUser actor, @PathVariable UUID paymentId,
			@Valid @RequestBody ConfirmTransferRequest request) {
		return payments.confirmBankTransfer(actor.userId(), paymentId, request);
	}

	@PostMapping("/api/v1/admin/payments/{paymentId}/refund")
	@PreAuthorize("hasAuthority('FINANCE_ADJUST')")
	public PaymentResponse refund(AuthenticatedUser actor, @PathVariable UUID paymentId,
			@Valid @RequestBody RefundRequest request) {
		return payments.recordRefund(actor.userId(), paymentId, request);
	}

}
