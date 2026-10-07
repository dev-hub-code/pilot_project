package com.sealease.backend.reporting.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.reporting.engine.ReportDocument;
import com.sealease.backend.reporting.engine.ReportFormat;
import com.sealease.backend.reporting.engine.ReportRenderer;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.UUID;

/** Turns report documents into HTTP responses: JSON previews, or PDF/CSV downloads that are never cached. */
@Component
public class ReportResponses {

	private final ReportRenderer renderer;
	private final AuditService audit;
	private final TransactionTemplate transactions;

	public ReportResponses(ReportRenderer renderer, AuditService audit, PlatformTransactionManager transactionManager) {
		this.renderer = renderer;
		this.audit = audit;
		this.transactions = new TransactionTemplate(transactionManager);
	}

	public ResponseEntity<?> render(ReportDocument document, ReportFormat format) {
		if (format == ReportFormat.JSON) {
			return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(document);
		}
		byte[] body = format == ReportFormat.PDF ? renderer.pdf(document) : renderer.csv(document);
		return ResponseEntity.ok()
			.contentType(format.mediaType())
			.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
				.filename(document.filename() + "." + format.extension()).build().toString())
			.cacheControl(CacheControl.noStore())
			.body(body);
	}

	/** Records that a report was produced, in its own transaction (reports themselves only read). */
	public void audit(UUID actorId, AuditAction action, String report, Map<String, ?> details) {
		transactions.executeWithoutResult(status ->
				audit.record(AuditRecord.of(actorId, action, "REPORT", report).withNewValue(details)));
	}

}
