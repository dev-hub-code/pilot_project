package com.sealease.backend.earning.service;

import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.earning.dto.DuePayouts;
import com.sealease.backend.earning.dto.EarningsSummary;
import com.sealease.backend.earning.dto.LeaseRentResponse;
import com.sealease.backend.earning.dto.LeaseRentResponse.LeaseRentStatus;
import com.sealease.backend.earning.dto.LeaseSearchCriteria;
import com.sealease.backend.earning.dto.PayoutMonth;
import com.sealease.backend.earning.entity.RentAccrual;
import com.sealease.backend.earning.event.RentCreditedEvent;
import com.sealease.backend.earning.repository.RentAccrualRepository;
import com.sealease.backend.investment.dto.HoldingLabel;
import com.sealease.backend.investment.dto.PayoutTerms;
import com.sealease.backend.investment.service.HoldingService;
import com.sealease.backend.ledger.dto.LedgerAccountResponse;
import com.sealease.backend.ledger.service.AccountType;
import com.sealease.backend.ledger.service.EarningsBalance;
import com.sealease.backend.ledger.service.LedgerService;
import com.sealease.backend.ledger.service.Posting;
import com.sealease.backend.ledger.service.TransactionType;
import com.sealease.backend.referral.service.ReferralEarningService;
import com.sealease.backend.withdrawal.service.WithdrawalService;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Rent to investors, credited to their wallet once per credit interval (an hour by default; admins set it, see
 * {@link RentCreditSettingsService}). Each holding has one rent accrual for its lease: the monthly rent (and, for
 * holdings sold before capital returns ended, price ÷ tenure of capital back) owed for the lease months elapsed. Each
 * run credits up to the last interval boundary that has passed, from where the lease was last credited, in one
 * balanced ledger transaction per calendar month it covers (unique per holding and starting instant), so monthly
 * statements add up exactly. Referral commission on those hours is paid with them. Once the lease end is credited the
 * holding matures and its container returns to inventory.
 */
@Service
public class PayoutService {

	private static final Logger log = LoggerFactory.getLogger(PayoutService.class);
	private static final int BATCH_SIZE = 200;
	private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);
	/** The longest range {@link #monthly} answers: three years. */
	static final int MAX_MONTHS = 36;

	private final RentAccrualRepository accruals;
	private final HoldingService holdings;
	private final LedgerService ledger;
	private final ReferralEarningService referralEarnings;
	private final ApplicationEventPublisher events;
	private final TransactionTemplate transactions;
	private final JdbcTemplate jdbc;
	private final WithdrawalService withdrawals;
	private final RentCreditSettingsService credits;
	private final Clock clock;

	public PayoutService(RentAccrualRepository accruals, HoldingService holdings, LedgerService ledger,
			ReferralEarningService referralEarnings, ApplicationEventPublisher events, TransactionTemplate transactions,
			JdbcTemplate jdbc, WithdrawalService withdrawals, RentCreditSettingsService credits, Clock clock) {
		this.accruals = accruals;
		this.holdings = holdings;
		this.ledger = ledger;
		this.referralEarnings = referralEarnings;
		this.events = events;
		this.transactions = transactions;
		this.jdbc = jdbc;
		this.withdrawals = withdrawals;
		this.credits = credits;
		this.clock = clock;
	}

	/**
	 * When a lease taken at {@code now} starts: the next full hour (or now, if exactly on one). With a credit interval
	 * shorter than an hour, the next step of it instead. Leases start on the hour whatever the interval, so changing it
	 * never delays a lease; the rent from the start is credited at the next boundary of the interval in force.
	 */
	public Instant leaseStartAfter(Instant now) {
		CreditInterval interval = credits.current();
		return (interval.minutes() < 60 ? interval : CreditInterval.ofMinutes(60)).nextStepFrom(now);
	}

	/** How often rent is credited now, e.g. "every hour". */
	public CreditInterval creditInterval() {
		return credits.current();
	}

	/** Called when a holding's lease starts (its container is assigned), in that transaction. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void schedule(PayoutTerms terms) {
		accruals.saveAndFlush(new RentAccrual(terms.holdingId(), terms.userId(), terms.productId(), terms.leaseStartsAt(),
				terms.tenureMonths(), terms.monthlyRent(), terms.capitalReturned()));
	}

	/**
	 * Credits every whole hour of rent not yet credited (every hour that has passed), each lease in its own
	 * transaction so one failure does not hold back the rest. Safe to run concurrently and repeatedly: an hour is
	 * credited once (the ledger transaction is unique per holding and starting hour).
	 *
	 * @return the number of credit steps (hours in production) credited, over all leases
	 */
	public int creditDue() {
		Instant now = clock.instant();
		CreditInterval interval = credits.current();
		Instant step = interval.stepStart(now);
		int credited = 0;
		List<UUID> due;
		do {
			due = accruals.findUncreditedIds(step, PageRequest.of(0, BATCH_SIZE));
			int before = credited;
			for (UUID id : due) {
				try {
					Integer hours = transactions.execute(status -> credit(id, now, interval));
					credited += hours == null ? 0 : hours;
				}
				catch (RuntimeException e) {
					// Rolled back on its own; the next run tries it again.
					log.error("Could not credit rent of lease {}", id, e);
				}
			}
			if (credited == before) {
				break;
			}
		}
		while (due.size() == BATCH_SIZE);
		return credited;
	}

	/** Credits the lease's hours that have passed by {@code now}: one credit per calendar month they fall in. */
	private int credit(UUID accrualId, Instant now, CreditInterval interval) {
		RentAccrual accrual = accruals.findByIdForUpdate(accrualId).orElse(null);
		// Re-checked under the lock: another run may have credited it since it was selected.
		if (accrual == null || accrual.isCompleted()) {
			return 0;
		}
		Instant until = accrual.creditableUntil(interval.stepStart(now));
		if (!until.isAfter(accrual.getCreditedUntil())) {
			return 0;
		}
		Instant before = accrual.getCreditedUntil();
		HoldingLabel label = holdings.labels(List.of(accrual.getHoldingId())).get(accrual.getHoldingId());
		// Rent on a container paid in cash at the office is collected in cash there; otherwise it is paid by bank.
		EarningsBalance balance = label.paidAtOffice() ? EarningsBalance.RENT_CASH : EarningsBalance.RENT_BANK;
		while (accrual.getCreditedUntil().isBefore(until)) {
			Instant from = accrual.getCreditedUntil();
			Instant to = min(until, firstOfNextMonth(from));
			creditHours(accrual, label, balance, from, to, now);
		}
		accruals.flush();
		if (accrual.isCompleted()) {
			holdings.mature(accrual.getHoldingId(), now);
		}
		return (int) (Duration.between(before, accrual.getCreditedUntil()).toSeconds() / interval.length().toSeconds());
	}

	/** Credits the hours from {@code from} to {@code to} in one ledger transaction, and the commission on them. */
	private void creditHours(RentAccrual accrual, HoldingLabel label, EarningsBalance balance, Instant from, Instant to,
			Instant now) {
		Money[] amounts = accrual.amountsUpTo(to);
		Money rent = amounts[0];
		Money capital = amounts[1];
		Money total = rent.plus(capital);
		UUID transactionId = null;
		if (total.isPositive()) {
			List<Posting> postings = new ArrayList<>();
			if (rent.isPositive()) {
				postings.add(Posting.debit(AccountType.PLATFORM_RENT_EXPENSE, null, rent));
			}
			if (capital.isPositive()) {
				postings.add(Posting.debit(AccountType.PLATFORM_CAPITAL_RETURNS, null, capital));
			}
			postings.add(Posting.credit(balance.account(), accrual.getUserId(), total));
			transactionId = ledger.post(TransactionType.INVESTOR_PAYOUT, accrual.getHoldingId() + ":" + from,
					"Rent %s to %s · %s · container %s".formatted(HOUR.format(from), HOUR.format(to), label.productCode(),
							label.containerNumber()),
					null, postings);
		}
		accrual.creditUpTo(to, rent, capital, now);
		if (transactionId == null) {
			return;
		}
		UUID creditId = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO rent_credits (id, holding_id, user_id, day, from_at, to_at, rent_amount, capital_amount,
				                          currency, ledger_transaction_id, created_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", creditId, accrual.getHoldingId(), accrual.getUserId(), LocalDate.ofInstant(from, ZoneOffset.UTC),
				Timestamp.from(from), Timestamp.from(to), rent.amount(), capital.amount(),
				total.currency().getCurrencyCode(), transactionId, Timestamp.from(now));
		events.publishEvent(new RentCreditedEvent(creditId, accrual.getHoldingId(), accrual.getUserId(),
				accrual.getProductId(), label.productCode(), label.amount(), accrual.monthsAt(from), accrual.monthsAt(to),
				from, to));
	}

	// ----------------------------------------------------------------------------- investor

	@Transactional(readOnly = true)
	public EarningsSummary summary(UUID userId) {
		// One account per earnings balance and currency; the wallet total adds them up per currency.
		List<LedgerAccountResponse> accounts = ledger.investorAccounts(userId);
		List<EarningsSummary.WalletBalance> wallet = accounts.stream()
			.map(a -> new EarningsSummary.WalletBalance(EarningsBalance.of(a.accountType()), a.balance()))
			.sorted(Comparator.comparing(EarningsSummary.WalletBalance::balance))
			.toList();
		Map<String, Money> totals = new TreeMap<>();
		accounts.forEach(a -> totals.merge(a.balance().currency(), Money.of(a.balance().amount(), a.balance().currency()), Money::plus));
		List<MoneyResponse> balances = totals.values().stream().map(MoneyResponse::from).toList();

		List<RentAccrual> leases = accruals.findByUserIdOrderByStartsAtAsc(userId);
		Map<UUID, HoldingLabel> labels = holdings.labels(leases.stream().map(RentAccrual::getHoldingId).toList());
		Map<String, Money> rent = new TreeMap<>();
		Map<String, Money> capital = new TreeMap<>();
		for (RentAccrual a : leases) {
			if (a.creditedRent().isPositive()) {
				rent.merge(a.currency().getCurrencyCode(), a.creditedRent(), Money::plus);
			}
			// Only holdings sold before capital returns ended have any.
			if (a.creditedCapital().isPositive()) {
				capital.merge(a.currency().getCurrencyCode(), a.creditedCapital(), Money::plus);
			}
		}
		// Steps are credited once they end: the earliest rent not credited yet goes at the next boundary after it.
		Instant nextCreditAt = leases.stream().filter(a -> !a.isCompleted()).map(RentAccrual::getCreditedUntil)
			.min(Comparator.naturalOrder()).map(credits.current()::nextBoundaryAfter).orElse(null);
		WithdrawalService.InvestorTotals withdrawn = withdrawals.totals(userId);
		return new EarningsSummary(balances, wallet, responses(rent), responses(capital), referralEarnings.totalEarned(userId),
				leases.stream().map(a -> LeaseRentResponse.from(a, labels.get(a.getHoldingId()), false)).toList(),
				withdrawn.inProgress().stream().map(MoneyResponse::from).toList(),
				withdrawn.withdrawn().stream().map(MoneyResponse::from).toList(), nextCreditAt);
	}

	/**
	 * The investor's rent per calendar month (UTC) from {@code from} to {@code to} (both included), oldest first:
	 * credited so far, and still to come from leases running that month. Months without rent are left out.
	 */
	@Transactional(readOnly = true)
	public List<PayoutMonth> monthly(UUID userId, YearMonth from, YearMonth to) {
		if (to.isBefore(from) || from.plusMonths(MAX_MONTHS - 1).isBefore(to)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Choose a range of 1 to " + MAX_MONTHS + " months, ending after it starts");
		}
		Instant rangeStart = from.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
		Instant rangeEnd = to.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
		// month|currency -> rent credited, capital credited, rent to come, capital to come
		Map<String, Money[]> byMonth = new TreeMap<>();
		// Credits never span two months.
		jdbc.query("""
				SELECT to_char(from_at AT TIME ZONE 'UTC', 'YYYY-MM') AS month, currency, sum(rent_amount) AS rent,
				       sum(capital_amount) AS capital
				FROM rent_credits WHERE user_id = ? AND from_at >= ? AND from_at < ?
				GROUP BY 1, 2
				""", rs -> {
			Currency currency = Currency.getInstance(rs.getString("currency"));
			Money[] sums = sums(byMonth, rs.getString("month"), currency);
			sums[0] = sums[0].plus(Money.of(rs.getBigDecimal("rent"), currency));
			sums[1] = sums[1].plus(Money.of(rs.getBigDecimal("capital"), currency));
		}, userId, Timestamp.from(rangeStart), Timestamp.from(rangeEnd));
		// Still to come: what each running lease owes over the month's hours not credited yet.
		for (RentAccrual a : accruals.findOverlapping(userId, rangeStart, rangeEnd)) {
			if (a.isCompleted()) {
				continue;
			}
			for (YearMonth month = from; !month.isAfter(to); month = month.plusMonths(1)) {
				Instant monthStart = month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
				Instant monthEnd = month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
				Instant start = max(monthStart, a.getCreditedUntil());
				Instant end = min(monthEnd, a.getEndsAt());
				if (!end.isAfter(start)) {
					continue;
				}
				Money rentToCome = a.rentUpTo(end).minus(a.rentUpTo(start));
				Money capitalToCome = a.capitalUpTo(end).minus(a.capitalUpTo(start));
				Money[] sums = sums(byMonth, month.toString(), a.currency());
				sums[2] = sums[2].plus(rentToCome);
				sums[3] = sums[3].plus(capitalToCome);
			}
		}
		List<PayoutMonth> months = new ArrayList<>();
		byMonth.forEach((key, sums) -> months.add(new PayoutMonth(key.substring(0, key.indexOf('|')),
				MoneyResponse.from(sums[0]), MoneyResponse.from(sums[1]), MoneyResponse.from(sums[2]),
				MoneyResponse.from(sums[3]))));
		return months;
	}

	private static Money[] sums(Map<String, Money[]> byMonth, String month, Currency currency) {
		return byMonth.computeIfAbsent(month + "|" + currency.getCurrencyCode(), k -> {
			Money[] zeros = new Money[4];
			Arrays.fill(zeros, Money.zero(currency));
			return zeros;
		});
	}

	/** The investor's leases and the rent of each, latest lease first. */
	@Transactional(readOnly = true)
	public Page<LeaseRentResponse> mine(UUID userId, LeaseRentStatus status, Pageable pageable) {
		return search(new LeaseSearchCriteria(status, userId, null), pageable, false);
	}

	// -------------------------------------------------------------------------------- staff

	@Transactional(readOnly = true)
	public Page<LeaseRentResponse> search(LeaseSearchCriteria criteria, Pageable pageable) {
		return search(criteria, pageable, true);
	}

	/** Leases with hours that have ended and are not credited yet, per currency, and what those hours come to. */
	@Transactional(readOnly = true)
	public List<DuePayouts> due() {
		Instant now = clock.instant();
		Map<String, long[]> counts = new TreeMap<>();
		Map<String, Money> owed = new TreeMap<>();
		Instant step = credits.current().stepStart(now);
		for (RentAccrual a : accruals.findBehind(step)) {
			Money[] amounts = a.amountsUpTo(a.creditableUntil(step));
			String currency = a.currency().getCurrencyCode();
			counts.computeIfAbsent(currency, c -> new long[1])[0]++;
			owed.merge(currency, amounts[0].plus(amounts[1]), Money::plus);
		}
		return counts.entrySet().stream()
			.map(e -> new DuePayouts(e.getValue()[0], MoneyResponse.from(owed.get(e.getKey()))))
			.toList();
	}

	private Page<LeaseRentResponse> search(LeaseSearchCriteria criteria, Pageable pageable, boolean staff) {
		Page<RentAccrual> page = accruals.findAll(matching(criteria), pageable);
		Map<UUID, HoldingLabel> labels = holdings.labels(page.map(RentAccrual::getHoldingId).toSet());
		return page.map(a -> LeaseRentResponse.from(a, labels.get(a.getHoldingId()), staff));
	}

	private static List<MoneyResponse> responses(Map<String, Money> totals) {
		return totals.values().stream().map(MoneyResponse::from).collect(Collectors.toList());
	}

	private static Specification<RentAccrual> matching(LeaseSearchCriteria c) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (c.status() == LeaseRentStatus.RUNNING) {
				predicates.add(cb.isNull(root.get("completedAt")));
			}
			if (c.status() == LeaseRentStatus.COMPLETED) {
				predicates.add(cb.isNotNull(root.get("completedAt")));
			}
			if (c.userId() != null) {
				predicates.add(cb.equal(root.get("userId"), c.userId()));
			}
			if (c.holdingId() != null) {
				predicates.add(cb.equal(root.get("holdingId"), c.holdingId()));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

	/** 00:00 UTC on the 1st of the month after the one {@code instant} falls in. */
	static Instant firstOfNextMonth(Instant instant) {
		return YearMonth.from(instant.atZone(ZoneOffset.UTC)).plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC)
			.toInstant();
	}

	private static Instant min(Instant a, Instant b) {
		return a.isBefore(b) ? a : b;
	}

	private static Instant max(Instant a, Instant b) {
		return a.isAfter(b) ? a : b;
	}

}
