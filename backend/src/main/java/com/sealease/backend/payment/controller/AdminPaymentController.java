package com.sealease.backend.payment.controller;

import com.sealease.backend.common.api.PageResponse;
import com.sealease.backend.common.api.ReasonRequest;
import com.sealease.backend.order.service.OrderService;
import com.sealease.backend.payment.dto.ConfirmTransferRequest;
import com.sealease.backend.payment.dto.PaymentResponse;
import com.sealease.backend.payment.dto.PaymentSearchCriteria;
import com.sealease.backend.payment.dto.RefundRequest;
import com.sealease.backend.payment.entity.PaymentMethod;
import com.sealease.backend.payment.entity.PaymentStatus;
import com.sealease.backend.payment.service.PaymentService;
import com.sealease.backend.security.AuthenticatedUser;
import com.sealease.backend.user.service.UserAccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
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

	private final OrderService orders;
	private final UserAccountService accounts;

	public AdminPaymentController(PaymentService payments, OrderService orders, UserAccountService accounts) {
		this.orders = orders;
		this.accounts = accounts;
		this.payments = payments;
	}

	/**
	 * Finance view sees every payment; those who confirm payments see the ones waiting for confirmation; those who
	 * only search (support) see what an exact search finds, and nothing without one. A search ({@code q}) is a whole
	 * payment reference, a whole order number, or an investor's readable ID or email.
	 */
	@GetMapping("/api/v1/admin/payments")
	@PreAuthorize("hasAnyAuthority('FINANCE_VIEW', 'PAYMENT_CONFIRM', 'PAYMENT_SEARCH')")
	public PageResponse<PaymentResponse> search(AuthenticatedUser viewer, @RequestParam(required = false) PaymentStatus status,
			@RequestParam(required = false) PaymentMethod method, @RequestParam(required = false) @Size(max = 100) String q,
			@PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		boolean searching = q != null && !q.isBlank();
		boolean financeView = viewer.hasPermission("FINANCE_VIEW");
		boolean searchOnly = !financeView && (viewer.hasPermission("PAYMENT_SEARCH") && (searching
				|| !viewer.hasPermission("PAYMENT_CONFIRM")));
		PaymentStatus visible = financeView || searchOnly ? status : PaymentStatus.PENDING;
		return PageResponse.from(payments.search(new PaymentSearchCriteria(visible, method, searching ? q : null,
				searching ? orders.idByNumber(q).orElse(null) : null,
				searching ? accounts.findIdExactly(q).orElse(null) : null, searchOnly), pageable));
	}

	/** Takes the payment: only the finance member it is assigned to may confirm or reject it. */
	@PostMapping("/api/v1/admin/payments/{paymentId}/take")
	@PreAuthorize("hasAuthority('PAYMENT_CONFIRM')")
	public PaymentResponse take(AuthenticatedUser actor, @PathVariable UUID paymentId) {
		return payments.takeTask(actor.userId(), paymentId);
	}

	@PostMapping("/api/v1/admin/payments/{paymentId}/release")
	@PreAuthorize("hasAuthority('PAYMENT_CONFIRM')")
	public PaymentResponse release(AuthenticatedUser actor, @PathVariable UUID paymentId) {
		return payments.releaseTask(actor.userId(), paymentId);
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

	@PostMapping("/api/v1/admin/payments/{paymentId}/reject")
	@PreAuthorize("hasAuthority('PAYMENT_CONFIRM')")
	public PaymentResponse reject(AuthenticatedUser actor, @PathVariable UUID paymentId,
			@Valid @RequestBody ReasonRequest request) {
		return payments.rejectBankPayment(actor.userId(), paymentId, request.reason());
	}

	@PostMapping("/api/v1/admin/payments/{paymentId}/refund")
	@PreAuthorize("hasAuthority('FINANCE_ADJUST')")
	public PaymentResponse refund(AuthenticatedUser actor, @PathVariable UUID paymentId,
			@Valid @RequestBody RefundRequest request) {
		return payments.recordRefund(actor.userId(), paymentId, request);
	}

}
