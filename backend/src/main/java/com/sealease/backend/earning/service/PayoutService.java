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
import com.sealease.backend.investment.entity.ProductTerms;
import com.sealease.backend.investment.service.HoldingService;
import com.sealease.backend.kafka.KafkaTopics;
import com.sealease.backend.ledger.dto.LedgerAccountResponse;
import com.sealease.backend.ledger.service.AccountType;
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
 * Monthly payouts to investors. Each holding gets one installment per month of its tenure, due one
 * month after the previous (in arrears from the lease start): the month's rent plus price ÷ tenure
 * of capital returned (the last installment absorbs the rounding, so the whole price comes back). Due installments are credited to the investor's wallet, each in its own
 * balanced ledger transaction (idempotent on the installment id); after the last one the holding
 * matures and its container returns to inventory.
 */
@Service
public class PayoutService {

	private static final String ENTITY = "PAYOUT";
	private static final int BATCH_SIZE = 200;
	/** The longest range {@link #monthly} answers: three years. */
	static final int MAX_MONTHS = 36;

	private final PayoutInstallmentRepository installments;
	private final HoldingService holdings;
	private final LedgerService ledger;
	private final OutboxPublisher outbox;
	private final AuditService audit;
	private final ReferralEarningService referralEarnings;
	private final ApplicationEventPublisher events;
	private final TransactionTemplate transactions;
	private final Clock clock;

	public PayoutService(PayoutInstallmentRepository installments, HoldingService holdings, LedgerService ledger,
			OutboxPublisher outbox, AuditService audit, ReferralEarningService referralEarnings,
			ApplicationEventPublisher events, TransactionTemplate transactions, Clock clock) {
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
		List<PayoutInstallment> schedule = new ArrayList<>();
		for (int n = 1; n <= terms.tenureMonths(); n++) {
			schedule.add(new PayoutInstallment(terms.holdingId(), terms.userId(), terms.productId(), n,
					terms.leaseStartsOn().plusMonths(n), terms.monthlyRent(),
					ProductTerms.capitalInstallment(terms.amount(), terms.tenureMonths(), n)));
		}
		installments.saveAll(schedule);
		installments.flush();
	}

	/**
	 * Pays every installment that has fallen due, each in its own transaction so one failure does not
	 * hold back the rest. Safe to run concurrently and repeatedly.
	 *
	 * @return the number of installments paid
	 */
	public int payDue() {
		LocalDate today = today();
		int paid = 0;
		List<UUID> due;
		do {
			due = installments.findDueIds(today, PageRequest.of(0, BATCH_SIZE));
			int before = paid;
			for (UUID id : due) {
				if (Boolean.TRUE.equals(transactions.execute(status -> pay(id, today)))) {
					paid++;
				}
			}
			if (paid == before) {
				break;
			}
		}
		while (due.size() == BATCH_SIZE);
		return paid;
	}

	private boolean pay(UUID installmentId, LocalDate today) {
		PayoutInstallment installment = installments.findByIdForUpdate(installmentId).orElse(null);
		// Re-checked under the lock: another run may have paid it since it was selected.
		if (installment == null || installment.getStatus() != PayoutStatus.SCHEDULED || !installment.isDueOn(today)) {
			return false;
		}
		HoldingLabel label = holdings.labels(List.of(installment.getHoldingId())).get(installment.getHoldingId());
		List<Posting> postings = new ArrayList<>();
		if (installment.rent().isPositive()) {
			postings.add(Posting.debit(AccountType.PLATFORM_RENT_EXPENSE, null, installment.rent()));
		}
		if (installment.capital().isPositive()) {
			postings.add(Posting.debit(AccountType.PLATFORM_CAPITAL_RETURNS, null, installment.capital()));
		}
		postings.add(Posting.credit(AccountType.INVESTOR_EARNINGS, installment.getUserId(), installment.total()));
		UUID transactionId = ledger.post(TransactionType.INVESTOR_PAYOUT, installmentId.toString(),
				"Payout %d of %d · %s · container %s".formatted(installment.getInstallmentNumber(), label.tenureMonths(),
						label.productCode(), label.containerNumber()),
				null, postings);
		Instant now = clock.instant();
		installment.markPaid(transactionId, now);
		installments.flush();

		audit.record(AuditRecord.of(null, AuditAction.PAYOUT_PAID, ENTITY, installmentId)
			.withNewValue(Map.of("holdingId", installment.getHoldingId().toString(),
					"installment", installment.getInstallmentNumber(), "rent", installment.rent().toString(),
					"capital", installment.capital().toString(), "ledgerTransactionId", transactionId.toString())));
		outbox.publish(DomainEvent.of(KafkaTopics.EARNING_CREATED, "PayoutPaid", ENTITY, installmentId,
				Map.of("installmentId", installmentId.toString(), "userId", installment.getUserId().toString(),
						"holdingId", installment.getHoldingId().toString(),
						"installmentNumber", installment.getInstallmentNumber(),
						"rent", installment.rent().amount().toPlainString(),
						"capital", installment.capital().amount().toPlainString(),
						"currency", installment.rent().currency().getCurrencyCode())));
		events.publishEvent(new PayoutPaidEvent(installmentId, installment.getUserId(), installment.getProductId(),
				label.productCode(), installment.getInstallmentNumber(), label.amount()));

		if (!installments.existsByHoldingIdAndStatus(installment.getHoldingId(), PayoutStatus.SCHEDULED)) {
			holdings.mature(installment.getHoldingId(), now);
		}
		return true;
	}

	// ----------------------------------------------------------------------------- investor

	@Transactional(readOnly = true)
	public EarningsSummary summary(UUID userId) {
		List<MoneyResponse> balances = ledger.investorAccounts(userId).stream().map(LedgerAccountResponse::balance).toList();
		List<PayoutInstallment> all = installments.findByUserIdOrderByDueOnAscInstallmentNumberAsc(userId);
		Map<UUID, HoldingLabel> labels = holdings.labels(all.stream().map(PayoutInstallment::getHoldingId).distinct().toList());

		Map<String, Money> rent = new TreeMap<>();
		Map<String, Money> capital = new TreeMap<>();
		Map<UUID, List<PayoutInstallment>> byHolding = new LinkedHashMap<>();
		for (PayoutInstallment p : all) {
			byHolding.computeIfAbsent(p.getHoldingId(), id -> new ArrayList<>()).add(p);
			if (p.getStatus() == PayoutStatus.PAID) {
				rent.merge(p.rent().currency().getCurrencyCode(), p.rent(), Money::plus);
				capital.merge(p.capital().currency().getCurrencyCode(), p.capital(), Money::plus);
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
		return new EarningsSummary(balances, responses(rent), responses(capital), referralEarnings.totalEarned(userId),
				next, perHolding);
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
