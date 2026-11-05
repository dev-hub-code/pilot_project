package com.sealease.backend.withdrawal.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.common.time.DisplayTimeZone;
import com.sealease.backend.ledger.service.EarningsBalance;
import com.sealease.backend.payment.gateway.RazorpaySignatures;
import com.sealease.backend.reporting.engine.ReportDocument;
import com.sealease.backend.reporting.engine.ReportDocument.Row;
import com.sealease.backend.reporting.engine.ReportRenderer;
import com.sealease.backend.user.dto.UserAccount;
import com.sealease.backend.user.service.UserAccountService;
import com.sealease.backend.withdrawal.dto.InitiatePaymentResponse;
import com.sealease.backend.withdrawal.entity.Withdrawal;
import com.sealease.backend.withdrawal.entity.WithdrawalBatch;
import com.sealease.backend.withdrawal.entity.WithdrawalKind;
import com.sealease.backend.withdrawal.entity.WithdrawalOrigin;
import com.sealease.backend.withdrawal.entity.WithdrawalPayoutMethod;
import com.sealease.backend.withdrawal.entity.WithdrawalStatus;
import com.sealease.backend.withdrawal.repository.WithdrawalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Paying approved withdrawals: handing them to the payout provider, settling them from a provider's signed webhook,
 * and the investor's receipt once paid.
 *
 * <p>Nothing here marks a withdrawal paid on a staff member's say-so: payment is initiated by staff, and completed only
 * from evidence (the reconciled bank sheet or the bank's reference entered by finance, in {@link PayoutBatchService},
 * or a verified webhook here). An ambiguous provider outcome (still processing, unknown, amounts not matching) is
 * recorded and left for finance to reconcile, never retried automatically, so nothing is ever paid twice.
 */
@Service
public class WithdrawalPaymentService {

	private static final Logger log = LoggerFactory.getLogger(WithdrawalPaymentService.class);

	/** The provider name webhook events are recorded under. */
	public static final String WEBHOOK_PROVIDER = "PAYOUT_WEBHOOK";

	private final WithdrawalRepository withdrawals;
	private final PayoutBatchService batches;
	private final List<PayoutProvider> providers;
	private final WithdrawalProperties properties;
	private final UserAccountService accounts;
	private final AuditService audit;
	private final ReportRenderer renderer;
	private final DisplayTimeZone zone;
	private final JdbcTemplate jdbc;
	private final JsonMapper json;
	private final Clock clock;

	public WithdrawalPaymentService(WithdrawalRepository withdrawals, PayoutBatchService batches,
			List<PayoutProvider> providers, WithdrawalProperties properties, UserAccountService accounts,
			AuditService audit, ReportRenderer renderer, DisplayTimeZone zone, JdbcTemplate jdbc, JsonMapper json,
			Clock clock) {
		this.withdrawals = withdrawals;
		this.batches = batches;
		this.providers = providers;
		this.properties = properties;
		this.accounts = accounts;
		this.audit = audit;
		this.renderer = renderer;
		this.zone = zone;
		this.jdbc = jdbc;
		this.json = json;
		this.clock = clock;
	}

	// --------------------------------------------------------------------------------------------- initiate

	/**
	 * Hands approved online requests to the payout provider, grouped into one batch per kind and payout method. All or
	 * nothing: any withdrawal that is not an approved request stops the whole call.
	 */
	@Transactional
	public InitiatePaymentResponse initiate(UUID actorId, List<UUID> withdrawalIds) {
		PayoutProvider provider = provider();
		Instant now = clock.instant();
		Map<String, List<Withdrawal>> groups = new LinkedHashMap<>();
		// Locked in a stable order, so two staff members initiating overlapping lists cannot deadlock.
		for (UUID id : withdrawalIds.stream().distinct().sorted().toList()) {
			Withdrawal w = withdrawals.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Withdrawal", id));
			if (actorId.equals(w.getUserId())) {
				throw new BusinessException(ErrorCode.FORBIDDEN, "You cannot pay your own withdrawal");
			}
			if (w.getOrigin() != WithdrawalOrigin.REQUESTED) {
				throw new BusinessException(ErrorCode.CONFLICT, w.getReference()
						+ " is paid with its withdrawal run, not from here");
			}
			w.initiatePayment(actorId, provider.name(), now);
			groups.computeIfAbsent(kind(w) + "|" + w.getPayoutMethod(), k -> new ArrayList<>()).add(w);
		}
		withdrawals.flush();
		List<InitiatePaymentResponse.Batch> made = new ArrayList<>();
		for (List<Withdrawal> items : groups.values()) {
			Withdrawal first = items.getFirst();
			WithdrawalBatch batch = provider.initiate(kind(first), first.getPayoutMethod(), items, actorId);
			for (Withdrawal w : items) {
				audit.record(AuditRecord.of(actorId, AuditAction.WITHDRAWAL_PAYMENT_INITIATED, WithdrawalService.ENTITY,
						w.getId())
					.withNewValue(Map.of("provider", provider.name(), "batch", batch.getReference(),
							"net", w.net().toString())));
			}
			made.add(new InitiatePaymentResponse.Batch(batch.getId(), batch.getReference(), items.size()));
		}
		return new InitiatePaymentResponse(provider.name(), made);
	}

	private PayoutProvider provider() {
		return providers.stream().filter(p -> p.name().equalsIgnoreCase(properties.payoutProvider())).findFirst()
			.orElseThrow(() -> new IllegalStateException("No payout provider named " + properties.payoutProvider()));
	}

	private static WithdrawalKind kind(Withdrawal w) {
		return w.getBalance() == EarningsBalance.REFERRAL ? WithdrawalKind.REFERRAL : WithdrawalKind.RENT;
	}

	// --------------------------------------------------------------------------------------------- webhook

	/** What became of one webhook delivery. */
	public enum WebhookOutcome {
		PAID, FAILED, DUPLICATE, IGNORED, UNKNOWN_WITHDRAWAL, AMOUNT_MISMATCH, NEEDS_RECONCILIATION
	}

	public boolean webhookEnabled() {
		return !properties.payoutWebhookSecret().isBlank();
	}

	/**
	 * A payout provider's status update, signed with the webhook secret (HMAC-SHA256 of the raw body, hex):
	 * <pre>{"id": "evt_1", "type": "payout.paid" | "payout.failed" | "payout.processing",
	 *  "withdrawalReference": "WD-100001", "providerReference": "UTR123", "amount": "990.00", "currency": "INR",
	 *  "reason": "why it failed"}</pre>
	 * Each event id is recorded once: a redelivery changes nothing. Paid is accepted only for a withdrawal with the
	 * provider (PROCESSING) and only for exactly its net amount; anything else is recorded for reconciliation.
	 */
	@Transactional
	public WebhookOutcome onWebhook(String body, String signature) {
		if (!RazorpaySignatures.webhookValid(properties.payoutWebhookSecret(), body, signature)) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid webhook signature");
		}
		JsonNode event = json.readTree(body);
		String eventId = text(event, "id");
		String type = text(event, "type");
		if (eventId == null || type == null) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "An event needs an id and a type");
		}
		String reference = text(event, "withdrawalReference");
		String providerReference = text(event, "providerReference");
		Optional<Withdrawal> found = reference == null ? Optional.empty() : withdrawals.findByReference(reference);
		// Recorded first: a second delivery of the same event stops here.
		int inserted = jdbc.update("""
				INSERT INTO payout_webhook_events (id, provider, event_id, event_type, withdrawal_id, provider_reference,
				                                   outcome, payload, received_at)
				VALUES (?, ?, ?, ?, ?, ?, 'RECEIVED', CAST(? AS jsonb), ?) ON CONFLICT (provider, event_id) DO NOTHING""",
				UUID.randomUUID(), WEBHOOK_PROVIDER, eventId, type, found.map(Withdrawal::getId).orElse(null),
				providerReference, body, Timestamp.from(clock.instant()));
		if (inserted == 0) {
			return WebhookOutcome.DUPLICATE;
		}
		WebhookOutcome outcome = settle(type, found, providerReference, event);
		jdbc.update("UPDATE payout_webhook_events SET outcome = ? WHERE provider = ? AND event_id = ?", outcome.name(),
				WEBHOOK_PROVIDER, eventId);
		audit.record(AuditRecord.of(null, AuditAction.PAYOUT_WEBHOOK_RECEIVED, WithdrawalService.ENTITY,
				found.map(Withdrawal::getId).orElse(null))
			.withNewValue(Map.of("event", eventId, "type", type, "outcome", outcome.name(),
					"providerReference", providerReference == null ? "" : providerReference)));
		return outcome;
	}

	private WebhookOutcome settle(String type, Optional<Withdrawal> found, String providerReference, JsonNode event) {
		boolean paid = "payout.paid".equals(type);
		boolean failed = "payout.failed".equals(type);
		if (!paid && !failed) {
			// Still processing, reversed, or something we do not know: never acted on automatically.
			return "payout.processing".equals(type) ? WebhookOutcome.IGNORED : WebhookOutcome.NEEDS_RECONCILIATION;
		}
		if (found.isEmpty()) {
			log.warn("Payout webhook {} for an unknown withdrawal", type);
			return WebhookOutcome.UNKNOWN_WITHDRAWAL;
		}
		Withdrawal w = found.get();
		if (paid) {
			if (providerReference == null) {
				return WebhookOutcome.NEEDS_RECONCILIATION;
			}
			BigDecimal amount = decimal(text(event, "amount"));
			if (amount == null || amount.compareTo(w.net().amount()) != 0
					|| !w.getCurrency().equalsIgnoreCase(String.valueOf(text(event, "currency")))) {
				log.warn("Payout webhook amount does not match withdrawal {}", w.getReference());
				return WebhookOutcome.AMOUNT_MISMATCH;
			}
		}
		boolean changed = batches.settleFromProvider(w.getId(), paid, providerReference, text(event, "reason"));
		if (!changed) {
			// Already settled, or not with the provider: left for finance, never re-applied.
			return w.getStatus() == WithdrawalStatus.PAID && paid ? WebhookOutcome.DUPLICATE
					: WebhookOutcome.NEEDS_RECONCILIATION;
		}
		return paid ? WebhookOutcome.PAID : WebhookOutcome.FAILED;
	}

	// --------------------------------------------------------------------------------------------- receipt

	/** The receipt of a paid withdrawal, as a PDF: the investor's own (or, with {@code userId} null, staff's). */
	@Transactional
	public Receipt receipt(UUID userId, UUID withdrawalId) {
		Withdrawal w = withdrawals.findById(withdrawalId)
			.filter(x -> userId == null || x.getUserId().equals(userId))
			.orElseThrow(() -> new ResourceNotFoundException("Withdrawal", withdrawalId));
		if (w.getStatus() != WithdrawalStatus.PAID) {
			throw new BusinessException(ErrorCode.CONFLICT, "A receipt is available once the withdrawal is paid");
		}
		UserAccount investor = accounts.getAccount(w.getUserId());
		DateTimeFormatter at = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH).withZone(zone.zone());
		List<Row> details = new ArrayList<>();
		details.add(Row.of("Withdrawal", w.getReference(), "", ""));
		details.add(Row.of("Investor", (investor.firstName() + " " + investor.lastName()).strip(), investor.userCode(), ""));
		details.add(Row.of("Requested", at.format(w.getCreatedAt()), "", ""));
		details.add(Row.of("Paid", w.getClosedAt() == null ? "" : at.format(w.getClosedAt()), "", ""));
		details.add(Row.of("Paid by", w.isOfficeCash() ? "Cash at the office"
				: "Bank transfer to " + w.getBankName() + " •••• " + w.getBankAccountLast4(), "", ""));
		details.add(Row.of(w.isOfficeCash() ? "Receipt number" : "Bank reference (UTR)",
				w.getPayoutReference() == null ? "" : w.getPayoutReference(), "", ""));
		List<Row> amounts = List.of(
				Row.of("Amount withdrawn", "", "", w.amount().display()),
				Row.of("Charges", "", "", "− " + w.fee().display()),
				Row.of("Tax withheld (TDS)", "", "", "− " + w.tax().display()),
				Row.total("Amount paid", "", "", w.net().display()));
		Instant now = clock.instant();
		ReportDocument document = new ReportDocument("Withdrawal receipt", w.getReference(),
				"withdrawal-receipt-" + w.getReference(), now,
				List.of(new ReportDocument.Section("Details", List.of("", "", "", ""), details),
						new ReportDocument.Section("Amounts", List.of("", "", "", "Amount"), amounts)),
				"Times are in " + zone.longName() + ". This receipt is generated from our records.");
		audit.record(AuditRecord.of(userId, AuditAction.WITHDRAWAL_RECEIPT_DOWNLOADED, WithdrawalService.ENTITY, w.getId()));
		return new Receipt(document.filename(), renderer.pdf(document));
	}

	public record Receipt(String filename, byte[] content) {
	}

	// --------------------------------------------------------------------------------------------- internal

	private static String text(JsonNode node, String field) {
		JsonNode value = node.path(field);
		return value.isMissingNode() || value.isNull() || value.asString().isBlank() ? null : value.asString().strip();
	}

	private static BigDecimal decimal(String value) {
		try {
			return value == null ? null : new BigDecimal(value);
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

}
