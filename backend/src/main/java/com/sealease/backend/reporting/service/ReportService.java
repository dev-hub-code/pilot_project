package com.sealease.backend.reporting.service;

import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.earning.dto.DuePeriodResponse;
import com.sealease.backend.earning.service.RentalService;
import com.sealease.backend.investment.dto.HoldingResponse;
import com.sealease.backend.investment.dto.Lease;
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
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
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
	private final RentalService rentals;
	private final WithdrawalService withdrawals;
	private final UserAccountService accounts;
	private final Clock clock;

	public ReportService(LedgerService ledger, HoldingService holdings, ProductService products, RentalService rentals,
			WithdrawalService withdrawals, UserAccountService accounts, Clock clock) {
		this.ledger = ledger;
		this.holdings = holdings;
		this.products = products;
		this.rentals = rentals;
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
			sections.add(new Section("Summary (" + s.currency() + ")", List.of("", "", "", "Amount"), List.of(
					Row.of("Opening balance", "", "", Format.money(s.opening())),
					Row.of("Rental income", "Your share of rent, after fees", "", Format.money(sum(s, currency, TransactionType.RENTAL_DISTRIBUTION))),
					Row.of("Referral commissions", "", "", Format.money(sum(s, currency, TransactionType.REFERRAL_COMMISSION))),
					Row.of("Adjustments", "", "", Format.money(sum(s, currency, TransactionType.ADJUSTMENT))),
					Row.of("Withdrawals", "Requested, less any returned", "",
							Format.money(sum(s, currency, TransactionType.WITHDRAWAL_RESERVE)
								.plus(sum(s, currency, TransactionType.WITHDRAWAL_RELEASE)))),
					Row.total("Closing balance", "", "", Format.money(s.closing())))));
			sections.add(new Section("Account activity (" + s.currency() + ")", List.of("Date", "Description", "Type", "Amount"),
					s.entries().stream()
						.map(e -> Row.of(Format.date(e.at()), e.description(), Format.label(e.type().name()), Format.money(e.amount())))
						.toList()));
		}
		if (sections.isEmpty()) {
			sections.add(new Section("Summary", List.of("", "", "", "Amount"), List.of()));
		}
		List<HoldingResponse> held = holdings.portfolio(userId).holdings();
		sections.add(new Section("Investments", List.of("Offering", "Title", "Ownership", "Invested"), held.stream()
			.map(h -> Row.of(h.productCode(), h.productTitle() + " · " + Format.label(h.status().name()),
					Format.percent(h.ownershipPercent()), Format.money(h.amount())))
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
			sections.add(new Section(code, List.of("Item", "Basis", "", "Amount"), List.of(
					Row.of("Rent collected", "Lessee payments distributed", "",
							Format.money(total(t, zero, TransactionType.RENTAL_DISTRIBUTION, AccountType.RENTAL_CASH, Direction.DEBIT))),
					Row.of("  to investors", "Net of management fees", "",
							Format.money(total(t, zero, TransactionType.RENTAL_DISTRIBUTION, AccountType.INVESTOR_EARNINGS, Direction.CREDIT))),
					Row.of("  management fees", "Platform revenue", "",
							Format.money(total(t, zero, TransactionType.RENTAL_DISTRIBUTION, AccountType.PLATFORM_FEE_REVENUE, Direction.CREDIT))),
					Row.of("  retained", "Unsold shares and rounding", "",
							Format.money(total(t, zero, TransactionType.RENTAL_DISTRIBUTION, AccountType.PLATFORM_RETAINED, Direction.CREDIT))),
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

	// ------------------------------------------------------------------ offerings & funding

	@Transactional(readOnly = true)
	public ReportDocument offerings() {
		List<ProductResponse> all = products.published();
		List<UUID> ids = all.stream().map(ProductResponse::id).toList();
		Map<UUID, Long> investors = holdings.investorCounts(ids);
		Map<UUID, RentalService.Received> received = rentals.received(ids);
		LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);

		List<Row> funding = all.stream()
			.map(p -> Row.of(p.code(), p.title() + " · " + Format.label(p.status().name()),
					p.capacity().fundedPercent().stripTrailingZeros().toPlainString() + "% · "
							+ investors.getOrDefault(p.id(), 0L) + " investor(s)",
					Format.money(p.capacity().committed()) + " of " + Format.money(p.price())))
			.toList();
		List<Row> leases = all.stream()
			.filter(p -> p.leaseStartsOn() != null)
			.map(p -> {
				int periods = Lease.periodCount(p.rentalFrequency(), p.durationMonths());
				RentalService.Received paid = received.get(p.id());
				Money currencyZero = Money.zero(Currency.getInstance(p.price().currency()));
				return Row.of(p.code(), Format.date(p.leaseStartsOn()) + " – " + Format.date(p.leaseEndsOn().minusDays(1)),
						(paid == null ? 0 : paid.periods()) + " of " + periods + " periods paid",
						Format.money(paid == null ? currencyZero : paid.amount()) + " received");
			})
			.toList();
		List<DuePeriodResponse> due = rentals.due();
		List<Row> overdue = due.stream()
			.map(d -> Row.of(d.productCode(), "Period " + d.periodNumber() + " of " + d.periodCount(),
					"Due " + Format.date(d.dueOn()) + (d.daysOverdue() > 0 ? " · " + d.daysOverdue() + " days late" : ""),
					Format.money(d.expectedAmount())))
			.toList();
		return new ReportDocument("Offerings & funding", "As of " + Format.date(today), "offerings-" + today, clock.instant(),
				List.of(new Section("Funding", List.of("Offering", "Title", "Funded", "Committed"), funding),
						new Section("Leases", List.of("Offering", "Lease", "Rent", "Received"), leases),
						new Section("Rent due and not recorded", List.of("Offering", "Period", "Due", "Expected"), overdue)),
				"Funded percentages count confirmed investments only. Received rent is what has been distributed to investors.");
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
			BigDecimal v = m.toMinorUnitScale();
			DecimalFormat f = new DecimalFormat(v.scale() == 0 ? "#,##0" : "#,##0." + "0".repeat(v.scale()),
					DecimalFormatSymbols.getInstance(Locale.ROOT));
			return f.format(v) + " " + m.currency().getCurrencyCode();
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
