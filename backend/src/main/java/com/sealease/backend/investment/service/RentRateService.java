package com.sealease.backend.investment.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyProperties;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.investment.dto.RentTier;
import com.sealease.backend.investment.dto.RentTiersRequest;
import com.sealease.backend.investment.dto.RentTiersResponse;
import com.sealease.backend.user.dto.UserAccount;
import com.sealease.backend.user.service.UserAccountService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Monthly rent by order value, set by staff ({@code rent_rate_tiers}). An order earns the rate of the band its total
 * falls in, on every container in it; the rate is fixed on the order when it is placed, so a change applies to orders
 * placed afterwards only. An order below the lowest band earns no rent and cannot be placed (usually the lowest band
 * starts at the minimum order value). Amounts are in the platform's currency.
 */
@Service
public class RentRateService {

	private static final String ENTITY = "RENT_RATE_TIERS";

	private final JdbcTemplate jdbc;
	private final MoneyProperties money;
	private final UserAccountService accounts;
	private final AuditService audit;
	private final Clock clock;

	public RentRateService(JdbcTemplate jdbc, MoneyProperties money, UserAccountService accounts, AuditService audit,
			Clock clock) {
		this.jdbc = jdbc;
		this.money = money;
		this.accounts = accounts;
		this.audit = audit;
		this.clock = clock;
	}

	/**
	 * The monthly rent percentage for an order of {@code orderTotal}; {@code null} when no band covers it. A band's
	 * upper amount is in whole rupees, so it covers totals up to the next rupee (e.g. "to 4,99,999" takes 4,99,999.50).
	 */
	public BigDecimal percentFor(Money orderTotal) {
		List<BigDecimal> rate = jdbc.queryForList("""
				SELECT monthly_rent_percent FROM rent_rate_tiers
				WHERE min_order_amount <= ? AND (max_order_amount IS NULL OR ? < max_order_amount + 1)
				ORDER BY min_order_amount DESC LIMIT 1""", BigDecimal.class, orderTotal.amount(), orderTotal.amount());
		return rate.isEmpty() ? null : rate.getFirst();
	}

	/** Why an order of {@code orderTotal} earns no rent (outside every band); empty when it does. */
	public List<String> problems(Money orderTotal) {
		if (percentFor(orderTotal) != null) {
			return List.of();
		}
		List<Row> rows = rows();
		if (rows.isEmpty()) {
			return List.of("No rent rates are set yet; orders cannot be placed");
		}
		Money from = Money.of(rows.getFirst().from(), orderTotal.currency());
		if (orderTotal.isLessThan(from)) {
			return List.of("Orders earn rent from " + from.display() + "; add " + from.minus(orderTotal).display() + " more");
		}
		Money to = Money.of(rows.getLast().to(), orderTotal.currency());
		return List.of("Orders earn rent up to " + to.display() + "; this order is above it");
	}

	@Transactional(readOnly = true)
	public RentTiersResponse get() {
		List<Row> rows = rows();
		Row latest = rows.stream().max((a, b) -> a.updatedAt().compareTo(b.updatedAt())).orElse(null);
		String by = null;
		if (latest != null && latest.updatedBy() != null) {
			UserAccount a = accounts.getAccounts(List.of(latest.updatedBy())).get(latest.updatedBy());
			by = a == null ? null : (a.firstName() + " " + a.lastName()).strip();
		}
		List<RentTier> tiers = rows.stream()
			.map(r -> new RentTier(amount(r.from()), r.to() == null ? null : amount(r.to()), r.percent()))
			.toList();
		return new RentTiersResponse(tiers, latest == null ? null : latest.updatedAt(), by);
	}

	/** Replaces every band; applies to orders placed from now on. */
	@Transactional
	public RentTiersResponse update(UUID actorId, RentTiersRequest request) {
		List<RentTiersRequest.Tier> next = request.tiers().stream()
			.sorted((a, b) -> a.fromAmount().compareTo(b.fromAmount()))
			.toList();
		List<String> problems = new ArrayList<>();
		for (int i = 0; i < next.size(); i++) {
			RentTiersRequest.Tier t = next.get(i);
			String band = "Band " + (i + 1);
			boolean last = i == next.size() - 1;
			if (t.toAmount() == null && !last) {
				problems.add(band + ": enter the amount it goes up to (only the last band can be open-ended)");
			}
			if (t.toAmount() != null && t.toAmount().compareTo(t.fromAmount()) < 0) {
				problems.add(band + ": the amount it goes up to must be at least the amount it starts from");
			}
			if (!last && t.toAmount() != null) {
				// The next band starts on the rupee after this one ends: no overlap and no gap.
				BigDecimal expected = t.toAmount().add(BigDecimal.ONE);
				if (next.get(i + 1).fromAmount().compareTo(expected) != 0) {
					problems.add("Band " + (i + 2) + " must start at " + amount(expected).amount()
							+ ", right after band " + (i + 1) + " ends");
				}
			}
		}
		if (!problems.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, String.join("; ", problems));
		}
		List<Row> before = rows();
		jdbc.execute("LOCK TABLE rent_rate_tiers IN EXCLUSIVE MODE");
		jdbc.update("DELETE FROM rent_rate_tiers");
		Timestamp now = Timestamp.from(clock.instant());
		for (RentTiersRequest.Tier t : next) {
			jdbc.update("""
					INSERT INTO rent_rate_tiers (min_order_amount, max_order_amount, monthly_rent_percent, updated_by, updated_at)
					VALUES (?, ?, ?, ?, ?)""", t.fromAmount(), t.toAmount(), t.monthlyRentPercent(), actorId, now);
		}
		audit.record(AuditRecord.of(actorId, AuditAction.RENT_RATE_TIERS_CHANGED, ENTITY, null)
			.withOldValue(Map.of("tiers", describe(before.stream()
				.map(r -> new RentTiersRequest.Tier(r.from(), r.to(), r.percent())).toList())))
			.withNewValue(Map.of("tiers", describe(next))));
		return get();
	}

	private List<Row> rows() {
		return jdbc.query("""
				SELECT min_order_amount, max_order_amount, monthly_rent_percent, updated_by, updated_at FROM rent_rate_tiers
				ORDER BY min_order_amount""",
				(rs, i) -> new Row(rs.getBigDecimal("min_order_amount"), rs.getBigDecimal("max_order_amount"),
						rs.getBigDecimal("monthly_rent_percent"), rs.getObject("updated_by", UUID.class),
						rs.getTimestamp("updated_at").toInstant()));
	}

	private MoneyResponse amount(BigDecimal value) {
		return MoneyResponse.from(Money.of(value, money.defaultCurrency()));
	}

	/** "100000-499999: 5%, 500000 and above: 5.5%" for the audit trail. */
	private static List<String> describe(List<RentTiersRequest.Tier> tiers) {
		return tiers.stream()
			.map(t -> t.fromAmount().stripTrailingZeros().toPlainString()
					+ (t.toAmount() == null ? " and above" : "-" + t.toAmount().stripTrailingZeros().toPlainString()) + ": "
					+ t.monthlyRentPercent().stripTrailingZeros().toPlainString() + "%")
			.toList();
	}

	private record Row(BigDecimal from, BigDecimal to, BigDecimal percent, UUID updatedBy, Instant updatedAt) {
	}

}
