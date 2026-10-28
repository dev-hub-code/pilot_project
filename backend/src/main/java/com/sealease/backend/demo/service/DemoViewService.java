package com.sealease.backend.demo.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sealease.backend.bankaccount.service.BankAccountService;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.common.time.DisplayTimeZone;
import com.sealease.backend.container.dto.ContainerSummary;
import com.sealease.backend.container.entity.ContainerCondition;
import com.sealease.backend.container.entity.ContainerStatus;
import com.sealease.backend.demo.service.DemoBook.Kind;
import com.sealease.backend.earning.dto.EarningsSummary;
import com.sealease.backend.earning.dto.LeaseRentResponse;
import com.sealease.backend.earning.dto.LeaseRentResponse.LeaseRentStatus;
import com.sealease.backend.earning.dto.PayoutMonth;
import com.sealease.backend.investment.dto.HoldingResponse;
import com.sealease.backend.investment.dto.PortfolioResponse;
import com.sealease.backend.investment.entity.HoldingStatus;
import com.sealease.backend.ledger.service.EarningsBalance;
import com.sealease.backend.order.dto.OrderItemResponse;
import com.sealease.backend.order.dto.OrderResponse;
import com.sealease.backend.order.entity.OrderStatus;
import com.sealease.backend.referral.dto.Downline;
import com.sealease.backend.referral.dto.DownlineMember;
import com.sealease.backend.referral.dto.ReferralMonth;
import com.sealease.backend.referral.dto.ReferralOverview;
import com.sealease.backend.withdrawal.dto.WithdrawalResponse;
import com.sealease.backend.withdrawal.entity.WithdrawalOrigin;
import com.sealease.backend.withdrawal.entity.WithdrawalPayoutMethod;
import com.sealease.backend.withdrawal.entity.WithdrawalStatus;

/**
 * A demo account's own pages: the responses the investor endpoints give, filled from {@link DemoBook} instead of the
 * account's real orders, holdings, ledger and withdrawals. Ids of dummy containers, leases and payouts are derived
 * from the dummy order's id, so they stay the same from one request to the next.
 */
@Service
public class DemoViewService {

	/** The time of day dummy orders are placed and payouts made, in the business's time zone. */
	private static final LocalTime AT = LocalTime.of(10, 0);

	private final DemoAccountService demos;
	private final BankAccountService bankAccounts;
	private final DisplayTimeZone zone;

	public DemoViewService(DemoAccountService demos, BankAccountService bankAccounts, DisplayTimeZone zone) {
		this.demos = demos;
		this.bankAccounts = bankAccounts;
		this.zone = zone;
	}

	public boolean isDemo(UUID userId) {
		return demos.isDemo(userId);
	}

	@Transactional(readOnly = true)
	public PortfolioResponse portfolio(UUID userId) {
		DemoBook book = demos.book(userId);
		List<HoldingResponse> holdings = new ArrayList<>();
		Money total = Money.zero(book.currency());
		int active = 0;
		for (DemoBook.Order o : book.ownOrders()) {
			total = total.plus(o.amount());
			Money rent = o.monthlyRentPerContainer();
			Money zero = Money.zero(book.currency());
			boolean matured = !o.endsOn().isAfter(book.today());
			if (!matured) {
				active += o.containers();
			}
			for (int n = 0; n < o.containers(); n++) {
				holdings.add(new HoldingResponse(id(o, "holding", n), o.productId(), o.planCode(), o.planTitle(),
						container(o, n), o.id(),
						MoneyResponse.from(o.pricePerContainer()), o.rentPercent(), BigDecimal.ZERO, MoneyResponse.from(rent),
						MoneyResponse.from(zero), MoneyResponse.from(rent), o.tenureMonths(),
						MoneyResponse.from(rent.times(BigDecimal.valueOf(o.tenureMonths()))), o.startsOn(), o.endsOn(),
						matured ? HoldingStatus.MATURED : HoldingStatus.ACTIVE, at(o.startsOn()),
						matured ? at(o.endsOn()) : null));
			}
		}
		holdings.sort(Comparator.comparing(HoldingResponse::leaseStartsOn).reversed());
		return new PortfolioResponse(List.of(MoneyResponse.from(total)), active, holdings);
	}

	@Transactional(readOnly = true)
	public EarningsSummary summary(UUID userId) {
		DemoBook book = demos.book(userId);
		Money rent = book.balance(Kind.RENT);
		Money referral = book.balance(Kind.REFERRAL);
		Money zero = Money.zero(book.currency());
		return new EarningsSummary(List.of(MoneyResponse.from(rent.plus(referral))),
				List.of(new EarningsSummary.WalletBalance(EarningsBalance.RENT_BANK, MoneyResponse.from(rent)),
						new EarningsSummary.WalletBalance(EarningsBalance.REFERRAL, MoneyResponse.from(referral))),
				List.of(MoneyResponse.from(book.rentPaid())), List.of(MoneyResponse.from(zero)),
				List.of(MoneyResponse.from(book.commissionPaid())), leases(book), List.of(MoneyResponse.from(zero)),
				List.of(MoneyResponse.from(book.withdrawn(Kind.RENT).plus(book.withdrawn(Kind.REFERRAL)))), null);
	}

	/** Months of the range with rent credited or to come, oldest first. */
	@Transactional(readOnly = true)
	public List<PayoutMonth> monthly(UUID userId, YearMonth from, YearMonth to) {
		Money zero = Money.zero(demos.book(userId).currency());
		return demos.book(userId).months().stream()
			.filter(m -> !m.month().isBefore(from) && !m.month().isAfter(to))
			.filter(m -> m.rentPaid().isPositive() || m.rentScheduled().isPositive())
			.map(m -> new PayoutMonth(m.month().toString(), MoneyResponse.from(m.rentPaid()), MoneyResponse.from(zero),
					MoneyResponse.from(m.rentScheduled()), MoneyResponse.from(zero)))
			.toList();
	}

	@Transactional(readOnly = true)
	public Page<LeaseRentResponse> leases(UUID userId, LeaseRentStatus status, Pageable pageable) {
		List<LeaseRentResponse> leases = new ArrayList<>(leases(demos.book(userId)).stream()
			.filter(l -> status == null || l.status() == status).toList());
		leases.sort(Comparator.comparing(LeaseRentResponse::startsAt).reversed());
		return page(leases, pageable);
	}

	@Transactional(readOnly = true)
	public Page<OrderResponse> orders(UUID userId, Pageable pageable) {
		DemoBook book = demos.book(userId);
		List<OrderResponse> orders = new ArrayList<>(book.ownOrders().stream().map(o -> order(userId, o)).toList());
		orders.sort(Comparator.comparing(OrderResponse::createdAt).reversed());
		return page(orders, pageable);
	}

	@Transactional(readOnly = true)
	public OrderResponse order(UUID userId, UUID orderId) {
		return demos.book(userId).ownOrders().stream().filter(o -> o.id().equals(orderId)).findFirst()
			.map(o -> order(userId, o)).orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
	}

	/** Payouts made so far, latest first: rent on the 1st, commission on the 5th, into the primary bank account. */
	@Transactional(readOnly = true)
	public Page<WithdrawalResponse> withdrawals(UUID userId, Pageable pageable) {
		DemoBook book = demos.book(userId);
		var account = bankAccounts.listFor(userId).stream().filter(a -> a.primary()).findFirst();
		List<WithdrawalResponse> withdrawals = new ArrayList<>();
		for (DemoBook.Payout p : book.payouts()) {
			Instant paidAt = at(p.paidOn());
			String suffix = p.earnedIn().toString().replace("-", "") + (p.kind() == Kind.RENT ? "1" : "2");
			withdrawals.add(new WithdrawalResponse(uuid(userId + "|withdrawal|" + suffix), "WD-D" + suffix, userId,
					MoneyResponse.from(p.amount()), WithdrawalOrigin.SCHEDULED, WithdrawalStatus.PAID,
					WithdrawalPayoutMethod.BANK_TRANSFER,
					p.kind() == Kind.RENT ? EarningsBalance.RENT_BANK : EarningsBalance.REFERRAL,
					account.map(a -> a.accountHolderName()).orElse(null), account.map(a -> a.bankName()).orElse(null),
					account.map(a -> a.accountNumberMasked()).orElse(null), 0, 0, null, null, null, null, null, null,
					null, "DEMO-" + suffix, null, null, null, paidAt, paidAt));
		}
		withdrawals.sort(Comparator.comparing(WithdrawalResponse::createdAt).reversed());
		return page(withdrawals, pageable);
	}

	/** The dummy payouts made from {@code from} (inclusive) to {@code to} (exclusive), oldest first: for the statement. */
	@Transactional(readOnly = true)
	public List<WithdrawalResponse> withdrawalsBetween(UUID userId, Instant from, Instant to) {
		return withdrawals(userId, Pageable.unpaged()).stream()
			.filter(w -> !w.createdAt().isBefore(from) && w.createdAt().isBefore(to))
			.sorted(Comparator.comparing(WithdrawalResponse::createdAt))
			.toList();
	}

	/** The real overview (the investor's own code and referrer), with the dummy downline's levels and commission. */
	@Transactional(readOnly = true)
	public ReferralOverview referralOverview(UUID userId, ReferralOverview real) {
		DemoBook book = demos.book(userId);
		List<ReferralOverview.Level> levels = new ArrayList<>();
		for (int level = 1; level <= DemoBook.COMMISSION_LEVELS; level++) {
			BigDecimal rate = level <= book.rates().size() ? book.rates().get(level - 1) : BigDecimal.ZERO;
			levels.add(new ReferralOverview.Level(level, rate, book.membersAt(level)));
		}
		return new ReferralOverview(real.code(), real.referredBy(), true, null, levels,
				List.of(MoneyResponse.from(book.commissionPaid())), real.ratesEffectiveFrom());
	}

	@Transactional(readOnly = true)
	public Downline downline(UUID userId) {
		DemoBook book = demos.book(userId);
		return new Downline(book.members().stream().map(m -> member(book, m)).toList(), false);
	}

	@Transactional(readOnly = true)
	public DownlineMember member(UUID userId, String memberId) {
		DemoBook book = demos.book(userId);
		return book.members().stream().filter(m -> m.id().toString().equals(memberId)).findFirst()
			.map(m -> member(book, m)).orElseThrow(() -> new ResourceNotFoundException("Downline member", memberId));
	}

	/** Months with commission credited, latest first. */
	@Transactional(readOnly = true)
	public List<ReferralMonth> referralMonthly(UUID userId) {
		List<ReferralMonth> months = new ArrayList<>(demos.book(userId).months().stream()
			.filter(m -> m.commissionTotal().isPositive())
			.map(m -> new ReferralMonth(m.month().toString(), m.commission().stream().map(MoneyResponse::from).toList(),
					MoneyResponse.from(m.commissionTotal())))
			.toList());
		months.sort(Comparator.comparing(ReferralMonth::month).reversed());
		return months;
	}

	// ----------------------------------------------------------------------------- internal

	private List<LeaseRentResponse> leases(DemoBook book) {
		List<LeaseRentResponse> leases = new ArrayList<>();
		for (DemoBook.Order o : book.ownOrders()) {
			Money monthly = o.monthlyRentPerContainer();
			Money credited = DemoBook.earned(monthly, o, book.startsOn(), book.creditedUntil());
			Money zero = Money.zero(book.currency());
			boolean completed = !o.endsOn().isAfter(book.creditedUntil());
			LocalDate creditedUntil = book.creditedUntil().isBefore(o.endsOn()) ? book.creditedUntil() : o.endsOn();
			for (int n = 0; n < o.containers(); n++) {
				leases.add(new LeaseRentResponse(id(o, "holding", n), null, o.productId(), o.planCode(), o.planTitle(),
						containerNumber(o, n), o.tenureMonths(), at(o.startsOn()), at(o.endsOn()),
						creditedUntil.isAfter(o.startsOn()) ? at(creditedUntil) : at(o.startsOn()),
						MoneyResponse.from(monthly), MoneyResponse.from(monthly.times(BigDecimal.valueOf(o.tenureMonths()))),
						MoneyResponse.from(credited), MoneyResponse.from(zero), MoneyResponse.from(zero),
						completed ? LeaseRentStatus.COMPLETED : LeaseRentStatus.RUNNING, completed ? at(o.endsOn()) : null));
			}
		}
		return leases;
	}

	private OrderResponse order(UUID userId, DemoBook.Order o) {
		List<OrderItemResponse> items = new ArrayList<>();
		Money monthly = o.monthlyRentPerContainer();
		for (int n = 0; n < o.containers(); n++) {
			items.add(new OrderItemResponse(id(o, "item", n), o.productId(), o.planCode(), o.planTitle(),
					o.containerType(),
					containerNumber(o, n), MoneyResponse.from(o.pricePerContainer()), o.rentPercent(), BigDecimal.ZERO,
					MoneyResponse.from(monthly), o.tenureMonths()));
		}
		Instant placed = at(o.startsOn());
		return new OrderResponse(o.id(), o.number(), userId, OrderStatus.CONFIRMED, MoneyResponse.from(o.amount()), null,
				placed, null, null, placed, items);
	}

	private DownlineMember member(DemoBook book, DemoBook.Member m) {
		return new DownlineMember(m.id().toString(), m.parentId() == null ? null : m.parentId().toString(), m.level(),
				m.name(), at(m.joinedOn()), List.of(MoneyResponse.from(book.commissionFrom(m.id()))));
	}

	private ContainerSummary container(DemoBook.Order o, int n) {
		return new ContainerSummary(id(o, "container", n), containerNumber(o, n), o.containerType(),
				ContainerCondition.CARGO_WORTHY, ContainerStatus.ON_LEASE, null, null, null, null, null, null, null);
	}

	/** "DEMU" and seven digits, the shape of a real container number. */
	private static String containerNumber(DemoBook.Order o, int n) {
		long digits = Math.floorMod(o.id().getLeastSignificantBits(), 1_000_000L) * 10 + n % 10;
		return "DEMU" + String.format("%07d", digits % 10_000_000L);
	}

	private static UUID id(DemoBook.Order o, String what, int n) {
		return uuid(o.id() + "|" + what + "|" + n);
	}

	private static UUID uuid(String seed) {
		return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
	}

	private Instant at(LocalDate day) {
		return day.atTime(AT).atZone(zone.zone()).toInstant();
	}

	private static <T> Page<T> page(List<T> all, Pageable pageable) {
		if (pageable.isUnpaged()) {
			return new PageImpl<>(all);
		}
		int from = (int) Math.min(pageable.getOffset(), all.size());
		int to = Math.min(from + pageable.getPageSize(), all.size());
		return new PageImpl<>(all.subList(from, to), pageable, all.size());
	}

}
