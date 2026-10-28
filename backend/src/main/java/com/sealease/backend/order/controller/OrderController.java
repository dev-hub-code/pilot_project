package com.sealease.backend.order.controller;

import com.sealease.backend.common.api.PageResponse;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.common.web.IdempotencyKey;
import com.sealease.backend.demo.service.DemoViewService;
import com.sealease.backend.invoice.dto.InvoiceResponse;
import com.sealease.backend.invoice.service.InvoiceService;
import com.sealease.backend.order.dto.CheckoutRequest;
import com.sealease.backend.order.dto.OrderResponse;
import com.sealease.backend.order.service.OrderService;
import com.sealease.backend.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@PreAuthorize("hasAuthority('INVESTOR_PORTAL')")
public class OrderController {

	private final OrderService orders;
	private final InvoiceService invoices;
	private final DemoViewService demo;

	public OrderController(OrderService orders, InvoiceService invoices, DemoViewService demo) {
		this.orders = orders;
		this.invoices = invoices;
		this.demo = demo;
	}

	/** Checks out the cart. Requires an {@code Idempotency-Key}; a retry returns the same order. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public OrderResponse checkout(AuthenticatedUser investor, @RequestHeader(IdempotencyKey.HEADER) String idempotencyKey,
			@Valid @RequestBody CheckoutRequest request) {
		return orders.checkout(investor.userId(), idempotencyKey, request);
	}

	@GetMapping
	public PageResponse<OrderResponse> list(AuthenticatedUser investor,
			@PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return PageResponse.from(demo.isDemo(investor.userId()) ? demo.orders(investor.userId(), pageable)
				: orders.mine(investor.userId(), pageable));
	}

	@GetMapping("/{orderId}")
	public OrderResponse detail(AuthenticatedUser investor, @PathVariable UUID orderId) {
		return demo.isDemo(investor.userId()) ? demo.order(investor.userId(), orderId) : orders.mine(investor.userId(), orderId);
	}

	@PostMapping("/{orderId}/cancel")
	public OrderResponse cancel(AuthenticatedUser investor, @PathVariable UUID orderId) {
		return orders.cancel(investor.userId(), orderId);
	}

	@GetMapping("/{orderId}/invoice")
	public InvoiceResponse invoice(AuthenticatedUser investor, @PathVariable UUID orderId) {
		// Dummy orders have no invoice.
		if (demo.isDemo(investor.userId())) {
			throw new ResourceNotFoundException("Invoice", orderId);
		}
		OrderResponse order = orders.mine(investor.userId(), orderId);
		return invoices.forOrder(orderId, order.orderNumber(), investor.userId());
	}

}
