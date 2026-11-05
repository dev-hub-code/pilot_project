package com.sealease.backend.payment.controller;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyProperties;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.payment.dto.PayFromBalanceRequest;
import com.sealease.backend.common.web.IdempotencyKey;
import com.sealease.backend.demo.service.DemoViewService;
import com.sealease.backend.payment.dto.PaymentMethodsResponse;
import com.sealease.backend.payment.dto.PaymentResponse;
import com.sealease.backend.payment.dto.StartPaymentRequest;
import com.sealease.backend.payment.dto.SubmitDepositRequest;
import com.sealease.backend.payment.dto.SubmitReceiptRequest;
import com.sealease.backend.payment.dto.VerifyCheckoutRequest;
import com.sealease.backend.payment.gateway.GatewayPayments;
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
	private final GatewayPayments gateway;
	private final DemoViewService demo;
	private final MoneyProperties money;

	public PaymentController(PaymentService payments, GatewayPayments gateway, DemoViewService demo,
			MoneyProperties money) {
		this.payments = payments;
		this.gateway = gateway;
		this.demo = demo;
		this.money = money;
	}

	/** How this investor pays: online investors through the gateway, network investors by bank or at the office. */
	@GetMapping("/api/v1/payments/methods")
	public PaymentMethodsResponse methods(AuthenticatedUser investor) {
		Money balance = payments.reinvestableBalance(investor.userId(), money.defaultCurrency());
		Money limit = payments.checkoutLimit(investor.userId(), money.defaultCurrency());
		return new PaymentMethodsResponse(payments.methodsFor(investor.userId()),
				balance == null ? null : MoneyResponse.from(balance), limit == null ? null : MoneyResponse.from(limit));
	}

	/** The gateway's checkout finished: its signed result, checked with the gateway before the order is confirmed. */
	@PostMapping("/api/v1/payments/{paymentId}/checkout")
	public PaymentResponse verifyCheckout(AuthenticatedUser investor, @PathVariable UUID paymentId,
			@Valid @RequestBody VerifyCheckoutRequest request) {
		return gateway.verifyCheckout(investor.userId(), paymentId, request);
	}

	@GetMapping("/api/v1/orders/{orderId}/payments")
	public List<PaymentResponse> list(AuthenticatedUser investor, @PathVariable UUID orderId) {
		// A demo account's orders are dummies, with no payments.
		return demo.isDemo(investor.userId()) ? List.of() : payments.forOrder(orderId, investor.userId());
	}

	@PostMapping("/api/v1/orders/{orderId}/payments")
	@ResponseStatus(HttpStatus.CREATED)
	public PaymentResponse start(AuthenticatedUser investor, @PathVariable UUID orderId,
			@RequestHeader(IdempotencyKey.HEADER) String idempotencyKey, @Valid @RequestBody StartPaymentRequest request) {
		return payments.start(investor.userId(), orderId, request.method(), idempotencyKey);
	}

	/**
	 * Pays the order from the investor's available balance (online investors), with their password. Confirmed at once:
	 * the order is paid when this returns.
	 */
	@PostMapping("/api/v1/orders/{orderId}/payments/balance")
	@ResponseStatus(HttpStatus.CREATED)
	public PaymentResponse payFromBalance(AuthenticatedUser investor, @PathVariable UUID orderId,
			@RequestHeader(IdempotencyKey.HEADER) String idempotencyKey, @Valid @RequestBody PayFromBalanceRequest request) {
		return payments.payFromBalance(investor.userId(), orderId, request.password(), idempotencyKey);
	}

	/** How the investor paid a bank payment: the company account and the transaction id, cheque or receipt number. */
	@PostMapping("/api/v1/payments/{paymentId}/deposit")
	public PaymentResponse submitDeposit(AuthenticatedUser investor, @PathVariable UUID paymentId,
			@Valid @RequestBody SubmitDepositRequest request) {
		return payments.submitDeposit(investor.userId(), paymentId, request);
	}

	/** The receipt number the office gave the investor for cash paid there. */
	@PostMapping("/api/v1/payments/{paymentId}/receipt")
	public PaymentResponse submitReceipt(AuthenticatedUser investor, @PathVariable UUID paymentId,
			@Valid @RequestBody SubmitReceiptRequest request) {
		return payments.submitReceipt(investor.userId(), paymentId, request);
	}


}
