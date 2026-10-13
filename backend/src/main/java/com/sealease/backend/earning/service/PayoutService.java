package com.sealease.backend.earning.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.earning.dto.DuePayouts;
import com.sealease.backend.earning.dto.EarningsSummary;
import com.sealease.backend.earning.dto.PayoutMonth;
import com.sealease.backend.earning.dto.PayoutResponse;
import com.sealease.backend.earning.dto.PayoutSearchCriteria;
import com.sealease.backend.earning.entity.PayoutInstallment;
import com.sealease.backend.earning.entity.PayoutStatus;
import com.sealease.backend.earning.event.PayoutPaidEvent;
import com.sealease.backend.earning.repository.PayoutInstallmentRepository;
import com.sealease.backend.investment.dto.HoldingLabel;
import com.sealease.backend.investment.dto.PayoutTerms;
import com.sealease.backend.investment.service.HoldingService;
import com.sealease.backend.kafka.KafkaTopics;
import com.sealease.backend.ledger.dto.LedgerAccountResponse;
import com.sealease.backend.ledger.service.AccountType;
import com.sealease.backend.ledger.service.EarningsBalance;
import com.sealease.backend.ledger.service.LedgerService;
import com.sealease.backend.ledger.service.Posting;
import com.sealease.backend.ledger.service.TransactionType;
import com.sealease.backend.outbox.DomainEvent;
import com.sealease.backend.outbox.OutboxPublisher;
import com.sealease.backend.referral.service.ReferralEarningService;
import jakarta.persistence.criteria.Predicate;
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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Rent to investors, credited to their wallet every day. Each holding gets one installment per calendar month of its
 * lease (a part month at each end): the month's rent. Holdings sold before capital returns ended also get price ÷
 * tenure of capital back. Each whole day of an installment is credited the next day, in its own balanced ledger
 * transaction (unique per installment and day): the month's amount ÷ its days. Once a month is fully credited it is
 * PAID and referral commission on it is paid; after the last one the holding matures and its container returns to
 * inventory.
 */
@Service
public class PayoutService {

	private static final String ENTITY = "PAYOUT";
	private static final int BATCH_SIZE = 200;
	/** Payout periods start on the 1st: each is a calendar month. */
	private static final int CALENDAR_MONTHS = 1;
	/** The longest range {@link #monthly} answers: three years. */
	static final int MAX_MONTHS = 36;

	private final PayoutInstallmentRepository installments;
	private final HoldingService holdings;
	private final LedgerService ledger;
	private final OutboxPublisher outbox;
	private final AuditService audit;
	private final ReferralEarningService referralEarnings;
	private final PayoutSettingsService settings;
	private final ApplicationEventPublisher events;
	private final TransactionTemplate transactions;
	private final JdbcTemplate jdbc;
	private final Clock clock;

	public PayoutService(PayoutInstallmentRepository installments, HoldingService holdings, LedgerService ledger,
			OutboxPublisher outbox, AuditService audit, ReferralEarningService referralEarnings,
			PayoutSettingsService settings, ApplicationEventPublisher events, TransactionTemplate transactions,
			JdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.settings = settings;
		this.installments = installments;
		this.holdings = holdings;
		this.ledger = ledger;
		this.outbox = outbox;
		this.audit = audit;
		this.referralEarnings = referralEarnings;
		this.events = events;
		this.transactions = transactions;
		this.clock = clock;
	}

	/** Called by order confirmation for each new holding, in its transaction. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void schedule(PayoutTerms terms) {
		// Periods are calendar months (a part month at each end), so a day's rent is the month's rent ÷ its days.
		List<PayoutSchedule.Entry> plan = PayoutSchedule.plan(terms.leaseStartsOn(), terms.tenureMonths(),
				CALENDAR_MONTHS, terms.monthlyRent(), terms.capitalReturned());
		List<PayoutInstallment> schedule = plan.stream()
			.map(e -> new PayoutInstallment(terms.holdingId(), terms.userId(), terms.productId(), e.number(), plan.size(),
					// Fully credited when the period ends (each day is credited the day after).
				e.periodStart(), e.periodEnd(), e.monthShare(), e.periodEnd(), e.rent(), e.capital()))
			.toList();
		installments.saveAll(schedule);
		installments.flush();
	}

	/**
	 * Credits every whole day of rent not yet credited (every day before today), each installment in its own
	 * transaction so one failure does not hold back the rest. Safe to run concurrently and repeatedly: a day is
	 * credited once (the ledger transaction is unique per installment and day).
	 *
	 * @return the number of days credited, over all installments
	 */
	public int creditDue() {
		LocalDate today = today();
		int credited = 0;
		List<UUID> due;
		do {
			due = installments.findUncreditedIds(today, PageRequest.of(0, BATCH_SIZE));
			int before = credited;
			for (UUID id : due) {
				Integer days = transactions.execute(status -> credit(id, today));
				credited += days == null ? 0 : days;
			}
			if (credited == before) {
				break;
			}
		}
		while (due.size() == BATCH_SIZE);
		return credited;
	}

	/** Credits the installment's uncredited days before {@code today}, one ledger transaction per day. */
	private int credit(UUID installmentId, LocalDate today) {
		PayoutInstallment installment = installments.findByIdForUpdate(installmentId).orElse(null);
		// Re-checked under the lock: another run may have credited it since it was selected.
		if (installment == null || installment.getStatus() != PayoutStatus.SCHEDULED
				|| !installment.getCreditedThrough().isBefore(today)) {
			return 0;
		}
		HoldingLabel label = holdings.labels(List.of(installment.getHoldingId())).get(installment.getHoldingId());
		// Rent on a container paid in cash at the office is collected in cash there; otherwise it is paid by bank.
		EarningsBalance balance = label.paidAtOffice() ? EarningsBalance.RENT_CASH : EarningsBalance.RENT_BANK;
		Instant now = clock.instant();
		int days = 0;
		UUID transactionId = null;
		while (installment.getStatus() == PayoutStatus.SCHEDULED && installment.getCreditedThrough().isBefore(today)) {
			LocalDate day = installment.getCreditedThrough();
			Money[] amounts = installment.nextDay();
			Money rent = amounts[0];
			Money capital = amounts[1];
			List<Posting> postings = new ArrayList<>();
			if (rent.isPositive()) {
				postings.add(Posting.debit(AccountType.PLATFORM_RENT_EXPENSE, null, rent));
			}
			if (capital.isPositive()) {
				postings.add(Posting.debit(AccountType.PLATFORM_CAPITAL_RETURNS, null, capital));
			}
			Money total = rent.plus(capital);
			if (total.isPositive()) {
				postings.add(Posting.credit(balance.account(), installment.getUserId(), total));
				transactionId = ledger.post(TransactionType.INVESTOR_PAYOUT, installmentId + ":" + day,
						"Rent for %s · %s · container %s".formatted(day, label.productCode(), label.containerNumber()),
						null, postings);
				jdbc.update("""
						INSERT INTO rent_credits (id, installment_id, holding_id, user_id, day, rent_amount, capital_amount,
						                          currency, ledger_transaction_id, created_at)
						VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
						""", UUID.randomUUID(), installmentId, installment.getHoldingId(), installment.getUserId(), day,
						rent.amount(), capital.amount(), total.currency().getCurrencyCode(), transactionId,
						java.sql.Timestamp.from(now));
			}
			installment.creditDay(rent, capital, transactionId, now);
			days++;
		}
		installments.flush();
		if (installment.getStatus() == PayoutStatus.PAID) {
			completed(installment, label, now);
		}
		return days;
	}

	/** A period fully credited: recorded, announced, and referral commission on it paid; the last one matures the holding. */
	private void completed(PayoutInstallment installment, HoldingLabel label, Instant now) {
		UUID installmentId = installment.getId();
		audit.record(AuditRecord.of(null, AuditAction.PAYOUT_PAID, ENTITY, installmentId)
			.withNewValue(Map.of("holdingId", installment.getHoldingId().toString(),
					"installment", installment.getInstallmentNumber(), "rent", installment.rent().toString(),
					"capital", installment.capital().toString(), "days", installment.days())));
		outbox.publish(DomainEvent.of(KafkaTopics.EARNING_CREATED, "PayoutPaid", ENTITY, installmentId,
				Map.of("installmentId", installmentId.toString(), "userId", installment.getUserId().toString(),
						"holdingId", installment.getHoldingId().toString(),
						"installmentNumber", installment.getInstallmentNumber(),
						"rent", installment.rent().amount().toPlainString(),
						"capital", installment.capital().amount().toPlainString(),
						"currency", installment.rent().currency().getCurrencyCode())));
		events.publishEvent(new PayoutPaidEvent(installmentId, installment.getUserId(), installment.getProductId(),
				label.productCode(), installment.getInstallmentNumber(), label.amount(), installment.getMonthShare()));

		if (!installments.existsByHoldingIdAndStatus(installment.getHoldingId(), PayoutStatus.SCHEDULED)) {
			holdings.mature(installment.getHoldingId(), now);
		}
	}

	// ----------------------------------------------------------------------------- investor

	@Transactional(readOnly = true)
	public EarningsSummary summary(UUID userId) {
		// One account per earnings balance and currency; the wallet total adds them up per currency.
		List<LedgerAccountResponse> accounts = ledger.investorAccounts(userId);
		List<EarningsSummary.WalletBalance> wallet = accounts.stream()
			.map(a -> new EarningsSummary.WalletBalance(EarningsBalance.of(a.accountType()), a.balance()))
			.sorted(java.util.Comparator.comparing(EarningsSummary.WalletBalance::balance))
			.toList();
		Map<String, Money> totals = new TreeMap<>();
		accounts.forEach(a -> totals.merge(a.balance().currency(), Money.of(a.balance().amount(), a.balance().currency()), Money::plus));
		List<MoneyResponse> balances = totals.values().stream().map(MoneyResponse::from).toList();
		List<PayoutInstallment> all = installments.findByUserIdOrderByDueOnAscInstallmentNumberAsc(userId);
		Map<UUID, HoldingLabel> labels = holdings.labels(all.stream().map(PayoutInstallment::getHoldingId).distinct().toList());

		Map<String, Money> rent = new TreeMap<>();
		Map<String, Money> capital = new TreeMap<>();
		Map<UUID, List<PayoutInstallment>> byHolding = new LinkedHashMap<>();
		for (PayoutInstallment p : all) {
			byHolding.computeIfAbsent(p.getHoldingId(), id -> new ArrayList<>()).add(p);
			if (p.getStatus() == PayoutStatus.PAID) {
				rent.merge(p.rent().currency().getCurrencyCode(), p.rent(), Money::plus);
				// Only holdings sold before capital returns ended have any.
				if (p.capital().isPositive()) {
					capital.merge(p.capital().currency().getCurrencyCode(), p.capital(), Money::plus);
				}
			}
		}

		EarningsSummary.NextPayout next = null;
		List<PayoutInstallment> upcoming = all.stream().filter(p -> p.getStatus() == PayoutStatus.SCHEDULED).toList();
		if (!upcoming.isEmpty()) {
			LocalDate day = upcoming.getFirst().getDueOn();
			Money total = upcoming.stream().filter(p -> p.getDueOn().equals(day)).map(PayoutInstallment::total)
				.reduce(Money::plus).orElseThrow();
			next = new EarningsSummary.NextPayout(day, MoneyResponse.from(total));
		}

		List<EarningsSummary.HoldingPayouts> perHolding = new ArrayList<>();
		byHolding.forEach((holdingId, schedule) -> {
			List<PayoutInstallment> paid = schedule.stream().filter(p -> p.getStatus() == PayoutStatus.PAID).toList();
			PayoutInstallment following = schedule.stream().filter(p -> p.getStatus() == PayoutStatus.SCHEDULED)
				.findFirst().orElse(null);
			Currency currency = schedule.getFirst().total().currency();
			Money received = paid.stream().map(PayoutInstallment::total).reduce(Money.zero(currency), Money::plus);
			Instant lastPaidAt = paid.stream().map(PayoutInstallment::getPaidAt).max(Comparator.naturalOrder()).orElse(null);
			HoldingLabel label = labels.get(holdingId);
			perHolding.add(new EarningsSummary.HoldingPayouts(holdingId, schedule.getFirst().getProductId(),
					label == null ? null : label.containerNumber(), paid.size(), schedule.size(),
					MoneyResponse.from(received), following == null ? null : following.getDueOn(),
					following == null ? null : MoneyResponse.from(following.total()), lastPaidAt));
		});
		return new EarningsSummary(balances, wallet, responses(rent), responses(capital), referralEarnings.totalEarned(userId),
				next, perHolding, settings.payoutDay());
	}

	/** The investor's payouts, paid and scheduled. */
	/**
	 * The investor's payouts per calendar month from {@code from} to {@code to} (both included), by due date,
	 * oldest first; months without payouts are left out.
	 */
	@Transactional(readOnly = true)
	public List<PayoutMonth> monthly(UUID userId, YearMonth from, YearMonth to) {
		if (to.isBefore(from) || from.plusMonths(MAX_MONTHS - 1).isBefore(to)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Choose a range of 1 to " + MAX_MONTHS + " months, ending after it starts");
		}
		// month|currency -> rent paid, capital paid, rent scheduled, capital scheduled
		Map<String, Money[]> byMonth = new TreeMap<>();
		for (PayoutInstallment p : installments.findByUserIdAndDueOnBetweenOrderByDueOn(userId, from.atDay(1),
				to.atEndOfMonth())) {
			Currency currency = p.total().currency();
			Money[] sums = byMonth.computeIfAbsent(YearMonth.from(p.getDueOn()) + "|" + currency.getCurrencyCode(), k -> {
				Money[] zeros = new Money[4];
				Arrays.fill(zeros, Money.zero(currency));
				return zeros;
			});
			int offset = p.getStatus() == PayoutStatus.PAID ? 0 : 2;
			sums[offset] = sums[offset].plus(p.rent());
			sums[offset + 1] = sums[offset + 1].plus(p.capital());
		}
		List<PayoutMonth> months = new ArrayList<>();
		byMonth.forEach((key, sums) -> months.add(new PayoutMonth(key.substring(0, key.indexOf('|')),
				MoneyResponse.from(sums[0]), MoneyResponse.from(sums[1]), MoneyResponse.from(sums[2]),
				MoneyResponse.from(sums[3]))));
		return months;
	}

	@Transactional(readOnly = true)
	public Page<PayoutResponse> mine(UUID userId, PayoutStatus status, Pageable pageable) {
		return search(new PayoutSearchCriteria(status, userId, null, null), pageable, false);
	}

	// -------------------------------------------------------------------------------- staff

	@Transactional(readOnly = true)
	public Page<PayoutResponse> search(PayoutSearchCriteria criteria, Pageable pageable) {
		return search(criteria, pageable, true);
	}

	/** Payouts that have fallen due and are not paid yet, per currency. */
	@Transactional(readOnly = true)
	public List<DuePayouts> due() {
		return installments.dueTotals(today()).stream()
			.map(row -> new DuePayouts((Long) row[1], MoneyResponse.from(
					Money.of((BigDecimal) row[2], Currency.getInstance((String) row[0])))))
			.toList();
	}

	private Page<PayoutResponse> search(PayoutSearchCriteria criteria, Pageable pageable, boolean staff) {
		Page<PayoutInstallment> page = installments.findAll(matching(criteria), pageable);
		Map<UUID, HoldingLabel> labels = holdings.labels(page.map(PayoutInstallment::getHoldingId).toSet());
		return page.map(p -> PayoutResponse.from(p, labels.get(p.getHoldingId()), staff));
	}

	private LocalDate today() {
		return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
	}

	private static List<MoneyResponse> responses(Map<String, Money> totals) {
		return totals.values().stream().map(MoneyResponse::from).collect(Collectors.toList());
	}

	private static Specification<PayoutInstallment> matching(PayoutSearchCriteria c) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (c.status() != null) {
				predicates.add(cb.equal(root.get("status"), c.status()));
			}
			if (c.userId() != null) {
				predicates.add(cb.equal(root.get("userId"), c.userId()));
			}
			if (c.holdingId() != null) {
				predicates.add(cb.equal(root.get("holdingId"), c.holdingId()));
			}
			if (c.dueBy() != null) {
				predicates.add(cb.lessThanOrEqualTo(root.get("dueOn"), c.dueBy()));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

}
