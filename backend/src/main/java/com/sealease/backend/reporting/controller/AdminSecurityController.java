package com.sealease.backend.reporting.controller;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditCategory;
import com.sealease.backend.audit.AuditLogFilter;
import com.sealease.backend.common.api.PageResponse;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.reporting.dto.AuditLogEntryResponse;
import com.sealease.backend.reporting.dto.SecurityOverview;
import com.sealease.backend.reporting.service.AuditLogService;
import com.sealease.backend.reporting.service.SecurityDashboardService;
import com.sealease.backend.security.AuthenticatedUser;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

/** The security dashboard and the audit log, for staff with AUDIT_VIEW. */
@RestController
@PreAuthorize("hasAuthority('AUDIT_VIEW')")
public class AdminSecurityController {

	private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

	private final SecurityDashboardService dashboard;
	private final AuditLogService log;
	private final Clock clock;

	public AdminSecurityController(SecurityDashboardService dashboard, AuditLogService log, Clock clock) {
		this.dashboard = dashboard;
		this.log = log;
		this.clock = clock;
	}

	/** @param hours the period: 24, 168 (7 days), 720 (30 days) or 2160 (90 days) */
	@GetMapping("/api/v1/admin/security/overview")
	public SecurityOverview overview(@RequestParam(defaultValue = "168") int hours) {
		return dashboard.overview(hours);
	}

	/**
	 * Newest first.
	 *
	 * @param actor who did it: an account's email or user id
	 * @param from  first UTC day included
	 * @param to    last UTC day included
	 */
	@GetMapping("/api/v1/admin/audit-logs")
	public PageResponse<AuditLogEntryResponse> search(
			@RequestParam(required = false) AuditCategory category,
			@RequestParam(required = false) AuditAction action,
			@RequestParam(required = false) @Size(max = 320) String actor,
			@RequestParam(required = false) @Size(max = 64) String entityType,
			@RequestParam(required = false) @Size(max = 64) String entityId,
			@RequestParam(required = false) @Size(max = 45) String ip,
			@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to,
			@PageableDefault(size = 50) Pageable pageable) {
		Actor who = Actor.parse(actor);
		return log.search(filter(category, action, who, entityType, entityId, ip, from, to), who.email(), pageable);
	}

	@GetMapping("/api/v1/admin/audit-logs/{id}")
	public AuditLogEntryResponse entry(@PathVariable UUID id) {
		return log.get(id);
	}

	/** The same filters as the search, as a CSV download (audited). */
	@GetMapping("/api/v1/admin/audit-logs/export")
	public ResponseEntity<byte[]> export(AuthenticatedUser viewer,
			@RequestParam(required = false) AuditCategory category,
			@RequestParam(required = false) AuditAction action,
			@RequestParam(required = false) @Size(max = 320) String actor,
			@RequestParam(required = false) @Size(max = 64) String entityType,
			@RequestParam(required = false) @Size(max = 64) String entityId,
			@RequestParam(required = false) @Size(max = 45) String ip,
			@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to) {
		Actor who = Actor.parse(actor);
		byte[] body = log.export(viewer.userId(), filter(category, action, who, entityType, entityId, ip, from, to),
				who.email());
		String name = "audit-log-" + LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC) + ".csv";
		return ResponseEntity.ok()
			.contentType(CSV)
			.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
			.cacheControl(CacheControl.noStore())
			.body(body);
	}

	private static AuditLogFilter filter(AuditCategory category, AuditAction action, Actor who, String entityType,
			String entityId, String ip, LocalDate from, LocalDate to) {
		if (from != null && to != null && to.isBefore(from)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "The end date must not be before the start date");
		}
		return new AuditLogFilter(category, action, who.id(), blankToNull(entityType, true), blankToNull(entityId, false),
				blankToNull(ip, false), from == null ? null : from.atStartOfDay(ZoneOffset.UTC).toInstant(),
				to == null ? null : to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
	}

	private static String blankToNull(String value, boolean upperCase) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String v = value.strip();
		return upperCase ? v.toUpperCase(Locale.ROOT) : v;
	}

	/** The actor filter: a user id, an email to look up, or neither. */
	private record Actor(UUID id, String email) {

		static Actor parse(String value) {
			String v = blankToNull(value, false);
			if (v == null) {
				return new Actor(null, null);
			}
			try {
				return new Actor(UUID.fromString(v), null);
			} catch (IllegalArgumentException notAnId) {
				return new Actor(null, v);
			}
		}
	}

}
