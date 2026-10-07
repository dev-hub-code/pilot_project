package com.sealease.backend.reporting.controller;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.invoice.service.InvoiceService;
import com.sealease.backend.order.service.OrderService;
import com.sealease.backend.reporting.dto.KpiTile;
import com.sealease.backend.reporting.engine.ReportDocument;
import com.sealease.backend.reporting.engine.ReportFormat;
import com.sealease.backend.reporting.service.DashboardService;
import com.sealease.backend.reporting.service.ReportResponses;
import com.sealease.backend.reporting.service.ReportService;
import com.sealease.backend.security.AuthenticatedUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Staff reports. Previews (JSON) need REPORT_VIEW; downloads (PDF, CSV) need REPORT_GENERATE and are
 * audited with their parameters.
 */
@RestController
public class AdminReportController {

	private final ReportService reports;
	private final ReportResponses responses;
	private final DashboardService dashboard;
	private final OrderService orders;
	private final InvoiceService invoices;

	public AdminReportController(ReportService reports, ReportResponses responses, DashboardService dashboard,
			OrderService orders, InvoiceService invoices) {
		this.reports = reports;
		this.responses = responses;
		this.dashboard = dashboard;
		this.orders = orders;
		this.invoices = invoices;
	}

	@GetMapping("/api/v1/admin/reports/statement")
	@PreAuthorize("hasAnyAuthority('REPORT_VIEW', 'REPORT_GENERATE')")
	public ResponseEntity<?> statement(AuthenticatedUser actor, @RequestParam UUID userId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) String format) {
		return produce(actor, "INVESTOR_STATEMENT", format, Map.of("userId", userId.toString(), "from", from.toString(),
				"to", to.toString()), () -> reports.investorStatement(userId, new ReportService.Period(from, to)));
	}

	@GetMapping("/api/v1/admin/reports/financial-summary")
	@PreAuthorize("hasAnyAuthority('REPORT_VIEW', 'REPORT_GENERATE')")
	public ResponseEntity<?> financialSummary(AuthenticatedUser actor,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) String format) {
		return produce(actor, "FINANCIAL_SUMMARY", format, Map.of("from", from.toString(), "to", to.toString()),
				() -> reports.financialSummary(new ReportService.Period(from, to)));
	}

	@GetMapping("/api/v1/admin/reports/offerings")
	@PreAuthorize("hasAnyAuthority('REPORT_VIEW', 'REPORT_GENERATE')")
	public ResponseEntity<?> offerings(AuthenticatedUser actor, @RequestParam(required = false) String format) {
		return produce(actor, "OFFERINGS", format, Map.of(), reports::offerings);
	}

	/** Invoice PDFs for staff who can see orders; not a report, so not gated by the report permissions. */
	@GetMapping("/api/v1/admin/reports/invoices/{orderId}")
	@PreAuthorize("hasAuthority('ORDER_VIEW')")
	public ResponseEntity<?> invoice(@PathVariable UUID orderId, @RequestParam(required = false) String format) {
		return responses.render(reports.invoice(invoices.forOrder(orderId, orders.detail(orderId).orderNumber(), null)),
				ReportFormat.parse(format == null ? "pdf" : format));
	}

	/** Key figures for the admin overview, limited to what the viewer may see. */
	@GetMapping("/api/v1/admin/dashboard")
	@PreAuthorize("isAuthenticated()")
	public List<KpiTile> dashboard(AuthenticatedUser viewer) {
		return dashboard.tiles(viewer);
	}

	private ResponseEntity<?> produce(AuthenticatedUser actor, String report, String rawFormat, Map<String, String> parameters,
			Supplier<ReportDocument> build) {
		ReportFormat format = ReportFormat.parse(rawFormat);
		if (format != ReportFormat.JSON && !actor.hasPermission("REPORT_GENERATE")) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "Downloading reports needs the REPORT_GENERATE permission");
		}
		if (format == ReportFormat.JSON && !actor.hasPermission("REPORT_VIEW") && !actor.hasPermission("REPORT_GENERATE")) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "Viewing reports needs the REPORT_VIEW permission");
		}
		ResponseEntity<?> response = responses.render(build.get(), format);
		// Audited once the file exists: a failed generation hands nothing out.
		if (format != ReportFormat.JSON) {
			Map<String, Object> audited = new LinkedHashMap<>(parameters);
			audited.put("report", report);
			audited.put("format", format);
			responses.audit(actor.userId(), AuditAction.REPORT_GENERATED, report, audited);
		}
		return response;
	}

}
