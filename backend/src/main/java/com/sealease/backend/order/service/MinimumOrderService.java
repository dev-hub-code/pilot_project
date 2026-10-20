package com.sealease.backend.order.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyProperties;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.order.dto.MinimumOrderResponse;
import com.sealease.backend.user.dto.UserAccount;
import com.sealease.backend.user.service.UserAccountService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The smallest total an order may have, set by staff (one row, {@code order_settings}); zero means no minimum. The
 * cart cannot be checked out below it, and checkout refuses such an order.
 */
@Service
public class MinimumOrderService {

	private static final String ENTITY = "ORDER_SETTINGS";

	private final JdbcTemplate jdbc;
	private final MoneyProperties money;
	private final UserAccountService accounts;
	private final AuditService audit;
	private final Clock clock;

	public MinimumOrderService(JdbcTemplate jdbc, MoneyProperties money, UserAccountService accounts, AuditService audit,
			Clock clock) {
		this.jdbc = jdbc;
		this.money = money;
		this.accounts = accounts;
		this.audit = audit;
		this.clock = clock;
	}

	/** The minimum in force; zero when there is none. */
	public Money current() {
		BigDecimal amount = jdbc.queryForObject("SELECT min_order_amount FROM order_settings WHERE id = 1", BigDecimal.class);
		return Money.of(amount, money.defaultCurrency());
	}

	/** Why an order of {@code total} cannot be placed; empty when it can. */
	public List<String> problems(Money total) {
		Money minimum = current();
		return total.isLessThan(minimum)
				? List.of("The minimum order is " + minimum.display() + "; add " + minimum.minus(total).display() + " more")
				: List.of();
	}

	@Transactional(readOnly = true)
	public MinimumOrderResponse get() {
		return jdbc.queryForObject("SELECT min_order_amount, updated_at, updated_by FROM order_settings WHERE id = 1",
				(rs, i) -> {
					Timestamp updatedAt = rs.getTimestamp("updated_at");
					UUID updatedBy = rs.getObject("updated_by", UUID.class);
					String by = null;
					if (updatedBy != null) {
						UserAccount a = accounts.getAccounts(List.of(updatedBy)).get(updatedBy);
						by = a == null ? null : (a.firstName() + " " + a.lastName()).strip();
					}
					return new MinimumOrderResponse(
							MoneyResponse.from(Money.of(rs.getBigDecimal("min_order_amount"), money.defaultCurrency())),
							updatedAt == null ? null : updatedAt.toInstant(), by);
				});
	}

	/** Applies to orders placed from now on. */
	@Transactional
	public MinimumOrderResponse update(UUID actorId, BigDecimal amount) {
		BigDecimal before = jdbc.queryForObject("SELECT min_order_amount FROM order_settings WHERE id = 1 FOR UPDATE",
				BigDecimal.class);
		if (before.compareTo(amount) != 0) {
			jdbc.update("UPDATE order_settings SET min_order_amount = ?, updated_at = ?, updated_by = ? WHERE id = 1",
					amount, Timestamp.from(clock.instant()), actorId);
			audit.record(AuditRecord.of(actorId, AuditAction.MINIMUM_ORDER_CHANGED, ENTITY, null)
				.withOldValue(Map.of("minOrderAmount", before.stripTrailingZeros().toPlainString()))
				.withNewValue(Map.of("minOrderAmount", amount.stripTrailingZeros().toPlainString())));
		}
		return get();
	}

}
