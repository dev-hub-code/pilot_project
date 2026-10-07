package com.sealease.backend.invoice.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.common.money.Money;
import com.sealease.backend.invoice.dto.InvoiceResponse;
import com.sealease.backend.invoice.entity.Invoice;
import com.sealease.backend.invoice.entity.InvoiceLine;
import com.sealease.backend.invoice.repository.InvoiceLineRepository;
import com.sealease.backend.invoice.repository.InvoiceRepository;
import com.sealease.backend.kafka.KafkaTopics;
import com.sealease.backend.outbox.DomainEvent;
import com.sealease.backend.outbox.OutboxPublisher;
import com.sealease.backend.user.dto.ProfileResponse;
import com.sealease.backend.user.service.UserProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Issues invoices for confirmed orders. Numbers come from a database sequence ({@code INV-2026-000042});
 * sequences never roll back, so a number can be skipped by a failed transaction but never reused.
 */
@Service
public class InvoiceService {

	private static final String ENTITY = "INVOICE";

	private final InvoiceRepository invoices;
	private final InvoiceLineRepository lines;
	private final UserProfileService profiles;
	private final InvoiceProperties properties;
	private final OutboxPublisher outbox;
	private final AuditService audit;
	private final Clock clock;

	public InvoiceService(InvoiceRepository invoices, InvoiceLineRepository lines, UserProfileService profiles,
			InvoiceProperties properties, OutboxPublisher outbox, AuditService audit, Clock clock) {
		this.invoices = invoices;
		this.lines = lines;
		this.profiles = profiles;
		this.properties = properties;
		this.outbox = outbox;
		this.audit = audit;
		this.clock = clock;
	}

	/** Called by order confirmation, in its transaction. One invoice per order (unique key). */
	@Transactional(propagation = Propagation.MANDATORY)
	public InvoiceResponse issue(InvoiceRequest request) {
		Instant now = clock.instant();
		String number = "INV-%d-%06d".formatted(now.atZone(ZoneOffset.UTC).getYear(), invoices.nextNumber());
		ProfileResponse buyer = profiles.getProfile(request.userId());
		Money total = request.total();

		Invoice invoice = invoices.saveAndFlush(new Invoice(number, request.orderId(), request.userId(),
				request.paymentId(), total, new Invoice.Party(properties.issuerName(), properties.issuerAddress()),
				blankToNull(properties.issuerTaxId()),
				new Invoice.Party(buyer.firstName() + " " + buyer.lastName(), address(buyer.address())), buyer.email(),
				blankToNull(properties.notes()), now));
		int n = 1;
		for (InvoiceRequest.Line line : request.lines()) {
			lines.save(new InvoiceLine(invoice.getId(), n++, line.description(), line.amount().amount()));
		}
		lines.flush();

		audit.record(AuditRecord.of(null, AuditAction.INVOICE_ISSUED, ENTITY, invoice.getId())
			.withNewValue(Map.of("invoiceNumber", number, "orderId", request.orderId().toString(),
					"total", total.toString())));
		outbox.publish(DomainEvent.of(KafkaTopics.INVOICE_GENERATED, "InvoiceGenerated", ENTITY, invoice.getId(),
				Map.of("invoiceId", invoice.getId().toString(), "invoiceNumber", number,
						"orderId", request.orderId().toString(), "userId", request.userId().toString(),
						"total", total.amount().toPlainString(), "currency", total.currency().getCurrencyCode())));
		return InvoiceResponse.from(invoice, request.orderNumber(),
				lines.findByInvoiceIdOrderByLineNumber(invoice.getId()));
	}

	/** The invoice of an order; with {@code ownerId}, only if that user owns it. */
	@Transactional(readOnly = true)
	public InvoiceResponse forOrder(UUID orderId, String orderNumber, UUID ownerId) {
		Invoice invoice = invoices.findByOrderId(orderId)
			.filter(i -> ownerId == null || i.getUserId().equals(ownerId))
			.orElseThrow(() -> new ResourceNotFoundException("Invoice for order", orderId));
		return InvoiceResponse.from(invoice, orderNumber, lines.findByInvoiceIdOrderByLineNumber(invoice.getId()));
	}

	private static String address(ProfileResponse.Address a) {
		if (a == null) {
			return null;
		}
		String cityLine = Stream.of(a.postalCode(), a.city()).filter(Objects::nonNull).collect(Collectors.joining(" "));
		String joined = Stream.of(a.line1(), a.line2(), cityLine, a.stateRegion(), a.country())
			.filter(s -> s != null && !s.isBlank())
			.collect(Collectors.joining(", "));
		return joined.isEmpty() ? null : joined;
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

}
