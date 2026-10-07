package com.sealease.backend.ledger.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.common.web.IdempotencyKey;
import com.sealease.backend.ledger.dto.AdjustmentRequest;
import com.sealease.backend.ledger.dto.InvestorStatement;
import com.sealease.backend.ledger.dto.LedgerAccountResponse;
import com.sealease.backend.ledger.dto.LedgerEntryResponse;
import com.sealease.backend.ledger.dto.PeriodTotal;
import com.sealease.backend.ledger.dto.TrialBalanceLine;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Double-entry ledger. Every business event that moves money is posted as one balanced
 * transaction; accounts, transactions and entries are append-only, so a balance is always the sum
 * of its entries and corrections are new (compensating) transactions.
 *
 * <p>Balances are checked here and again by the database at commit ({@code ledger_check_balanced}).
 */
@Service
public class LedgerService {

	private static final String ENTITY = "LEDGER_TRANSACTION";

	/** Signed balance on the account's normal side. */
	private static final String BALANCE = """
			coalesce((SELECT sum(CASE WHEN e.direction = a.normal THEN e.amount ELSE -e.amount END)
			          FROM ledger_entries e WHERE e.account_id = a.id), 0)
			""";
	private static final String ACCOUNTS = """
			SELECT a.id, a.account_type, a.owner_user_id, a.currency, a.created_at, %s AS balance
			FROM (SELECT la.*, CASE WHEN la.account_type IN (%s) THEN 'DEBIT' ELSE 'CREDIT' END AS normal
			      FROM ledger_accounts la) a
			""".formatted(BALANCE, Arrays.stream(AccountType.values())
		.filter(t -> t.normalBalance() == Direction.DEBIT)
		.map(t -> "'" + t.name() + "'")
		.collect(Collectors.joining(", ")));

	private final JdbcTemplate jdbc;
	private final AuditService audit;
	private final Clock clock;

	public LedgerService(JdbcTemplate jdbc, AuditService audit, Clock clock) {
		this.jdbc = jdbc;
		this.audit = audit;
		this.clock = clock;
	}

	/**
	 * Posts one balanced transaction in the caller's transaction. Zero-amount postings are skipped.
	 *
	 * @param reference unique per transaction type, e.g. the rental receipt id
	 * @return the transaction id
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public UUID post(TransactionType type, String reference, String description, UUID actorId, List<Posting> postings) {
		List<Posting> lines = postings.stream().filter(p -> !p.amount().isZero()).toList();
		if (lines.size() < 2) {
			throw new IllegalArgumentException("A ledger transaction needs at least two non-zero postings");
		}
		Currency currency = lines.getFirst().amount().currency();
		Money debits = Money.zero(currency);
		Money credits = Money.zero(currency);
		for (Posting line : lines) {
			if (line.direction() == Direction.DEBIT) {
				debits = debits.plus(line.amount());
			}
			else {
				credits = credits.plus(line.amount());
			}
		}
		if (!debits.equals(credits)) {
			throw new IllegalStateException("Unbalanced " + type + " " + reference + ": debits " + debits
					+ ", credits " + credits);
		}

		OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
		UUID transactionId = UUID.randomUUID();
		int inserted = jdbc.update("""
				INSERT INTO ledger_transactions (id, transaction_type, reference, currency, description, created_by,
				                                 created_at)
				VALUES (?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT (transaction_type, reference) DO NOTHING
				""", transactionId, type.name(), reference, currency.getCurrencyCode(), description, actorId, now);
		if (inserted == 0) {
			throw new BusinessException(ErrorCode.CONFLICT, "This " + type.name().toLowerCase(Locale.ROOT).replace('_', ' ')
					+ " has already been posted");
		}
		List<Object[]> rows = new ArrayList<>();
		for (Posting line : lines) {
			rows.add(new Object[] { UUID.randomUUID(), transactionId,
					accountId(line.accountType(), line.ownerUserId(), currency, now), line.direction().name(),
					line.amount().amount(), now });
		}
		jdbc.batchUpdate("""
				INSERT INTO ledger_entries (id, transaction_id, account_id, direction, amount, created_at)
				VALUES (?, ?, ?, ?, ?, ?)
				""", rows);
		return transactionId;
	}

	/**
	 * Finance corrects an investor's earnings balance. Idempotent per key; a debit may not take the
	 * balance below zero, and nobody may adjust their own balance.
	 */
	@Transactional
	public LedgerAccountResponse adjust(UUID actorId, AdjustmentRequest request, String idempotencyKey) {
		String key = IdempotencyKey.require(idempotencyKey);
		if (request.userId().equals(actorId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "You cannot adjust your own balance");
		}
		Currency currency = Currency.getInstance(request.currency());
		int digits = Math.max(currency.getDefaultFractionDigits(), 0);
		if (request.amount().stripTrailingZeros().scale() > digits) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Amounts in " + currency.getCurrencyCode() + " allow at most " + digits + " decimals");
		}
		Money amount = Money.of(request.amount(), currency);
		Money balance = lockInvestorBalance(request.userId(), currency);
		UUID accountId = accountId(AccountType.INVESTOR_EARNINGS, request.userId(), currency,
				OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));

		Optional<UUID> replay = transactionId(TransactionType.ADJUSTMENT, request.userId() + ":" + key);
		if (replay.isPresent()) {
			return account(accountId);
		}
		if (request.direction() == Direction.DEBIT && amount.isGreaterThan(balance)) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"The investor's balance is only " + balance.display());
		}
		Direction investorSide = request.direction();
		Direction platformSide = investorSide == Direction.CREDIT ? Direction.DEBIT : Direction.CREDIT;
		String reason = request.reason().strip();
		UUID transactionId = post(TransactionType.ADJUSTMENT, request.userId() + ":" + key, "Adjustment: " + reason,
				actorId, List.of(new Posting(AccountType.INVESTOR_EARNINGS, request.userId(), investorSide, amount),
						new Posting(AccountType.PLATFORM_ADJUSTMENTS, null, platformSide, amount)));
		audit.record(AuditRecord.of(actorId, AuditAction.LEDGER_ADJUSTMENT_POSTED, ENTITY, transactionId)
			.withNewValue(Map.of("userId", request.userId().toString(), "direction", investorSide,
					"amount", amount.toString(), "reason", reason)));
		return account(accountId);
	}

	/**
	 * Locks an investor's earnings account (opening it if needed) and returns its balance. Every
	 * posting that takes money out of the account (adjustments, withdrawals) holds this lock, so two
	 * of them can never both spend the same balance.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public Money lockInvestorBalance(UUID userId, Currency currency) {
		UUID accountId = accountId(AccountType.INVESTOR_EARNINGS, userId, currency,
				OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
		jdbc.queryForObject("SELECT id FROM ledger_accounts WHERE id = ? FOR UPDATE", UUID.class, accountId);
		return Money.of(balanceOf(accountId), currency);
	}

	// ------------------------------------------------------------------------------ queries

	/** An investor's earnings balances, one per currency. */
	@Transactional(readOnly = true)
	public List<LedgerAccountResponse> investorAccounts(UUID userId) {
		return jdbc.query(ACCOUNTS + " WHERE a.owner_user_id = ? ORDER BY a.currency", ACCOUNT, userId);
	}

	@Transactional(readOnly = true)
	public Page<LedgerAccountResponse> accounts(AccountType type, UUID ownerUserId, Pageable pageable) {
		StringBuilder where = new StringBuilder(" WHERE TRUE");
		List<Object> args = new ArrayList<>();
		if (type != null) {
			where.append(" AND a.account_type = ?");
			args.add(type.name());
		}
		if (ownerUserId != null) {
			where.append(" AND a.owner_user_id = ?");
			args.add(ownerUserId);
		}
		Long total = jdbc.queryForObject("SELECT count(*) FROM ledger_accounts a" + where, Long.class, args.toArray());
		List<Object> pageArgs = new ArrayList<>(args);
		pageArgs.add(pageable.getPageSize());
		pageArgs.add(pageable.getOffset());
		// Platform accounts first, then investors; stable within each group.
		List<LedgerAccountResponse> content = jdbc.query(ACCOUNTS + where
				+ " ORDER BY a.owner_user_id NULLS FIRST, a.account_type, a.currency, a.id LIMIT ? OFFSET ?", ACCOUNT,
				pageArgs.toArray());
		return new PageImpl<>(content, pageable, total == null ? 0 : total);
	}

	@Transactional(readOnly = true)
	public LedgerAccountResponse account(UUID accountId) {
		return jdbc.query(ACCOUNTS + " WHERE a.id = ?", ACCOUNT, accountId).stream()
			.findFirst()
			.orElseThrow(() -> new ResourceNotFoundException("Ledger account", accountId));
	}

	@Transactional(readOnly = true)
	public Page<LedgerEntryResponse> entries(UUID accountId, Pageable pageable) {
		LedgerAccountResponse account = account(accountId);
		Long total = jdbc.queryForObject("SELECT count(*) FROM ledger_entries WHERE account_id = ?", Long.class,
				accountId);
		List<LedgerEntryResponse> content = jdbc.query("""
				SELECT e.id, e.transaction_id, t.transaction_type, t.reference, t.description, e.direction, e.amount,
				       e.created_at
				FROM ledger_entries e JOIN ledger_transactions t ON t.id = e.transaction_id
				WHERE e.account_id = ?
				ORDER BY e.created_at DESC, e.id DESC
				LIMIT ? OFFSET ?
				""", (rs, i) -> new LedgerEntryResponse(rs.getObject("id", UUID.class),
				rs.getObject("transaction_id", UUID.class), TransactionType.valueOf(rs.getString("transaction_type")),
				rs.getString("reference"), rs.getString("description"), Direction.valueOf(rs.getString("direction")),
				MoneyResponse.from(Money.of(rs.getBigDecimal("amount"), Currency.getInstance(account.balance().currency()))),
				rs.getObject("created_at", OffsetDateTime.class).toInstant()), accountId, pageable.getPageSize(),
				pageable.getOffset());
		return new PageImpl<>(content, pageable, total == null ? 0 : total);
	}

	/**
	 * Trial balance per currency: total debits and credits over all entries. They are equal whenever
	 * the ledger is consistent.
	 */
	@Transactional(readOnly = true)
	public List<TrialBalanceLine> trialBalance() {
		return jdbc.query("""
				SELECT t.currency,
				       coalesce(sum(e.amount) FILTER (WHERE e.direction = 'DEBIT'), 0)  AS debits,
				       coalesce(sum(e.amount) FILTER (WHERE e.direction = 'CREDIT'), 0) AS credits
				FROM ledger_entries e JOIN ledger_transactions t ON t.id = e.transaction_id
				GROUP BY t.currency ORDER BY t.currency
				""", (rs, i) -> {
			Currency currency = Currency.getInstance(rs.getString("currency"));
			Money debits = Money.of(rs.getBigDecimal("debits"), currency);
			Money credits = Money.of(rs.getBigDecimal("credits"), currency);
			return new TrialBalanceLine(currency.getCurrencyCode(), MoneyResponse.from(debits),
					MoneyResponse.from(credits), debits.equals(credits));
		});
	}

	// ---------------------------------------------------------------------------- reporting

	/** The investor's earnings accounts over {@code [from, to)}, one statement per currency. */
	@Transactional(readOnly = true)
	public List<InvestorStatement> investorStatements(UUID userId, Instant from, Instant to) {
		List<InvestorStatement> statements = new ArrayList<>();
		for (LedgerAccountResponse account : investorAccounts(userId)) {
			Currency currency = Currency.getInstance(account.balance().currency());
			BigDecimal opening = signedSum(account.id(), null, from);
			List<InvestorStatement.Entry> entries = jdbc.query("""
					SELECT t.created_at, t.transaction_type, t.description,
					       CASE e.direction WHEN 'CREDIT' THEN e.amount ELSE -e.amount END AS signed
					FROM ledger_entries e JOIN ledger_transactions t ON t.id = e.transaction_id
					WHERE e.account_id = ? AND e.created_at >= ? AND e.created_at < ?
					ORDER BY e.created_at, e.id
					""", (rs, i) -> new InvestorStatement.Entry(rs.getObject("created_at", OffsetDateTime.class).toInstant(),
					TransactionType.valueOf(rs.getString("transaction_type")), rs.getString("description"),
					Money.of(rs.getBigDecimal("signed"), currency)), account.id(), utc(from), utc(to));
			BigDecimal moved = entries.stream().map(e -> e.amount().amount()).reduce(BigDecimal.ZERO, BigDecimal::add);
			statements.add(new InvestorStatement(currency.getCurrencyCode(), Money.of(opening, currency),
					Money.of(opening.add(moved), currency), entries));
		}
		return statements;
	}

	/** Totals posted in {@code [from, to)} by currency, transaction type, account type and side. */
	@Transactional(readOnly = true)
	public Map<String, List<PeriodTotal>> periodTotals(Instant from, Instant to) {
		Map<String, List<PeriodTotal>> byCurrency = new TreeMap<>();
		jdbc.query("""
				SELECT a.currency, t.transaction_type, a.account_type, e.direction, sum(e.amount) AS total
				FROM ledger_entries e
				         JOIN ledger_transactions t ON t.id = e.transaction_id
				         JOIN ledger_accounts a ON a.id = e.account_id
				WHERE e.created_at >= ? AND e.created_at < ?
				GROUP BY a.currency, t.transaction_type, a.account_type, e.direction
				""", rs -> {
			Currency currency = Currency.getInstance(rs.getString("currency"));
			byCurrency.computeIfAbsent(currency.getCurrencyCode(), c -> new ArrayList<>())
				.add(new PeriodTotal(TransactionType.valueOf(rs.getString("transaction_type")),
						AccountType.valueOf(rs.getString("account_type")), Direction.valueOf(rs.getString("direction")),
						Money.of(rs.getBigDecimal("total"), currency)));
		}, utc(from), utc(to));
		return byCurrency;
	}

	/** Balances of one account type, summed over all its accounts, as of {@code at}, per currency. */
	@Transactional(readOnly = true)
	public Map<String, Money> balancesAt(AccountType type, Instant at) {
		Map<String, Money> balances = new TreeMap<>();
		jdbc.query("""
				SELECT a.currency,
				       coalesce(sum(CASE WHEN e.direction = ? THEN e.amount ELSE -e.amount END), 0) AS balance
				FROM ledger_accounts a LEFT JOIN ledger_entries e ON e.account_id = a.id AND e.created_at < ?
				WHERE a.account_type = ?
				GROUP BY a.currency
				""", rs -> {
			Currency currency = Currency.getInstance(rs.getString("currency"));
			balances.put(currency.getCurrencyCode(), Money.of(rs.getBigDecimal("balance"), currency));
		}, type.normalBalance().name(), utc(at), type.name());
		return balances;
	}

	/** Credits minus debits on an account, for entries in {@code [from, to)} ({@code from} null: since the start). */
	private BigDecimal signedSum(UUID accountId, Instant from, Instant to) {
		return jdbc.queryForObject("""
				SELECT coalesce(sum(CASE direction WHEN 'CREDIT' THEN amount ELSE -amount END), 0)
				FROM ledger_entries WHERE account_id = ? AND created_at >= ? AND created_at < ?
				""", BigDecimal.class, accountId, from == null ? OffsetDateTime.parse("1970-01-01T00:00:00Z") : utc(from), utc(to));
	}

	private static OffsetDateTime utc(Instant instant) {
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	// ----------------------------------------------------------------------------- internal

	private UUID accountId(AccountType type, UUID owner, Currency currency, OffsetDateTime now) {
		// Concurrent first postings race to create the account: the unique indexes make one win.
		jdbc.update("""
				INSERT INTO ledger_accounts (id, account_type, owner_user_id, currency, created_at)
				VALUES (?, ?, ?, ?, ?)
				ON CONFLICT DO NOTHING
				""", UUID.randomUUID(), type.name(), owner, currency.getCurrencyCode(), now);
		return owner == null
				? jdbc.queryForObject("""
						SELECT id FROM ledger_accounts WHERE account_type = ? AND owner_user_id IS NULL AND currency = ?
						""", UUID.class, type.name(), currency.getCurrencyCode())
				: jdbc.queryForObject("""
						SELECT id FROM ledger_accounts WHERE account_type = ? AND owner_user_id = ? AND currency = ?
						""", UUID.class, type.name(), owner, currency.getCurrencyCode());
	}

	private Optional<UUID> transactionId(TransactionType type, String reference) {
		return jdbc.query("SELECT id FROM ledger_transactions WHERE transaction_type = ? AND reference = ?",
				(rs, i) -> rs.getObject("id", UUID.class), type.name(), reference).stream().findFirst();
	}

	private BigDecimal balanceOf(UUID accountId) {
		return jdbc.queryForObject("SELECT balance FROM (" + ACCOUNTS + " WHERE a.id = ?) b", BigDecimal.class,
				accountId);
	}

	private static final RowMapper<LedgerAccountResponse> ACCOUNT = LedgerService::mapAccount;

	private static LedgerAccountResponse mapAccount(ResultSet rs, int row) throws SQLException {
		AccountType type = AccountType.valueOf(rs.getString("account_type"));
		Currency currency = Currency.getInstance(rs.getString("currency"));
		return new LedgerAccountResponse(rs.getObject("id", UUID.class), type,
				rs.getObject("owner_user_id", UUID.class), type.normalBalance(),
				MoneyResponse.from(Money.of(rs.getBigDecimal("balance"), currency)),
				rs.getObject("created_at", OffsetDateTime.class).toInstant());
	}

}
