package com.sealease.backend.order.controller;

import com.sealease.backend.common.api.PageResponse;
import com.sealease.backend.invoice.dto.InvoiceResponse;
import com.sealease.backend.invoice.service.InvoiceService;
import com.sealease.backend.investment.dto.HoldingResponse;
import com.sealease.backend.investment.service.HoldingService;
import com.sealease.backend.order.dto.OrderResponse;
import com.sealease.backend.order.dto.OrderSearchCriteria;
import com.sealease.backend.order.entity.OrderStatus;
import com.sealease.backend.order.service.OrderService;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/orders")
@PreAuthorize("hasAuthority('ORDER_VIEW')")
public class AdminOrderController {

	private final OrderService orders;
	private final InvoiceService invoices;
	private final HoldingService holdings;

	public AdminOrderController(OrderService orders, InvoiceService invoices, HoldingService holdings) {
		this.orders = orders;
		this.invoices = invoices;
		this.holdings = holdings;
	}

	@GetMapping
	public PageResponse<OrderResponse> search(@RequestParam(required = false) @Size(max = 30) String q,
			@RequestParam(required = false) OrderStatus status, @RequestParam(required = false) UUID userId,
			@PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return PageResponse.from(orders.search(new OrderSearchCriteria(q, status, userId), pageable));
	}

	@GetMapping("/{orderId}")
	public OrderResponse detail(@PathVariable UUID orderId) {
		return orders.detail(orderId);
	}

	@GetMapping("/{orderId}/holdings")
	public List<HoldingResponse> holdings(@PathVariable UUID orderId) {
		return holdings.forOrder(orderId);
	}

	@GetMapping("/{orderId}/invoice")
	public InvoiceResponse invoice(@PathVariable UUID orderId) {
		return invoices.forOrder(orderId, orders.detail(orderId).orderNumber(), null);
	}

}
