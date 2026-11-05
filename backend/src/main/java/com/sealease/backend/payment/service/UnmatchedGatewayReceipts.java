package com.sealease.backend.payment.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.payment.dto.UnmatchedReceiptResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Money the gateway reports for an order or virtual account that is none of ours. It is recorded once per gateway
 * payment (repeated webhooks change nothing) so finance can trace and refund it, instead of only being logged.
 */
@Service
public class UnmatchedGatewayReceipts {

	private static final Logger log = LoggerFactory.getLogger(UnmatchedGatewayReceipts.class);
	private static final String ENTITY = "UNMATCHED_GATEWAY_RECEIPT";

	private final JdbcTemplate jdbc;
	private final AuditService audit;
	private final Clock clock;

	public UnmatchedGatewayReceipts(JdbcTemplate jdbc, AuditService audit, Clock clock) {
		this.jdbc = jdbc;
		this.audit = audit;
		this.clock = clock;
	}

	/** Records the receipt in the caller's transaction; a gateway payment already recorded is left as it is. */
	@Transactional
	public void record(String provider, String providerReference, String gatewayPaymentId, Money received) {
		UUID id = UUID.randomUUID();
		int inserted = jdbc.update("""
				INSERT INTO unmatched_gateway_receipts (id, provider, provider_reference, gateway_payment_id, amount, currency, received_at)
				VALUES (?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT (provider, gateway_payment_id) DO NOTHING""",
				id, provider, providerReference, gatewayPaymentId,
				received == null ? null : received.amount(),
				received == null ? null : received.currency().getCurrencyCode(),
				Timestamp.from(clock.instant()));
		if (inserted == 0) {
			return;
		}
		log.warn("{} reported payment {} for {}, which is not one of ours: recorded for finance as {}", provider,
				gatewayPaymentId, providerReference, id);
		Map<String, Object> details = new HashMap<>();
		details.put("provider", provider);
		details.put("providerReference", providerReference);
		details.put("gatewayPaymentId", gatewayPaymentId);
		if (received != null) {
			details.put("amount", MoneyResponse.from(received).amount());
			details.put("currency", received.currency().getCurrencyCode());
		}
		audit.record(AuditRecord.of(null, AuditAction.PAYMENT_UNMATCHED_RECEIVED, ENTITY, id).withNewValue(details));
	}

	/** Open receipts first (oldest first), then the most recently resolved. */
	@Transactional(readOnly = true)
	public List<UnmatchedReceiptResponse> list(boolean openOnly) {
		return jdbc.query("""
				SELECT * FROM unmatched_gateway_receipts
				WHERE ? = false OR resolved_at IS NULL
				ORDER BY resolved_at IS NOT NULL, coalesce(resolved_at, received_at) DESC
				LIMIT 500""", ROW, openOnly);
	}

	/** Finance traced the money (refunded it, or matched it by hand): closes it with a note. */
	@Transactional
	public UnmatchedReceiptResponse resolve(UUID actorId, UUID id, String resolution) {
		String note = resolution.strip();
		int updated = jdbc.update("""
				UPDATE unmatched_gateway_receipts SET resolved_by = ?, resolved_at = ?, resolution = ?
				WHERE id = ? AND resolved_at IS NULL""", actorId, Timestamp.from(clock.instant()), note, id);
		if (updated == 0) {
			jdbc.query("SELECT * FROM unmatched_gateway_receipts WHERE id = ?", ROW, id).stream().findFirst()
				.orElseThrow(() -> new ResourceNotFoundException("Unmatched receipt", id));
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "This receipt is already resolved");
		}
		audit.record(AuditRecord.of(actorId, AuditAction.PAYMENT_UNMATCHED_RESOLVED, ENTITY, id)
			.withNewValue(Map.of("resolution", note)));
		return jdbc.queryForObject("SELECT * FROM unmatched_gateway_receipts WHERE id = ?", ROW, id);
	}

	private static final RowMapper<UnmatchedReceiptResponse> ROW = (rs, n) -> {
		BigDecimal amount = rs.getBigDecimal("amount");
		String currency = rs.getString("currency");
		MoneyResponse money = amount == null || currency == null ? null
				: MoneyResponse.from(Money.of(amount, Currency.getInstance(currency)));
		return new UnmatchedReceiptResponse(rs.getObject("id", UUID.class), rs.getString("provider"),
				rs.getString("provider_reference"), rs.getString("gateway_payment_id"), money,
				instant(rs.getTimestamp("received_at")), rs.getObject("resolved_by", UUID.class),
				instant(rs.getTimestamp("resolved_at")), rs.getString("resolution"));
	};

	private static Instant instant(Timestamp at) {
		return at == null ? null : at.toInstant();
	}

}
