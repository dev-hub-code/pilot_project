package com.sealease.backend.reporting.service;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyProperties;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.crm.dto.PipelineStage;
import com.sealease.backend.crm.entity.LeadStage;
import com.sealease.backend.crm.service.LeadService;
import com.sealease.backend.helpdesk.service.SupportService;
import com.sealease.backend.investment.service.HoldingService;
import com.sealease.backend.ledger.dto.PeriodTotal;
import com.sealease.backend.ledger.service.AccountType;
import com.sealease.backend.ledger.service.Direction;
import com.sealease.backend.ledger.service.EarningsBalance;
import com.sealease.backend.ledger.service.LedgerService;
import com.sealease.backend.ledger.service.TransactionType;
import com.sealease.backend.reporting.dto.DashboardCharts;
import com.sealease.backend.reporting.dto.KpiTile;
import com.sealease.backend.role.service.UserRoleService;
import com.sealease.backend.security.AuthenticatedUser;
import com.sealease.backend.withdrawal.service.WithdrawalService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Currency;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Key figures for the admin overview. Each tile is computed only for viewers holding the
 * permission that guards its underlying data, so the overview never reveals more than the API would.
 */
@Service
public class DashboardService {

	private static final int CHART_MONTHS = 12;
	private static final List<AccountType> PAYOUT_ACCOUNTS = List.of(AccountType.PLATFORM_RENT_EXPENSE,
			AccountType.PLATFORM_CAPITAL_RETURNS, AccountType.PLATFORM_REFERRAL_EXPENSE);

	private final UserRoleService roles;
	private final HoldingService holdings;
	private final LedgerService ledger;
	private final WithdrawalService withdrawals;
	private final SupportService support;
	private final LeadService leads;
	private final MoneyProperties moneyProperties;
	private final Clock clock;

	public DashboardService(UserRoleService roles, HoldingService holdings, LedgerService ledger,
			WithdrawalService withdrawals, SupportService support, LeadService leads, MoneyProperties moneyProperties,
			Clock clock) {
		this.roles = roles;
		this.holdings = holdings;
		this.ledger = ledger;
		this.withdrawals = withdrawals;
		this.support = support;
		this.leads = leads;
		this.moneyProperties = moneyProperties;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<KpiTile> tiles(AuthenticatedUser viewer) {
		List<KpiTile> tiles = new ArrayList<>();
		if (viewer.hasPermission("USER_VIEW")) {
			tiles.add(new KpiTile("investors", "Investors", List.of(String.valueOf(roles.countUsersWithRole("INVESTOR"))),
					"Registered investor accounts", "/admin/users"));
		}
		if (viewer.hasPermission("INVESTMENT_VIEW")) {
			tiles.add(new KpiTile("capital", "Capital invested", money(holdings.totalInvested().values()),
					"Containers bought by investors", "/admin/products"));
		}
		if (viewer.hasPermission("FINANCE_VIEW")) {
			LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
			ReportService.Period month = new ReportService.Period(today.withDayOfMonth(1), today);
			Map<String, Money> paid = new TreeMap<>();
			ledger.periodTotals(month.start(), month.end()).forEach((currency, totals) -> totals.stream()
				.filter(t -> t.transactionType() == TransactionType.INVESTOR_PAYOUT
						&& EarningsBalance.ACCOUNT_TYPES.contains(t.accountType()) && t.direction() == Direction.CREDIT)
				.map(PeriodTotal::amount)
				.forEach(m -> paid.merge(currency, m, Money::plus)));
			tiles.add(new KpiTile("payouts", "Paid to investors this month", money(paid.values()),
					"Monthly rent plus capital returned", "/admin/payouts?status=PAID"));
			tiles.add(new KpiTile("owed", "Owed to investors",
					money(ledger.investorBalancesAt(clock.instant()).values()),
					"Earnings balances not yet withdrawn", "/admin/ledger"));
		}
		if (viewer.hasPermission("WITHDRAWAL_VIEW")) {
			tiles.add(new KpiTile("withdrawals", "Withdrawals in progress", money(withdrawals.openAmounts().values()),
					withdrawals.openCount() + " awaiting approval or payout", "/admin/withdrawals"));
		}
		if (viewer.hasPermission("SUPPORT_TICKET_VIEW")) {
			SupportService.QueueCounts queue = support.queueCounts();
			tiles.add(new KpiTile("support", "Support requests waiting", List.of(String.valueOf(queue.open())),
					queue.overdue() + " overdue", "/admin/support"));
		}
		if (viewer.hasPermission("LEAD_VIEW")) {
			List<PipelineStage> pipeline = leads.pipeline(new LeadService.Viewer(viewer.userId(),
					viewer.hasPermission("LEAD_ASSIGN")), null);
			long open = pipeline.stream().filter(p -> p.stage().isOpen()).mapToLong(PipelineStage::leads).sum();
			long fresh = pipeline.stream().filter(p -> p.stage() == LeadStage.NEW).mapToLong(PipelineStage::leads).sum();
			tiles.add(new KpiTile("leads", "Open leads", List.of(String.valueOf(open)), fresh + " new", "/admin/leads"));
		}
		return tiles;
	}

	/** Twelve months of trends for the overview, each series gated like the tile over the same data. */
	@Transactional(readOnly = true)
	public DashboardCharts charts(AuthenticatedUser viewer) {
		YearMonth now = YearMonth.from(LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC));
		YearMonth first = now.minusMonths(CHART_MONTHS - 1);
		Instant from = first.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
		Instant to = now.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
		Currency currency = moneyProperties.defaultCurrency();

		List<DashboardCharts.PayoutMonth> payouts = null;
		if (viewer.hasPermission("FINANCE_VIEW")) {
			Map<YearMonth, Map<AccountType, Money>> byMonth = new TreeMap<>();
			ledger.monthlyTotals(PAYOUT_ACCOUNTS, currency, from, to).forEach(t -> byMonth
				.computeIfAbsent(t.month(), m -> new EnumMap<>(AccountType.class)).put(t.accountType(), t.amount()));
			Money zero = Money.zero(currency);
			payouts = byMonth.entrySet().stream().map(e -> new DashboardCharts.PayoutMonth(e.getKey().toString(),
					MoneyResponse.from(e.getValue().getOrDefault(AccountType.PLATFORM_RENT_EXPENSE, zero)),
					MoneyResponse.from(e.getValue().getOrDefault(AccountType.PLATFORM_CAPITAL_RETURNS, zero)),
					MoneyResponse.from(e.getValue().getOrDefault(AccountType.PLATFORM_REFERRAL_EXPENSE, zero)))).toList();
		}
		List<DashboardCharts.AmountMonth> invested = !viewer.hasPermission("INVESTMENT_VIEW") ? null
				: holdings.investedByMonth(currency, from).entrySet().stream()
					.map(e -> new DashboardCharts.AmountMonth(e.getKey().toString(), MoneyResponse.from(e.getValue()))).toList();
		List<DashboardCharts.CountMonth> investors = !viewer.hasPermission("USER_VIEW") ? null
				: roles.roleAssignmentsByMonth("INVESTOR", from).entrySet().stream()
					.map(e -> new DashboardCharts.CountMonth(e.getKey().toString(), e.getValue())).toList();
		List<DashboardCharts.StageCount> pipeline = null;
		if (viewer.hasPermission("LEAD_VIEW")) {
			Map<LeadStage, Long> counts = new EnumMap<>(LeadStage.class);
			leads.pipeline(new LeadService.Viewer(viewer.userId(), viewer.hasPermission("LEAD_ASSIGN")), null)
				.forEach(p -> counts.merge(p.stage(), p.leads(), Long::sum));
			pipeline = Arrays.stream(LeadStage.values())
				.map(stage -> new DashboardCharts.StageCount(stage, counts.getOrDefault(stage, 0L))).toList();
		}
		return new DashboardCharts(payouts, invested, investors, pipeline);
	}

	private static List<String> money(Collection<Money> amounts) {
		List<String> out = amounts.stream().filter(m -> !m.isZero()).map(ReportService.Format::money).toList();
		return out.isEmpty() ? List.of("—") : out;
	}

}
