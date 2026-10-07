package com.sealease.backend.reporting.service;

import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyFormat;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.earning.dto.DuePayouts;
import com.sealease.backend.earning.service.PayoutService;
import com.sealease.backend.investment.dto.HoldingResponse;
import com.sealease.backend.investment.dto.ProductResponse;
import com.sealease.backend.investment.service.HoldingService;
import com.sealease.backend.investment.service.ProductService;
import com.sealease.backend.invoice.dto.InvoiceResponse;
import com.sealease.backend.ledger.dto.InvestorStatement;
import com.sealease.backend.ledger.dto.PeriodTotal;
import com.sealease.backend.ledger.service.AccountType;
import com.sealease.backend.ledger.service.Direction;
import com.sealease.backend.ledger.service.LedgerService;
import com.sealease.backend.ledger.service.TransactionType;
import com.sealease.backend.reporting.engine.ReportDocument;
import com.sealease.backend.reporting.engine.ReportDocument.Row;
import com.sealease.backend.reporting.engine.ReportDocument.Section;
import com.sealease.backend.user.dto.UserAccount;
import com.sealease.backend.user.service.UserAccountService;
import com.sealease.backend.withdrawal.dto.WithdrawalResponse;
import com.sealease.backend.withdrawal.service.WithdrawalService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Builds report documents from the modules that own the data. Money figures come from the ledger
 * wherever possible, so reports agree with the books to the cent.
 */
@Service
public class ReportService {

	/** Longest period a statement or summary may cover. */
	static final long MAX_DAYS = 366;
	private static final String LEDGER_NOTE = "Figures are taken from the SeaLease double-entry ledger; amounts are in the "
			+ "currency shown, rounded to its minor unit. Dates are UTC.";

	private final LedgerService ledger;
	private final HoldingService holdings;
	private final ProductService products;
	private final PayoutService payouts;
	private final WithdrawalService withdrawals;
	private final UserAccountService accounts;
	private final Clock clock;

	public ReportService(LedgerService ledger, HoldingService holdings, ProductService products, PayoutService payouts,
			WithdrawalService withdrawals, UserAccountService accounts, Clock clock) {
		this.ledger = ledger;
		this.holdings = holdings;
		this.products = products;
		this.payouts = payouts;
		this.withdrawals = withdrawals;
		this.accounts = accounts;
		this.clock = clock;
	}

	/** An inclusive range of UTC dates. */
	public record Period(LocalDate from, LocalDate to) {

		public Period {
			if (from == null || to == null || to.isBefore(from)) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Give a period whose end is not before its start");
			}
			if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED, "A report covers at most " + MAX_DAYS + " days");
			}
		}

		Instant start() {
			return from.atStartOfDay(ZoneOffset.UTC).toInstant();
		}

		/** Exclusive. */
		Instant end() {
			return to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
		}

		String label() {
			return Format.date(from) + " – " + Format.date(to);
		}

	}

	// ------------------------------------------------------------------- investor statement

	@Transactional(readOnly = true)
	public ReportDocument investorStatement(UUID userId, Period period) {
		UserAccount investor = accounts.getAccount(userId);
		List<Section> sections = new ArrayList<>();
		for (InvestorStatement s : ledger.investorStatements(userId, period.start(), period.end())) {
			Currency currency = Currency.getInstance(s.currency());
			sections.add(new Section("Summary (" + MoneyFormat.symbol(currency) + ")", List.of("", "", "", "Amount"), List.of(
					Row.of("Opening balance", "", "", Format.money(s.opening())),
					Row.of("Monthly payouts", "Rent plus capital returned", "", Format.money(sum(s, currency, TransactionType.INVESTOR_PAYOUT))),
					Row.of("Referral commissions", "", "", Format.money(sum(s, currency, TransactionType.REFERRAL_COMMISSION))),
					Row.of("Adjustments", "", "", Format.money(sum(s, currency, TransactionType.ADJUSTMENT))),
					Row.of("Withdrawals", "Requested, less any returned", "",
							Format.money(sum(s, currency, TransactionType.WITHDRAWAL_RESERVE)
								.plus(sum(s, currency, TransactionType.WITHDRAWAL_RELEASE)))),
					Row.total("Closing balance", "", "", Format.money(s.closing())))));
			sections.add(new Section("Account activity (" + MoneyFormat.symbol(currency) + ")", List.of("Date", "Description", "Type", "Amount"),
					s.entries().stream()
						.map(e -> Row.of(Format.date(e.at()), e.description(), Format.label(e.type().name()), Format.money(e.amount())))
						.toList()));
		}
		if (sections.isEmpty()) {
			sections.add(new Section("Summary", List.of("", "", "", "Amount"), List.of()));
		}
		List<HoldingResponse> held = holdings.portfolio(userId).holdings();
		sections.add(new Section("Containers", List.of("Plan", "Container", "Lease", "Invested"), held.stream()
			.map(h -> Row.of(h.productCode(), h.container().containerNumber() + " · " + Format.label(h.status().name()),
					Format.date(h.leaseStartsOn()) + " – " + Format.date(h.leaseEndsOn().minusDays(1)), Format.money(h.amount())))
			.toList()));
		List<WithdrawalResponse> paidOut = withdrawals.forStatement(userId, period.start(), period.end());
		sections.add(new Section("Withdrawals requested in the period", List.of("Date", "Reference", "Status", "Amount"),
				paidOut.stream()
					.map(w -> Row.of(Format.date(w.createdAt()), w.reference() + " · " + w.bankName() + " " + w.bankAccountMasked(),
							Format.label(w.status().name()), Format.money(w.amount())))
					.toList()));
		return new ReportDocument("Investor statement", investor.firstName() + " " + investor.lastName() + " · "
				+ investor.email() + " · " + period.label(),
				"statement-" + period.from() + "-" + period.to(), clock.instant(), sections, LEDGER_NOTE);
	}

	// -------------------------------------------------------------------- financial summary

	@Transactional(readOnly = true)
	public ReportDocument financialSummary(Period period) {
		Map<String, List<PeriodTotal>> totals = ledger.periodTotals(period.start(), period.end());
		Map<String, Money> owed = ledger.balancesAt(AccountType.INVESTOR_EARNINGS, period.end());
		Map<String, Money> inTransit = ledger.balancesAt(AccountType.WITHDRAWALS_IN_TRANSIT, period.end());
		Set<String> currencies = new TreeSet<>(totals.keySet());
		currencies.addAll(owed.keySet());
		List<Section> sections = new ArrayList<>();
		for (String code : currencies) {
			Currency currency = Currency.getInstance(code);
			List<PeriodTotal> t = totals.getOrDefault(code, List.of());
			Money zero = Money.zero(currency);
			sections.add(new Section("Totals (" + MoneyFormat.symbol(currency) + ")", List.of("Item", "Basis", "", "Amount"), List.of(
					Row.of("Rent paid to investors", "Monthly rent on their containers", "",
							Format.money(total(t, zero, TransactionType.INVESTOR_PAYOUT, AccountType.PLATFORM_RENT_EXPENSE, Direction.DEBIT))),
					Row.of("Capital returned to investors", "Monthly part of the price", "",
							Format.money(total(t, zero, TransactionType.INVESTOR_PAYOUT, AccountType.PLATFORM_CAPITAL_RETURNS, Direction.DEBIT))),
					Row.of("Referral commissions", "Paid by the platform", "",
							Format.money(total(t, zero, TransactionType.REFERRAL_COMMISSION, AccountType.PLATFORM_REFERRAL_EXPENSE, Direction.DEBIT))),
					Row.of("Adjustments to investors", "Credits less debits", "",
							Format.money(total(t, zero, TransactionType.ADJUSTMENT, AccountType.INVESTOR_EARNINGS, Direction.CREDIT)
								.minus(total(t, zero, TransactionType.ADJUSTMENT, AccountType.INVESTOR_EARNINGS, Direction.DEBIT)))),
					Row.of("Withdrawals requested", "", "",
							Format.money(total(t, zero, TransactionType.WITHDRAWAL_RESERVE, AccountType.WITHDRAWALS_IN_TRANSIT, Direction.CREDIT))),
					Row.of("Withdrawals returned", "Rejected, cancelled or failed", "",
							Format.money(total(t, zero, TransactionType.WITHDRAWAL_RELEASE, AccountType.WITHDRAWALS_IN_TRANSIT, Direction.DEBIT))),
					Row.of("Withdrawals paid out", "", "",
							Format.money(total(t, zero, TransactionType.WITHDRAWAL_PAYOUT, AccountType.RENTAL_CASH, Direction.CREDIT))),
					Row.total("Owed to investors", "At the end of the period", "", Format.money(owed.getOrDefault(code, zero))),
					Row.total("Withdrawals in transit", "At the end of the period", "", Format.money(inTransit.getOrDefault(code, zero))))));
		}
		return new ReportDocument("Financial summary", period.label(), "financial-summary-" + period.from() + "-" + period.to(),
				clock.instant(), sections, LEDGER_NOTE);
	}

	// --------------------------------------------------------------------- plans & payouts

	@Transactional(readOnly = true)
	public ReportDocument offerings() {
		List<ProductResponse> all = products.published();
		Map<UUID, Long> investors = holdings.investorCounts(all.stream().map(ProductResponse::id).toList());
		LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);

		List<Row> plans = all.stream()
			.map(p -> Row.of(p.code(), p.title() + " · " + Format.label(p.status().name()),
					p.containersSold() + " sold · " + p.availableContainers() + " available · "
							+ investors.getOrDefault(p.id(), 0L) + " investor(s)",
					Format.money(p.price()) + " · " + Format.percent(p.monthlyRentPercent()) + " rent + "
							+ Format.percent(p.monthlyCapitalReturnPercent()) + " capital a month"))
			.toList();
		List<DuePayouts> due = payouts.due();
		List<Row> unpaid = due.stream()
			.map(d -> Row.of("Due and unpaid", d.count() + " payout(s)", "", Format.money(d.total())))
			.toList();
		return new ReportDocument("Plans & payouts", "As of " + Format.date(today), "plans-" + today, clock.instant(),
				List.of(new Section("Plans", List.of("Plan", "Title", "Containers", "Terms"), plans),
						new Section("Payouts due and not yet paid", List.of("", "Payouts", "", "Amount"), unpaid)),
				"Containers sold count confirmed purchases. Payouts are credited automatically as they fall due.");
	}

	// ------------------------------------------------------------------------------ invoice

	public ReportDocument invoice(InvoiceResponse invoice) {
		InvoiceResponse.Party issuer = invoice.issuer();
		InvoiceResponse.Party buyer = invoice.buyer();
		List<Row> parties = new ArrayList<>();
		parties.add(Row.of("From", issuer.name(), issuer.taxId() == null ? "" : "Tax ID " + issuer.taxId(), ""));
		parties.add(Row.of("", issuer.address(), "", ""));
		parties.add(Row.of("Bill to", buyer.name(), buyer.email(), ""));
		if (buyer.address() != null) {
			parties.add(Row.of("", buyer.address(), "", ""));
		}
		List<Row> lines = new ArrayList<>(invoice.lines().stream()
			.map(l -> Row.of(String.valueOf(l.lineNumber()), l.description(), "", Format.money(l.amount())))
			.toList());
		lines.add(Row.total("", "Total", "", Format.money(invoice.total())));
		return new ReportDocument("Invoice " + invoice.invoiceNumber(), "Order " + invoice.orderNumber() + " · issued "
				+ Format.date(invoice.issuedAt()), "invoice-" + invoice.invoiceNumber(), clock.instant(),
				List.of(new Section("Parties", List.of("", "Name", "Details", ""), parties),
						new Section("Items", List.of("#", "Description", "", "Amount"), lines)),
				invoice.notes() == null ? "Thank you for investing with SeaLease. This invoice was issued electronically."
						: invoice.notes());
	}

	// ----------------------------------------------------------------------------- internal

	private static Money sum(InvestorStatement s, Currency currency, TransactionType type) {
		return s.entries().stream().filter(e -> e.type() == type).map(InvestorStatement.Entry::amount)
			.reduce(Money.zero(currency), Money::plus);
	}

	private static Money total(List<PeriodTotal> totals, Money zero, TransactionType type, AccountType account,
			Direction direction) {
		Predicate<PeriodTotal> match = t -> t.transactionType() == type && t.accountType() == account
				&& t.direction() == direction;
		return totals.stream().filter(match).map(PeriodTotal::amount).reduce(zero, Money::plus);
	}

	/** Formatting for people: grouped digits, currency code, short dates. */
	static final class Format {

		private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy");

		static String money(Money m) {
			return m.display();
		}

		static String money(MoneyResponse m) {
			return m == null ? "" : money(Money.of(new BigDecimal(m.amount()), Currency.getInstance(m.currency())));
		}

		static String date(Instant at) {
			return at == null ? "" : DATE.format(at.atZone(ZoneOffset.UTC));
		}

		static String date(LocalDate day) {
			return day == null ? "" : DATE.format(day);
		}

		static String percent(BigDecimal p) {
			return p.stripTrailingZeros().toPlainString() + "%";
		}

		/** "WITHDRAWAL_RESERVE" → "Withdrawal reserve" */
		static String label(String constant) {
			String s = constant.toLowerCase(Locale.ROOT).replace('_', ' ');
			return Character.toUpperCase(s.charAt(0)) + s.substring(1);
		}

		private Format() {
		}

	}

}
