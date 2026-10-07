package com.sealease.backend.reporting.controller;

import com.sealease.backend.invoice.service.InvoiceService;
import com.sealease.backend.order.service.OrderService;
import com.sealease.backend.reporting.engine.ReportFormat;
import com.sealease.backend.reporting.service.ReportResponses;
import com.sealease.backend.reporting.service.ReportService;
import com.sealease.backend.security.AuthenticatedUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/** An investor's own documents: their statement and their invoices. */
@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasAuthority('INVESTOR_PORTAL')")
public class ReportController {

	private final ReportService reports;
	private final ReportResponses responses;
	private final OrderService orders;
	private final InvoiceService invoices;

	public ReportController(ReportService reports, ReportResponses responses, OrderService orders, InvoiceService invoices) {
		this.reports = reports;
		this.responses = responses;
		this.orders = orders;
		this.invoices = invoices;
	}

	@GetMapping("/statement")
	public ResponseEntity<?> statement(AuthenticatedUser investor,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) String format) {
		return responses.render(reports.investorStatement(investor.userId(), new ReportService.Period(from, to)),
				ReportFormat.parse(format));
	}

	@GetMapping("/invoices/{orderId}")
	public ResponseEntity<?> invoice(AuthenticatedUser investor, @PathVariable UUID orderId,
			@RequestParam(required = false) String format) {
		var order = orders.mine(investor.userId(), orderId);
		return responses.render(reports.invoice(invoices.forOrder(orderId, order.orderNumber(), investor.userId())),
				ReportFormat.parse(format == null ? "pdf" : format));
	}

}
