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
import com.sealease.backend.permission.PermissionCode;
import com.sealease.backend.security.AuthenticatedUser;
import com.sealease.backend.user.service.UserAccountService;
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
	private final UserAccountService accounts;

	public AdminOrderController(OrderService orders, InvoiceService invoices, HoldingService holdings,
			UserAccountService accounts) {
		this.accounts = accounts;
		this.orders = orders;
		this.invoices = invoices;
		this.holdings = holdings;
	}

	/** Without RECORD_BROWSE, only an exact search: a whole order number, or an investor's readable ID or email. */
	@GetMapping
	public PageResponse<OrderResponse> search(@RequestParam(required = false) @Size(max = 100) String q,
			@RequestParam(required = false) OrderStatus status, @RequestParam(required = false) UUID userId,
			@PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable, AuthenticatedUser viewer) {
		boolean exact = !viewer.hasPermission(PermissionCode.RECORD_BROWSE.name());
		return PageResponse.from(orders.search(new OrderSearchCriteria(q, status, exact ? null : userId, exact,
				accounts.findIdExactly(q).orElse(null)), pageable));
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
