package com.sealease.backend.reporting.catalog;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.common.time.DisplayTimeZone;
import com.sealease.backend.container.entity.ContainerCondition;
import com.sealease.backend.container.entity.ContainerStatus;
import com.sealease.backend.container.entity.ContainerType;
import com.sealease.backend.crm.entity.LeadSource;
import com.sealease.backend.crm.entity.LeadStage;
import com.sealease.backend.helpdesk.entity.SupportTeam;
import com.sealease.backend.helpdesk.entity.TicketCategory;
import com.sealease.backend.helpdesk.entity.TicketPriority;
import com.sealease.backend.helpdesk.entity.TicketStatus;
import com.sealease.backend.investment.entity.HoldingStatus;
import com.sealease.backend.kyc.entity.KycSubmissionStatus;
import com.sealease.backend.ledger.service.AccountType;
import com.sealease.backend.ledger.service.Direction;
import com.sealease.backend.ledger.service.EarningsBalance;
import com.sealease.backend.ledger.service.TransactionType;
import com.sealease.backend.order.entity.OrderStatus;
import com.sealease.backend.payment.entity.PaymentMethod;
import com.sealease.backend.payment.entity.PaymentStatus;
import com.sealease.backend.referral.dto.ReferralScheme;
import com.sealease.backend.referral.entity.ReferralEarningKind;
import com.sealease.backend.user.entity.KycStatus;
import com.sealease.backend.user.entity.UserStatus;
import com.sealease.backend.withdrawal.entity.WithdrawalPayoutMethod;
import com.sealease.backend.withdrawal.entity.WithdrawalStatus;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.sealease.backend.reporting.catalog.ReportColumn.date;
import static com.sealease.backend.reporting.catalog.ReportColumn.dateTime;
import static com.sealease.backend.reporting.catalog.ReportColumn.id;
import static com.sealease.backend.reporting.catalog.ReportColumn.money;
import static com.sealease.backend.reporting.catalog.ReportColumn.month;
import static com.sealease.backend.reporting.catalog.ReportColumn.number;
import static com.sealease.backend.reporting.catalog.ReportColumn.percent;
import static com.sealease.backend.reporting.catalog.ReportColumn.status;
import static com.sealease.backend.reporting.catalog.ReportColumn.text;

/**
 * Every report the reporting area offers, as fixed read-only queries over the tables the modules own. Money comes
 * from the double-entry ledger where the ledger records it (earnings, commissions, adjustments, withdrawals);
 * investment receipts and refunds, which the ledger does not record, come from the payment records and are labelled
 * so. Investor capital is never presented as company revenue.
 *
 * <p>Access: every report needs REPORT_VIEW; reports that name investors or show their money also need one of the
 * existing permissions in their {@code requires} set. Aggregates without personal data need REPORT_VIEW alone.
 */
@Component
public class ReportCatalog {

	// Permission groups: any one of each set (on top of REPORT_VIEW). Empty: REPORT_VIEW alone.
	private static final String[] AGGREGATE = {};
	private static final String[] FINANCIAL = { "FINANCIAL_REPORT_VIEW", "FINANCE_VIEW", "LEDGER_VIEW" };
	private static final String[] INVESTMENTS = { "INVESTOR_ANALYTICS_VIEW", "INVESTMENT_VIEW", "INVESTOR_VIEW", "FINANCE_VIEW" };
	private static final String[] INVESTORS = { "INVESTOR_ANALYTICS_VIEW", "INVESTOR_VIEW", "USER_VIEW" };
	private static final String[] EARNINGS = { "FINANCIAL_REPORT_VIEW", "FINANCE_VIEW", "PAYOUT_VIEW", "LEDGER_VIEW" };
	private static final String[] PAYMENTS = { "FINANCIAL_REPORT_VIEW", "FINANCE_VIEW", "PAYMENT_VIEW", "PAYMENT_SEARCH" };
	private static final String[] WITHDRAWALS = { "FINANCIAL_REPORT_VIEW", "FINANCE_VIEW", "WITHDRAWAL_VIEW" };
	private static final String[] REFERRALS = { "FINANCIAL_REPORT_VIEW", "FINANCE_VIEW", "REFERRAL_COMMISSION_VIEW", "REFERRAL_VIEW" };
	private static final String[] CONTAINERS = { "OPERATIONAL_REPORT_VIEW", "CONTAINER_VIEW", "INVESTMENT_VIEW" };
	private static final String[] ORDERS = { "OPERATIONAL_REPORT_VIEW", "ORDER_VIEW" };
	private static final String[] AGREEMENTS = { "OPERATIONAL_REPORT_VIEW", "AGREEMENT_VIEW", "ORDER_VIEW" };
	private static final String[] LEADS = { "OPERATIONAL_REPORT_VIEW", "LEAD_VIEW" };
	private static final String[] TICKETS = { "OPERATIONAL_REPORT_VIEW", "SUPPORT_TICKET_VIEW", "SUPPORT_VIEW", "SUPPORT_ANALYTICS_VIEW" };
	private static final String[] KYC = { "KYC_VIEW", "KYC_REVIEW", "AUDIT_VIEW", "COMPLIANCE_CASE_VIEW" };
	private static final String[] AUDIT = { "AUDIT_VIEW" };

	/** Audit actions that record a look at, or export of, sensitive documents and data. */
	static final List<String> SENSITIVE_ACCESS = List.of("KYC_DOCUMENT_VIEWED", "KYC_SUBMISSION_VIEWED",
			"BANK_ACCOUNT_PROOF_VIEWED", "TICKET_ATTACHMENT_VIEWED", "FINANCE_TASKS_VIEWED", "WITHDRAWAL_BATCH_EXPORTED",
			"AUDIT_LOG_EXPORTED", "REPORT_GENERATED");

	private final Map<String, ReportDefinition> reports = new LinkedHashMap<>();
	private final String zone;

	public ReportCatalog(DisplayTimeZone display) {
		String id = display.zone().getId();
		// The zone goes into SQL text (month buckets); it comes from configuration, but is checked all the same.
		if (!id.matches("[A-Za-z0-9_/+\\-]+")) {
			throw new IllegalStateException("Unexpected time zone id " + id);
		}
		this.zone = id;
		financial();
		investments();
		investors();
		earnings();
		payments();
		withdrawals();
		referrals();
		containers();
		operations();
		crm();
		helpdesk();
		compliance();
	}

	public List<ReportDefinition> all() {
		return List.copyOf(reports.values());
	}

	public Optional<ReportDefinition> find(String key) {
		return Optional.ofNullable(reports.get(key));
	}

	// ------------------------------------------------------------------------- helpers

	private static String code(String alias) {
		return "('SL' || lpad(" + alias + ".user_number::text, 5, '0'))";
	}

	private static String name(String alias) {
		return "(" + alias + ".first_name || ' ' || " + alias + ".last_name)";
	}

	/** First day of the month the instant falls in, in the display time zone. */
	private String monthOf(String instant) {
		return "(date_trunc('month', (" + instant + ") AT TIME ZONE '" + zone + "'))::date";
	}

	private static List<String> names(Enum<?>[] values) {
		return Arrays.stream(values).map(Enum::name).toList();
	}

	private static String quoted(List<String> values) {
		return values.stream().map(v -> "'" + v + "'").collect(Collectors.joining(", ", "(", ")"));
	}

	/** Account types whose balance is normally a debit (assets and expenses). */
	private static String debitNormal() {
		return quoted(Arrays.stream(AccountType.values()).filter(t -> t.normalBalance() == Direction.DEBIT)
			.map(Enum::name).toList());
	}

	private static String investorAccounts() {
		return quoted(Arrays.stream(AccountType.values()).filter(AccountType::isPerInvestor).map(Enum::name).toList());
	}

	private static String accountName(String typeSql) {
		return "CASE " + typeSql
				+ " WHEN 'RENTAL_CASH' THEN 'Client-money bank account'"
				+ " WHEN 'INVESTOR_EARNINGS' THEN 'Investor earnings owed (bank)'"
				+ " WHEN 'INVESTOR_CASH_EARNINGS' THEN 'Investor earnings owed (cash)'"
				+ " WHEN 'INVESTOR_REFERRAL_EARNINGS' THEN 'Referral commission owed'"
				+ " WHEN 'PLATFORM_ADJUSTMENTS' THEN 'Adjustments to investors'"
				+ " WHEN 'PLATFORM_REFERRAL_EXPENSE' THEN 'Referral commission expense'"
				+ " WHEN 'WITHDRAWALS_IN_TRANSIT' THEN 'Withdrawals in transit'"
				+ " WHEN 'PLATFORM_RENT_EXPENSE' THEN 'Returns paid to investors'"
				+ " WHEN 'PLATFORM_CAPITAL_RETURNS' THEN 'Capital returned to investors'"
				+ " ELSE " + typeSql + " END";
	}

	private static final String INVESTOR_TYPE_SQL =
			"CASE WHEN EXISTS (SELECT 1 FROM network_investors ni WHERE ni.user_id = %s.id) THEN 'NETWORK' ELSE 'ONLINE' END";

	private static String investorType(String userAlias) {
		return INVESTOR_TYPE_SQL.formatted(userAlias);
	}

	private static final String IS_INVESTOR =
			"EXISTS (SELECT 1 FROM user_roles ur JOIN roles r ON r.id = ur.role_id WHERE ur.user_id = %s.id AND r.name = 'INVESTOR')";

	private ReportDefinitionBuilder report(String key, ReportCategory category, String title) {
		return new ReportDefinitionBuilder(key, category, title);
	}

	private void add(ReportDefinitionBuilder builder) {
		ReportDefinition definition = builder.build();
		if (reports.putIfAbsent(definition.key(), definition) != null) {
			throw new IllegalStateException("Duplicate report " + definition.key());
		}
	}

	// ----------------------------------------------------------------------- financial

	private void financial() {
		String signedForAccount = "CASE WHEN e.direction = (CASE WHEN a.account_type IN " + debitNormal()
				+ " THEN 'DEBIT' ELSE 'CREDIT' END) THEN e.amount ELSE -e.amount END";
		add(report("general-ledger", ReportCategory.FINANCIAL, "General ledger")
			.describe("Every ledger entry with its account, reference and running balance on the account.")
			.requires(FINANCIAL)
			.from("""
					(SELECT e.id, e.created_at, e.direction, e.amount, a.account_type, a.owner_user_id, a.currency,
					        t.transaction_type, t.reference, t.description,
					        sum(%s) OVER (PARTITION BY e.account_id ORDER BY e.created_at, e.id) AS running_balance
					 FROM ledger_entries e
					 JOIN ledger_accounts a ON a.id = e.account_id
					 JOIN ledger_transactions t ON t.id = e.transaction_id) gl
					LEFT JOIN users u ON u.id = gl.owner_user_id""".formatted(signedForAccount))
			.columns(
					dateTime("postedAt", "Posted", "gl.created_at"),
					status("transactionType", "Transaction", "gl.transaction_type"),
					text("reference", "Reference", "gl.reference"),
					text("accountCode", "Account code", "gl.account_type"),
					text("accountName", "Account name", accountName("gl.account_type")),
					text("investor", "Investor", code("u")).linkTo("user", "userId"),
					id("userId", "u.id"),
					text("description", "Description", "gl.description"),
					money("debit", "Debit", "CASE WHEN gl.direction = 'DEBIT' THEN gl.amount END"),
					money("credit", "Credit", "CASE WHEN gl.direction = 'CREDIT' THEN gl.amount END"),
					money("balance", "Running balance", "gl.running_balance").notSummed())
			.period("gl.created_at", "Posted")
			.choice("account", "Account", "gl.account_type", names(AccountType.values()))
			.choice("transaction", "Transaction", "gl.transaction_type", names(TransactionType.values()))
			.search("gl.reference", "gl.description", code("u"))
			.amount("gl.amount")
			.currency("gl.currency")
			.sort("postedAt", true)
			.note("Running balances are on each account's normal side (debit for assets and expenses, credit for amounts "
					+ "owed) and include entries before the period. Total debits equal total credits."));

		String signed = "CASE WHEN e.direction = 'DEBIT' THEN e.amount ELSE -e.amount END";
		add(report("trial-balance", ReportCategory.FINANCIAL, "Trial balance")
			.describe("Opening, period and closing debits and credits per ledger account; debits equal credits.")
			.requires(AGGREGATE)
			.from("""
					(SELECT a.account_type, a.currency,
					        coalesce(sum(CASE WHEN e.created_at < :periodFrom THEN %1$s END), 0) AS opening_net,
					        coalesce(sum(CASE WHEN e.created_at >= :periodFrom AND e.created_at < :periodTo
					                          AND e.direction = 'DEBIT' THEN e.amount END), 0) AS period_debit,
					        coalesce(sum(CASE WHEN e.created_at >= :periodFrom AND e.created_at < :periodTo
					                          AND e.direction = 'CREDIT' THEN e.amount END), 0) AS period_credit,
					        coalesce(sum(CASE WHEN e.created_at < :periodTo THEN %1$s END), 0) AS closing_net
					 FROM ledger_accounts a LEFT JOIN ledger_entries e ON e.account_id = a.id
					 GROUP BY a.account_type, a.currency) tb""".formatted(signed))
			.columns(
					text("accountCode", "Account code", "tb.account_type"),
					text("accountName", "Account name", accountName("tb.account_type")),
					money("openingDebit", "Opening debit", "GREATEST(tb.opening_net, 0)"),
					money("openingCredit", "Opening credit", "GREATEST(-tb.opening_net, 0)"),
					money("periodDebit", "Period debit", "tb.period_debit"),
					money("periodCredit", "Period credit", "tb.period_credit"),
					money("closingDebit", "Closing debit", "GREATEST(tb.closing_net, 0)"),
					money("closingCredit", "Closing credit", "GREATEST(-tb.closing_net, 0)"))
			.currency("tb.currency")
			.sort("accountCode", false)
			.note("From the double-entry ledger. In the totals, opening debits equal opening credits, period debits equal "
					+ "period credits and closing debits equal closing credits. Investment receipts are not ledger entries "
					+ "(see Cash movements)."));

		add(report("reconciliation", ReportCategory.FINANCIAL, "Reconciliation exceptions")
			.describe("Live checks across the ledger, earnings, payments and withdrawals; an exception clears once corrected.")
			.requires(FINANCIAL)
			.from("""
					(SELECT 'UNBALANCED_TRANSACTION' AS kind, t.reference, t.created_at AS detected_at,
					        'Debits ' || sum(CASE WHEN e.direction = 'DEBIT' THEN e.amount ELSE 0 END)
					          || ' and credits ' || sum(CASE WHEN e.direction = 'CREDIT' THEN e.amount ELSE 0 END) AS detail,
					        abs(sum(CASE WHEN e.direction = 'DEBIT' THEN e.amount ELSE -e.amount END)) AS amount, t.currency
					 FROM ledger_transactions t JOIN ledger_entries e ON e.transaction_id = t.id
					 GROUP BY t.id, t.reference, t.created_at, t.currency
					 HAVING sum(CASE WHEN e.direction = 'DEBIT' THEN e.amount ELSE -e.amount END) <> 0
					 UNION ALL
					 SELECT 'MISSING_LEDGER_ENTRY', rc.holding_id::text || ' ' || rc.day, rc.created_at,
					        'Earnings credit without its ledger transaction', rc.rent_amount + rc.capital_amount, rc.currency
					 FROM rent_credits rc
					 WHERE rc.ledger_transaction_id IS NULL
					    OR NOT EXISTS (SELECT 1 FROM ledger_transactions t WHERE t.id = rc.ledger_transaction_id)
					 UNION ALL
					 SELECT 'EARNINGS_DISCREPANCY', t.reference, rc.created_at,
					        'Credit of ' || (rc.rent_amount + rc.capital_amount) || ' but ' || posted.amount || ' in the ledger',
					        abs(rc.rent_amount + rc.capital_amount - posted.amount), rc.currency
					 FROM rent_credits rc
					 JOIN ledger_transactions t ON t.id = rc.ledger_transaction_id
					 JOIN LATERAL (SELECT coalesce(sum(e.amount), 0) AS amount FROM ledger_entries e
					               JOIN ledger_accounts a ON a.id = e.account_id
					               WHERE e.transaction_id = t.id AND e.direction = 'CREDIT'
					                 AND a.account_type IN %1$s) posted ON true
					 WHERE posted.amount <> rc.rent_amount + rc.capital_amount
					 UNION ALL
					 SELECT 'WITHDRAWAL_DISCREPANCY', w.reference, w.closed_at, 'Paid, but no payout in the ledger', w.amount, w.currency
					 FROM withdrawals w
					 WHERE w.status = 'PAID' AND NOT EXISTS (SELECT 1 FROM ledger_transactions t
					       WHERE t.transaction_type = 'WITHDRAWAL_PAYOUT' AND t.reference = w.id::text)
					 UNION ALL
					 SELECT 'PAYMENT_DISCREPANCY', o.order_number, p.settled_at,
					        CASE WHEN o.status <> 'CONFIRMED' THEN 'Payment received for an order that is ' || o.status
					             ELSE 'Payment of ' || p.amount || ' for an order of ' || o.total_amount END,
					        abs(p.amount - o.total_amount), p.currency
					 FROM payments p JOIN orders o ON o.id = p.order_id
					 WHERE p.status = 'SUCCEEDED' AND (o.status <> 'CONFIRMED' OR p.amount <> o.total_amount)
					 UNION ALL
					 SELECT 'PAYMENT_DISCREPANCY', o.order_number, p.settled_at, 'Payment received but no invoice issued',
					        p.amount, p.currency
					 FROM payments p JOIN orders o ON o.id = p.order_id
					 WHERE p.status = 'SUCCEEDED' AND NOT EXISTS (SELECT 1 FROM invoices i WHERE i.order_id = p.order_id)
					 UNION ALL
					 SELECT 'DUPLICATE_REFERENCE', p.deposit_reference, max(p.created_at),
					        count(*) || ' payments give the same bank reference', sum(p.amount), min(p.currency)
					 FROM payments p
					 WHERE p.deposit_reference IS NOT NULL AND p.status IN ('PENDING', 'SUCCEEDED')
					 GROUP BY p.deposit_reference HAVING count(*) > 1
					) x""".formatted(investorAccounts()))
			.columns(
					status("kind", "Exception", "x.kind"),
					text("reference", "Reference", "x.reference"),
					dateTime("detectedAt", "Date", "x.detected_at"),
					text("detail", "Detail", "x.detail"),
					money("amount", "Amount", "x.amount"),
					status("resolution", "Status", "'OPEN'").unsortable())
			.period("x.detected_at", "Date")
			.choice("kind", "Exception", "x.kind", List.of("UNBALANCED_TRANSACTION", "MISSING_LEDGER_ENTRY",
					"EARNINGS_DISCREPANCY", "WITHDRAWAL_DISCREPANCY", "PAYMENT_DISCREPANCY", "DUPLICATE_REFERENCE"))
			.search("x.reference", "x.detail")
			.currency("x.currency")
			.sort("detectedAt", true)
			.note("Checks run live each time: an exception disappears once the records are corrected (by compensating "
					+ "entries for the ledger). An empty report means everything reconciles."));

		add(report("cash-movements", ReportCategory.FINANCIAL, "Cash movements")
			.describe("Investment money received, refunds and withdrawals paid out, by the day they moved.")
			.requires(FINANCIAL)
			.from("""
					(SELECT p.settled_at AS moved_at, 'INVESTMENT_RECEIVED' AS movement,
					        coalesce(p.receipt_number, p.deposit_reference, p.provider_reference, p.external_reference) AS reference,
					        p.user_id, p.order_id, p.method AS channel, p.amount AS inflow, NULL::numeric AS outflow, p.currency
					 FROM payments p WHERE p.settled_at IS NOT NULL AND p.status IN ('SUCCEEDED', 'REFUND_REQUIRED', 'REFUNDED')
					 UNION ALL
					 SELECT p.refunded_at, 'REFUND', p.refund_reference, p.user_id, p.order_id, p.method, NULL, p.amount, p.currency
					 FROM payments p WHERE p.refunded_at IS NOT NULL
					 UNION ALL
					 SELECT w.closed_at, 'WITHDRAWAL_PAID', w.reference, w.user_id, NULL, w.payout_method, NULL, w.amount, w.currency
					 FROM withdrawals w WHERE w.status = 'PAID' AND w.closed_at IS NOT NULL) m
					LEFT JOIN users u ON u.id = m.user_id""")
			.columns(
					dateTime("movedAt", "Date", "m.moved_at"),
					status("movement", "Movement", "m.movement"),
					text("reference", "Reference", "m.reference"),
					text("investor", "Investor", code("u")).linkTo("user", "userId"),
					id("userId", "u.id"),
					status("channel", "Channel", "m.channel"),
					money("inflow", "Inflow", "m.inflow"),
					money("outflow", "Outflow", "m.outflow"))
			.period("m.moved_at", "Date")
			.choice("movement", "Movement", "m.movement", List.of("INVESTMENT_RECEIVED", "REFUND", "WITHDRAWAL_PAID"))
			.search("m.reference", code("u"))
			.amount("coalesce(m.inflow, m.outflow)")
			.currency("m.currency")
			.sort("movedAt", true)
			.note("Investment money is client capital returned over the term, not company revenue. Bank balances are not "
					+ "recorded in SeaLease: the client-money account's ledger balance is in the Trial balance."));
	}

	// ----------------------------------------------------------------------- investments

	private static final String HOLDINGS_FROM = """
			holdings h
			JOIN users u ON u.id = h.user_id
			JOIN investment_products p ON p.id = h.product_id
			JOIN orders o ON o.id = h.order_id
			LEFT JOIN rent_accruals ra ON ra.holding_id = h.id
			LEFT JOIN containers c ON c.id = h.container_id""";

	private ReportColumn[] holdingColumns(boolean maturityFirst) {
		ReportColumn maturity = date("maturity", "Maturity", "h.lease_ends_on");
		List<ReportColumn> columns = new java.util.ArrayList<>(List.of(
				text("order", "Order", "o.order_number").linkTo("order", "orderId"),
				id("orderId", "o.id"),
				text("investor", "Investor ID", code("u")).linkTo("user", "userId"),
				id("userId", "u.id"),
				text("investorName", "Investor", name("u")),
				text("plan", "Plan", "p.code"),
				status("investorType", "Investor type", "p.audience"),
				dateTime("confirmedAt", "Invested", "h.confirmed_at"),
				money("principal", "Principal", "h.amount"),
				number("tenure", "Tenure (months)", "h.tenure_months").notSummed(),
				date("leaseStart", "Term starts", "h.lease_starts_on"),
				maturity,
				text("container", "Container", "c.container_number"),
				status("status", "Status", "h.status"),
				money("earned", "Returns credited", "coalesce(ra.credited_rent, 0)"),
				money("capitalReturned", "Capital returned", "coalesce(ra.credited_capital, 0)"),
				money("outstanding", "Capital to be repaid",
						"coalesce(ra.capital_total, 0) - coalesce(ra.credited_capital, 0)")));
		if (maturityFirst) {
			columns.remove(maturity);
			columns.addFirst(maturity);
		}
		return columns.toArray(ReportColumn[]::new);
	}

	private void investments() {
		add(report("investment-register", ReportCategory.INVESTMENT, "Investment register")
			.describe("Every investment (one per container) with principal, term, returns credited and capital outstanding.")
			.requires(INVESTMENTS)
			.from(HOLDINGS_FROM)
			.columns(holdingColumns(false))
			.period("h.confirmed_at", "Invested")
			.choice("status", "Status", "h.status", names(HoldingStatus.values()))
			.choice("investorType", "Investor type", "p.audience", names(ReferralScheme.values()))
			.search("o.order_number", code("u"), name("u"), "p.code", "c.container_number")
			.amount("h.amount")
			.currency("h.currency")
			.sort("confirmedAt", true)
			.note("Returns credited and capital returned are kept apart. Capital to be repaid is the capital the investment's "
					+ "terms schedule for return, less what has been returned; it is set when the term starts and is zero for "
					+ "plans that do not repay capital."));

		add(report("investment-maturity", ReportCategory.INVESTMENT, "Investment maturity")
			.describe("Investments by the day their term ends.")
			.requires(INVESTMENTS)
			.from(HOLDINGS_FROM)
			.columns(holdingColumns(true))
			.periodByDay("h.lease_ends_on", "Maturity")
			.choice("status", "Status", "h.status", names(HoldingStatus.values()))
			.choice("investorType", "Investor type", "p.audience", names(ReferralScheme.values()))
			.search("o.order_number", code("u"), name("u"), "p.code", "c.container_number")
			.amount("h.amount")
			.currency("h.currency")
			.sort("maturity", false));

		add(report("plan-performance", ReportCategory.INVESTMENT, "Plan performance")
			.describe("Investments, investors, capital and returns per plan.")
			.requires(AGGREGATE)
			.from("investment_products p JOIN holdings h ON h.product_id = p.id LEFT JOIN rent_accruals ra ON ra.holding_id = h.id")
			.groupBy("p.id, p.code, p.title, p.audience, p.status, p.currency")
			.columns(
					text("plan", "Plan", "p.code"),
					text("title", "Name", "p.title"),
					status("investorType", "Offered to", "p.audience"),
					status("status", "Plan status", "p.status"),
					number("investments", "Investments", "count(h.id)").summed(),
					number("investors", "Investors", "count(DISTINCT h.user_id)"),
					money("capital", "Capital invested", "sum(h.amount)"),
					money("earned", "Returns credited", "sum(coalesce(ra.credited_rent, 0))"),
					money("capitalReturned", "Capital returned", "sum(coalesce(ra.credited_capital, 0))"),
					money("outstanding", "Capital to be repaid",
							"sum(coalesce(ra.capital_total, 0) - coalesce(ra.credited_capital, 0))"))
			.period("h.confirmed_at", "Invested")
			.choice("investorType", "Offered to", "p.audience", names(ReferralScheme.values()))
			.search("p.code", "p.title")
			.currency("p.currency")
			.sort("capital", true));

		add(report("investment-trends", ReportCategory.INVESTMENT, "Monthly investment trends")
			.describe("New investments, investors and capital per month.")
			.requires(AGGREGATE)
			.from("holdings h JOIN investment_products p ON p.id = h.product_id")
			.where("h.confirmed_at IS NOT NULL")
			.groupBy(monthOf("h.confirmed_at") + ", h.currency")
			.columns(
					month("month", "Month", monthOf("h.confirmed_at")),
					number("investments", "Investments", "count(*)").summed(),
					number("investors", "Investors", "count(DISTINCT h.user_id)"),
					money("capital", "Capital invested", "sum(h.amount)"),
					money("average", "Average investment", "round(avg(h.amount), 2)").notSummed())
			.period("h.confirmed_at", "Invested")
			.choice("investorType", "Investor type", "p.audience", names(ReferralScheme.values()))
			.currency("h.currency")
			.sort("month", true));

		add(report("investor-type-performance", ReportCategory.INVESTMENT, "Investor-type performance")
			.describe("Network and online investors compared: investments, capital and returns.")
			.requires(AGGREGATE)
			.from("holdings h JOIN investment_products p ON p.id = h.product_id LEFT JOIN rent_accruals ra ON ra.holding_id = h.id")
			.groupBy("p.audience, h.currency")
			.columns(
					status("investorType", "Investor type", "p.audience"),
					number("investments", "Investments", "count(h.id)").summed(),
					number("investors", "Investors", "count(DISTINCT h.user_id)"),
					money("capital", "Capital invested", "sum(h.amount)"),
					money("earned", "Returns credited", "sum(coalesce(ra.credited_rent, 0))"),
					money("outstanding", "Capital to be repaid",
							"sum(coalesce(ra.capital_total, 0) - coalesce(ra.credited_capital, 0))"))
			.period("h.confirmed_at", "Invested")
			.currency("h.currency")
			.sort("capital", true));
	}

	// ------------------------------------------------------------------------- investors

	private void investors() {
		add(report("investor-master", ReportCategory.INVESTOR, "Investor master")
			.describe("Every investor with type, account and KYC status, investments, earnings owed and withdrawals.")
			.requires(INVESTORS)
			.from("""
					users u
					LEFT JOIN user_profiles pr ON pr.user_id = u.id
					LEFT JOIN LATERAL (SELECT count(*) AS investments, coalesce(sum(h.amount), 0) AS capital
					                   FROM holdings h WHERE h.user_id = u.id) hs ON true
					LEFT JOIN LATERAL (SELECT coalesce(sum(CASE WHEN e.direction = 'CREDIT' THEN e.amount ELSE -e.amount END), 0) AS owed
					                   FROM ledger_accounts a JOIN ledger_entries e ON e.account_id = a.id
					                   WHERE a.owner_user_id = u.id AND a.account_type IN %s) bal ON true
					LEFT JOIN LATERAL (SELECT coalesce(sum(w.amount), 0) AS withdrawn FROM withdrawals w
					                   WHERE w.user_id = u.id AND w.status = 'PAID') wd ON true""".formatted(investorAccounts()))
			.where(IS_INVESTOR.formatted("u"))
			.columns(
					text("investor", "Investor ID", code("u")).linkTo("user", "userId"),
					id("userId", "u.id"),
					text("name", "Name", name("u")),
					text("email", "Email", "u.email").maskedWithout("INVESTOR_CONTACT_VIEW", "USER_VIEW").unsortable(),
					text("phone", "Phone", "pr.phone").maskedWithout("PHONE_NUMBER_VIEW", "INVESTOR_CONTACT_VIEW").unsortable(),
					status("investorType", "Investor type", investorType("u")),
					status("status", "Account", "u.status"),
					status("kyc", "KYC", "coalesce(pr.kyc_status, 'NOT_SUBMITTED')"),
					text("city", "City", "pr.city"),
					dateTime("joinedAt", "Joined", "u.created_at"),
					number("investments", "Investments", "hs.investments").summed(),
					money("capital", "Capital invested", "hs.capital"),
					money("owed", "Earnings owed", "bal.owed"),
					money("withdrawn", "Withdrawn", "wd.withdrawn"))
			.period("u.created_at", "Joined")
			.choice("investorType", "Investor type", investorType("u"), names(ReferralScheme.values()))
			.choice("status", "Account", "u.status", names(UserStatus.values()))
			.choice("kyc", "KYC", "coalesce(pr.kyc_status, 'NOT_SUBMITTED')", names(KycStatus.values()))
			.search(code("u"), name("u"), "pr.city")
			.amount("hs.capital")
			.sort("joinedAt", true)
			.note("Earnings owed is the investor's wallet balance in the ledger (returns, capital and referral commission "
					+ "credited, less withdrawals). Email needs USER_VIEW or INVESTOR_CONTACT_VIEW; phone numbers "
					+ "need PHONE_NUMBER_VIEW or INVESTOR_CONTACT_VIEW, as on the user pages."));

		String signed = "CASE WHEN e.direction = 'CREDIT' THEN e.amount ELSE -e.amount END";
		String inPeriod = "e.created_at >= :periodFrom";
		add(report("investor-statements", ReportCategory.INVESTOR, "Investor financial statements")
			.describe("Per investor: opening balance, returns and capital credited, commissions, adjustments, withdrawals, closing.")
			.requires(INVESTORS)
			.from("""
					ledger_accounts a
					JOIN ledger_entries e ON e.account_id = a.id
					JOIN ledger_transactions t ON t.id = e.transaction_id
					JOIN users u ON u.id = a.owner_user_id""")
			.where("a.account_type IN " + investorAccounts() + " AND e.created_at < :periodTo")
			.groupBy("u.id, u.user_number, u.first_name, u.last_name, a.currency")
			.columns(
					text("investor", "Investor ID", code("u")).linkTo("user", "userId"),
					id("userId", "u.id"),
					text("name", "Name", name("u")),
					money("opening", "Opening balance", "coalesce(sum(CASE WHEN e.created_at < :periodFrom THEN " + signed + " END), 0)"),
					money("returns", "Returns & capital credited", "coalesce(sum(CASE WHEN " + inPeriod
							+ " AND t.transaction_type = 'INVESTOR_PAYOUT' THEN " + signed + " END), 0)"),
					money("referral", "Referral commission", "coalesce(sum(CASE WHEN " + inPeriod
							+ " AND t.transaction_type = 'REFERRAL_COMMISSION' THEN " + signed + " END), 0)"),
					money("adjustments", "Adjustments", "coalesce(sum(CASE WHEN " + inPeriod
							+ " AND t.transaction_type = 'ADJUSTMENT' THEN " + signed + " END), 0)"),
					money("withdrawals", "Withdrawals", "coalesce(-sum(CASE WHEN " + inPeriod
							+ " AND t.transaction_type IN ('WITHDRAWAL_RESERVE', 'WITHDRAWAL_RELEASE') THEN " + signed + " END), 0)"),
					money("closing", "Closing balance", "coalesce(sum(" + signed + "), 0)"))
			.search(code("u"), name("u"))
			.currency("a.currency")
			.sort("closing", true)
			.note("From the investors' ledger accounts: closing = opening + credits + adjustments − withdrawals. "
					+ "Withdrawals are counted when requested (reserved) and added back if returned."));
	}

	// -------------------------------------------------------------------------- earnings

	private void earnings() {
		String from = """
				rent_credits rc
				JOIN users u ON u.id = rc.user_id
				JOIN holdings h ON h.id = rc.holding_id
				JOIN investment_products p ON p.id = h.product_id
				LEFT JOIN containers c ON c.id = h.container_id
				LEFT JOIN ledger_transactions t ON t.id = rc.ledger_transaction_id""";
		add(report("earnings-register", ReportCategory.EARNINGS, "Earnings register")
			.describe("Every earnings credit: returns and capital credited to the investor's wallet, kept apart.")
			.requires(EARNINGS)
			.from(from)
			.columns(
					date("day", "Day", "rc.day"),
					text("investor", "Investor ID", code("u")).linkTo("user", "userId"),
					id("userId", "u.id"),
					text("name", "Investor", name("u")),
					text("plan", "Plan", "p.code"),
					text("container", "Container", "c.container_number"),
					money("returns", "Returns (earnings)", "rc.rent_amount"),
					money("capital", "Capital returned", "rc.capital_amount"),
					money("credited", "Total credited", "rc.rent_amount + rc.capital_amount"),
					status("status", "Status", "CASE WHEN t.id IS NULL THEN 'NOT_IN_LEDGER' ELSE 'CREDITED' END"),
					dateTime("creditedAt", "Credited at", "rc.created_at"))
			.periodByDay("rc.day", "Day")
			.choice("status", "Status", "CASE WHEN t.id IS NULL THEN 'NOT_IN_LEDGER' ELSE 'CREDITED' END",
					List.of("CREDITED", "NOT_IN_LEDGER"))
			.search(code("u"), name("u"), "p.code", "c.container_number")
			.amount("rc.rent_amount")
			.currency("rc.currency")
			.sort("day", true)
			.note("Earnings are credited to the investor's wallet as they fall due (payable from then); they are paid out "
					+ "when the investor withdraws (see Withdrawal reports). Capital returned is not earnings."));

		add(report("earnings-monthly", ReportCategory.EARNINGS, "Monthly earnings")
			.describe("Returns and capital credited per month.")
			.requires(AGGREGATE)
			.from("rent_credits rc")
			.groupBy("(date_trunc('month', rc.day))::date, rc.currency")
			.columns(
					month("month", "Month", "(date_trunc('month', rc.day))::date"),
					number("credits", "Credits", "count(*)").summed(),
					number("investors", "Investors", "count(DISTINCT rc.user_id)"),
					money("returns", "Returns (earnings)", "sum(rc.rent_amount)"),
					money("capital", "Capital returned", "sum(rc.capital_amount)"),
					money("credited", "Total credited", "sum(rc.rent_amount + rc.capital_amount)"))
			.periodByDay("rc.day", "Day")
			.currency("rc.currency")
			.sort("month", true));

		add(report("earnings-by-plan", ReportCategory.EARNINGS, "Earnings by plan")
			.describe("Returns and capital credited per plan.")
			.requires(AGGREGATE)
			.from("rent_credits rc JOIN holdings h ON h.id = rc.holding_id JOIN investment_products p ON p.id = h.product_id")
			.groupBy("p.code, p.title, rc.currency")
			.columns(
					text("plan", "Plan", "p.code"),
					text("title", "Name", "p.title"),
					number("investors", "Investors", "count(DISTINCT rc.user_id)"),
					money("returns", "Returns (earnings)", "sum(rc.rent_amount)"),
					money("capital", "Capital returned", "sum(rc.capital_amount)"),
					money("credited", "Total credited", "sum(rc.rent_amount + rc.capital_amount)"))
			.periodByDay("rc.day", "Day")
			.search("p.code", "p.title")
			.currency("rc.currency")
			.sort("returns", true));

		add(report("earnings-by-investor", ReportCategory.EARNINGS, "Earnings by investor")
			.describe("Returns and capital credited per investor.")
			.requires(EARNINGS)
			.from("rent_credits rc JOIN users u ON u.id = rc.user_id")
			.groupBy("u.id, u.user_number, u.first_name, u.last_name, rc.currency")
			.columns(
					text("investor", "Investor ID", code("u")).linkTo("user", "userId"),
					id("userId", "u.id"),
					text("name", "Investor", name("u")),
					number("credits", "Credits", "count(*)").summed(),
					money("returns", "Returns (earnings)", "sum(rc.rent_amount)"),
					money("capital", "Capital returned", "sum(rc.capital_amount)"),
					money("credited", "Total credited", "sum(rc.rent_amount + rc.capital_amount)"))
			.periodByDay("rc.day", "Day")
			.search(code("u"), name("u"))
			.currency("rc.currency")
			.sort("returns", true));

		add(report("earnings-schedule", ReportCategory.EARNINGS, "Earnings schedule & forecast")
			.describe("Running investments: returns and capital credited so far and still to come under the contract.")
			.requires(EARNINGS)
			.from("""
					rent_accruals ra
					JOIN holdings h ON h.id = ra.holding_id
					JOIN users u ON u.id = ra.user_id
					JOIN investment_products p ON p.id = ra.product_id
					LEFT JOIN containers c ON c.id = h.container_id""")
			.where("ra.completed_at IS NULL")
			.columns(
					text("investor", "Investor ID", code("u")).linkTo("user", "userId"),
					id("userId", "u.id"),
					text("name", "Investor", name("u")),
					text("plan", "Plan", "p.code"),
					text("container", "Container", "c.container_number"),
					dateTime("startsAt", "Starts", "ra.starts_at"),
					dateTime("endsAt", "Ends", "ra.ends_at"),
					money("monthly", "Monthly return", "ra.monthly_rent"),
					money("returnsCredited", "Returns credited", "ra.credited_rent"),
					money("returnsRemaining", "Returns to come", "GREATEST(ra.monthly_rent * ra.tenure_months - ra.credited_rent, 0)"),
					money("capitalCredited", "Capital returned", "ra.credited_capital"),
					money("capitalRemaining", "Capital to come", "GREATEST(ra.capital_total - ra.credited_capital, 0)"),
					dateTime("creditedUntil", "Credited until", "ra.credited_until"))
			.search(code("u"), name("u"), "p.code", "c.container_number")
			.amount("ra.monthly_rent")
			.currency("ra.currency")
			.sort("endsAt", false)
			.note("Amounts to come are contractual: the monthly return over the full term, and the capital, less what has "
					+ "been credited. Nothing here is paid until it falls due."));
	}

	// -------------------------------------------------------------------------- payments

	private void payments() {
		String reference = "coalesce(p.receipt_number, p.deposit_reference, p.provider_reference, p.external_reference)";
		String reconciliation = """
				CASE WHEN p.status <> 'SUCCEEDED' THEN 'NOT_APPLICABLE'
				     WHEN o.status <> 'CONFIRMED' OR p.amount <> o.total_amount THEN 'MISMATCH'
				     WHEN NOT EXISTS (SELECT 1 FROM invoices i WHERE i.order_id = p.order_id) THEN 'NO_INVOICE'
				     ELSE 'MATCHED' END""";
		add(report("payment-register", ReportCategory.PAYMENT, "Payment register")
			.describe("Every payment towards an order: method, reference, settlement and reconciliation status.")
			.requires(PAYMENTS)
			.from("payments p JOIN orders o ON o.id = p.order_id JOIN users u ON u.id = p.user_id")
			.columns(
					text("reference", "Reference", reference),
					text("order", "Order", "o.order_number").linkTo("order", "orderId"),
					id("orderId", "o.id"),
					text("investor", "Investor ID", code("u")).linkTo("user", "userId"),
					id("userId", "u.id"),
					text("name", "Investor", name("u")),
					status("method", "Method", "p.method"),
					money("amount", "Amount", "p.amount"),
					dateTime("createdAt", "Started", "p.created_at"),
					dateTime("settledAt", "Settled", "p.settled_at"),
					status("status", "Status", "p.status"),
					status("reconciliation", "Reconciliation", reconciliation),
					text("failure", "Failure reason", "p.failure_reason"))
			.period("p.created_at", "Started")
			.choice("status", "Status", "p.status", names(PaymentStatus.values()))
			.choice("method", "Method", "p.method", names(PaymentMethod.values()))
			.choice("reconciliation", "Reconciliation", reconciliation,
					List.of("MATCHED", "MISMATCH", "NO_INVOICE", "NOT_APPLICABLE"))
			.search(reference, "o.order_number", code("u"), name("u"))
			.amount("p.amount")
			.currency("p.currency")
			.sort("createdAt", true));

		add(report("refund-register", ReportCategory.PAYMENT, "Refund register")
			.describe("Payments to be refunded or refunded, with reason and reference.")
			.requires(PAYMENTS)
			.from("payments p JOIN orders o ON o.id = p.order_id JOIN users u ON u.id = p.user_id LEFT JOIN users r ON r.id = p.refunded_by")
			.where("p.refunded_at IS NOT NULL OR p.status IN ('REFUND_REQUIRED', 'REFUNDED')")
			.columns(
					text("order", "Order", "o.order_number").linkTo("order", "orderId"),
					id("orderId", "o.id"),
					text("investor", "Investor ID", code("u")).linkTo("user", "userId"),
					id("userId", "u.id"),
					text("name", "Investor", name("u")),
					status("method", "Paid by", "p.method"),
					money("amount", "Amount", "p.amount"),
					status("status", "Status", "p.status"),
					text("reason", "Reason", "p.refund_reason"),
					dateTime("refundedAt", "Refunded", "p.refunded_at"),
					text("refundReference", "Refund reference", "p.refund_reference"),
					text("refundedBy", "Refunded by", name("r")))
			.period("coalesce(p.refunded_at, p.updated_at)", "Refunded")
			.choice("status", "Status", "p.status", List.of("REFUND_REQUIRED", "REFUNDED"))
			.search("o.order_number", code("u"), name("u"), "p.refund_reference")
			.amount("p.amount")
			.currency("p.currency")
			.sort("refundedAt", true));

		add(report("payment-summary", ReportCategory.PAYMENT, "Payments by method and status")
			.describe("Payment counts and amounts per method and status.")
			.requires(AGGREGATE)
			.from("payments p")
			.groupBy("p.method, p.status, p.currency")
			.columns(
					status("method", "Method", "p.method"),
					status("status", "Status", "p.status"),
					number("payments", "Payments", "count(*)").summed(),
					money("amount", "Amount", "sum(p.amount)"))
			.period("p.created_at", "Started")
			.choice("status", "Status", "p.status", names(PaymentStatus.values()))
			.choice("method", "Method", "p.method", names(PaymentMethod.values()))
			.currency("p.currency")
			.sort("amount", true));
	}

	// ----------------------------------------------------------------------- withdrawals

	private static final List<String> OPEN_WITHDRAWALS = List.of("PENDING_APPROVAL", "APPROVED", "BATCHED", "PROCESSING");

	private void withdrawals() {
		String from = """
				withdrawals w
				JOIN users u ON u.id = w.user_id
				LEFT JOIN withdrawal_batches b ON b.id = w.batch_id
				LEFT JOIN users a1 ON a1.id = w.first_approved_by
				LEFT JOIN users a2 ON a2.id = w.second_approved_by""";
		add(report("withdrawal-register", ReportCategory.WITHDRAWAL, "Withdrawal register")
			.describe("Every withdrawal: approvals, payment batch, bank reference and status.")
			.requires(WITHDRAWALS)
			.from(from)
			.columns(
					text("reference", "Withdrawal", "w.reference"),
					text("investor", "Investor ID", code("u")).linkTo("user", "userId"),
					id("userId", "u.id"),
					text("name", "Investor", name("u")),
					dateTime("requestedAt", "Requested", "w.created_at"),
					money("amount", "Amount", "w.amount"),
					status("balance", "From", "w.balance"),
					status("method", "Paid by", "w.payout_method"),
					status("status", "Status", "w.status"),
					text("approvedBy", "Approved by", "concat_ws(', ', " + name("a1") + ", " + name("a2") + ")"),
					text("batch", "Payment batch", "b.reference"),
					text("bank", "Bank", "CASE WHEN w.bank_account_last4 IS NULL THEN NULL ELSE coalesce(w.bank_name, 'Bank') || ' ••' || w.bank_account_last4 END"),
					text("payoutReference", "Bank reference", "w.payout_reference"),
					dateTime("closedAt", "Paid / closed", "w.closed_at"))
			.period("w.created_at", "Requested")
			.choice("status", "Status", "w.status", names(WithdrawalStatus.values()))
			.choice("balance", "From", "w.balance", names(EarningsBalance.values()))
			.choice("method", "Paid by", "w.payout_method", names(WithdrawalPayoutMethod.values()))
			.search("w.reference", code("u"), name("u"), "b.reference", "w.payout_reference")
			.amount("w.amount")
			.currency("w.currency")
			.sort("requestedAt", true));

		String ageDays = "floor(extract(epoch FROM (:now - w.created_at)) / 86400)::int";
		String bucket = "CASE WHEN " + ageDays + " <= 2 THEN '0-2 days' WHEN " + ageDays + " <= 7 THEN '3-7 days' WHEN "
				+ ageDays + " <= 14 THEN '8-14 days' ELSE '15+ days' END";
		add(report("withdrawal-aging", ReportCategory.WITHDRAWAL, "Withdrawal aging")
			.describe("Open withdrawals by how long they have waited.")
			.requires(WITHDRAWALS)
			.from(from)
			.where("w.status IN " + quoted(OPEN_WITHDRAWALS))
			.columns(
					text("reference", "Withdrawal", "w.reference"),
					text("investor", "Investor ID", code("u")).linkTo("user", "userId"),
					id("userId", "u.id"),
					text("name", "Investor", name("u")),
					dateTime("requestedAt", "Requested", "w.created_at"),
					money("amount", "Amount", "w.amount"),
					status("status", "Status", "w.status"),
					number("age", "Age (days)", ageDays).notSummed(),
					text("bucket", "Aging", bucket))
			.choice("status", "Status", "w.status", OPEN_WITHDRAWALS)
			.choice("bucket", "Aging", bucket, List.of("0-2 days", "3-7 days", "8-14 days", "15+ days"))
			.search("w.reference", code("u"), name("u"))
			.amount("w.amount")
			.currency("w.currency")
			.sort("age", true));

		add(report("withdrawal-summary", ReportCategory.WITHDRAWAL, "Withdrawal summary")
			.describe("Withdrawals per status: count and amount.")
			.requires(AGGREGATE)
			.from("withdrawals w")
			.groupBy("w.status, w.currency")
			.columns(
					status("status", "Status", "w.status"),
					number("withdrawals", "Withdrawals", "count(*)").summed(),
					number("investors", "Investors", "count(DISTINCT w.user_id)"),
					money("amount", "Amount", "sum(w.amount)"))
			.period("w.created_at", "Requested")
			.choice("method", "Paid by", "w.payout_method", names(WithdrawalPayoutMethod.values()))
			.currency("w.currency")
			.sort("amount", true));
	}

	// ------------------------------------------------------------------------- referrals

	private void referrals() {
		String from = """
				referral_earnings re
				JOIN users b ON b.id = re.beneficiary_user_id
				JOIN users s ON s.id = re.source_user_id
				LEFT JOIN investment_products p ON p.id = re.product_id""";
		add(report("commission-register", ReportCategory.REFERRAL, "Commission register")
			.describe("Every referral commission: level, referrer, referred investor, eligible amount, rate and commission.")
			.requires(REFERRALS)
			.from(from)
			.columns(
					dateTime("createdAt", "Date", "re.created_at"),
					number("level", "Level", "re.level").notSummed(),
					text("referrer", "Referrer ID", code("b")).linkTo("user", "referrerId"),
					id("referrerId", "b.id"),
					text("referrerName", "Referrer", name("b")),
					text("referred", "Referred ID", code("s")).linkTo("user", "referredId"),
					id("referredId", "s.id"),
					text("referredName", "Referred investor", name("s")),
					text("plan", "Plan", "p.code"),
					status("kind", "Kind", "re.kind"),
					money("base", "Eligible earnings", "re.base_amount"),
					percent("rate", "Rate %", "re.rate_percent"),
					money("commission", "Commission", "re.amount"))
			.period("re.created_at", "Date")
			.choice("level", "Level", "re.level::text", List.of("1", "2", "3", "4"))
			.choice("kind", "Kind", "re.kind", names(ReferralEarningKind.values()))
			.search(code("b"), name("b"), code("s"), name("s"), "p.code")
			.amount("re.amount")
			.currency("re.currency")
			.sort("createdAt", true)
			.note("Rates are those in force when each commission was earned (effective-dated rate versions)."));

		add(report("commission-levels", ReportCategory.REFERRAL, "Level-wise commission")
			.describe("Commissions per referral level.")
			.requires(AGGREGATE)
			.from("referral_earnings re")
			.groupBy("re.level, re.currency")
			.columns(
					number("level", "Level", "re.level").notSummed(),
					number("commissions", "Commissions", "count(*)").summed(),
					number("referrers", "Referrers", "count(DISTINCT re.beneficiary_user_id)"),
					money("base", "Eligible earnings", "sum(re.base_amount)"),
					money("commission", "Commission", "sum(re.amount)"))
			.period("re.created_at", "Date")
			.choice("kind", "Kind", "re.kind", names(ReferralEarningKind.values()))
			.currency("re.currency")
			.sort("level", false));

		add(report("referrer-performance", ReportCategory.REFERRAL, "Referrer performance")
			.describe("Per referrer: investors referred who earned them commission, and the commission.")
			.requires(REFERRALS)
			.from("referral_earnings re JOIN users b ON b.id = re.beneficiary_user_id")
			.groupBy("b.id, b.user_number, b.first_name, b.last_name, re.currency")
			.columns(
					text("referrer", "Referrer ID", code("b")).linkTo("user", "referrerId"),
					id("referrerId", "b.id"),
					text("name", "Referrer", name("b")),
					number("referred", "Investors earning them commission", "count(DISTINCT re.source_user_id)"),
					number("commissions", "Commissions", "count(*)").summed(),
					money("commission", "Commission", "sum(re.amount)"))
			.period("re.created_at", "Date")
			.choice("level", "Level", "re.level::text", List.of("1", "2", "3", "4"))
			.search(code("b"), name("b"))
			.currency("re.currency")
			.sort("commission", true));

		add(report("commission-monthly", ReportCategory.REFERRAL, "Referral income trends")
			.describe("Referral commission per month and level.")
			.requires(AGGREGATE)
			.from("referral_earnings re")
			.groupBy(monthOf("re.created_at") + ", re.currency")
			.columns(
					month("month", "Month", monthOf("re.created_at")),
					money("level1", "Level 1", "coalesce(sum(re.amount) FILTER (WHERE re.level = 1), 0)"),
					money("level2", "Level 2", "coalesce(sum(re.amount) FILTER (WHERE re.level = 2), 0)"),
					money("level3", "Level 3", "coalesce(sum(re.amount) FILTER (WHERE re.level = 3), 0)"),
					money("level4", "Level 4", "coalesce(sum(re.amount) FILTER (WHERE re.level = 4), 0)"),
					money("commission", "Total", "sum(re.amount)"))
			.period("re.created_at", "Date")
			.currency("re.currency")
			.sort("month", true));

		add(report("unpaid-commissions", ReportCategory.REFERRAL, "Unpaid commissions")
			.describe("Referral commission credited and not yet withdrawn, per investor, at the end of the period.")
			.requires(REFERRALS)
			.from("""
					(SELECT a.owner_user_id, a.currency,
					        sum(CASE WHEN e.direction = 'CREDIT' THEN e.amount ELSE -e.amount END) AS balance
					 FROM ledger_accounts a JOIN ledger_entries e ON e.account_id = a.id
					 WHERE a.account_type = 'INVESTOR_REFERRAL_EARNINGS' AND e.created_at < :periodTo
					 GROUP BY a.owner_user_id, a.currency) rb
					JOIN users u ON u.id = rb.owner_user_id""")
			.where("rb.balance <> 0")
			.columns(
					text("investor", "Investor ID", code("u")).linkTo("user", "userId"),
					id("userId", "u.id"),
					text("name", "Investor", name("u")),
					money("balance", "Commission not withdrawn", "rb.balance"))
			.search(code("u"), name("u"))
			.amount("rb.balance")
			.currency("rb.currency")
			.sort("balance", true)
			.note("The referral balance of each investor's ledger account at the end of the period."));
	}

	// ------------------------------------------------------------------------ containers

	private void containers() {
		add(report("container-inventory", ReportCategory.CONTAINER, "Container inventory")
			.describe("Every container with its status, condition, location and current investor.")
			.requires(CONTAINERS)
			.from("""
					containers c
					LEFT JOIN holdings h ON h.container_id = c.id AND h.status IN ('ACTIVE', 'AWAITING_ACCEPTANCE')
					LEFT JOIN users u ON u.id = h.user_id""")
			.columns(
					text("number", "Container", "c.container_number"),
					status("type", "Type", "c.container_type"),
					status("condition", "Condition", "c.condition"),
					status("status", "Status", "c.status"),
					text("location", "Location", "c.current_location"),
					text("country", "Country", "c.location_country"),
					number("year", "Built", "c.manufacture_year").notSummed(),
					money("cost", "Acquisition cost", "c.acquisition_cost"),
					text("investor", "Investor", code("u")).linkTo("user", "userId"),
					id("userId", "u.id"),
					date("leaseEnds", "Term ends", "h.lease_ends_on"),
					text("reason", "Status note", "c.status_reason"))
			.choice("status", "Status", "c.status", names(ContainerStatus.values()))
			.choice("type", "Type", "c.container_type", names(ContainerType.values()))
			.choice("condition", "Condition", "c.condition", names(ContainerCondition.values()))
			.search("c.container_number", "c.current_location", "c.location_country", code("u"))
			.amount("c.acquisition_cost")
			.currency("coalesce(c.acquisition_currency, 'INR')")
			.sort("number", false));

		String util = "round(100.0 * count(*) FILTER (WHERE c.status = 'ON_LEASE') / NULLIF(count(*) FILTER (WHERE c.status <> 'RETIRED'), 0), 1)";
		add(report("container-locations", ReportCategory.CONTAINER, "Location-wise inventory")
			.describe("Containers per location: available, on lease, in maintenance, and utilisation.")
			.requires(AGGREGATE)
			.from("containers c")
			.groupBy("coalesce(c.location_country, '—'), coalesce(c.current_location, '—')")
			.columns(
					text("country", "Country", "coalesce(c.location_country, '—')"),
					text("location", "Location", "coalesce(c.current_location, '—')"),
					number("total", "Containers", "count(*)").summed(),
					number("available", "Available", "count(*) FILTER (WHERE c.status = 'AVAILABLE')").summed(),
					number("reserved", "Reserved", "count(*) FILTER (WHERE c.status = 'RESERVED')").summed(),
					number("leased", "On lease", "count(*) FILTER (WHERE c.status = 'ON_LEASE')").summed(),
					number("maintenance", "Maintenance", "count(*) FILTER (WHERE c.status = 'MAINTENANCE')").summed(),
					number("retired", "Retired", "count(*) FILTER (WHERE c.status = 'RETIRED')").summed(),
					percent("utilisation", "Utilisation %", util))
			.choice("type", "Type", "c.container_type", names(ContainerType.values()))
			.search("c.location_country", "c.current_location")
			.sort("total", true)
			.note("Utilisation: containers on lease as a share of those not retired."));

		add(report("container-utilisation", ReportCategory.CONTAINER, "Container utilisation by type")
			.describe("Containers per type: available, on lease, in maintenance, and utilisation.")
			.requires(AGGREGATE)
			.from("containers c")
			.groupBy("c.container_type")
			.columns(
					status("type", "Type", "c.container_type"),
					number("total", "Containers", "count(*)").summed(),
					number("available", "Available", "count(*) FILTER (WHERE c.status = 'AVAILABLE')").summed(),
					number("leased", "On lease", "count(*) FILTER (WHERE c.status = 'ON_LEASE')").summed(),
					number("maintenance", "Maintenance", "count(*) FILTER (WHERE c.status = 'MAINTENANCE')").summed(),
					percent("utilisation", "Utilisation %", util))
			.sort("total", true)
			.note("Utilisation: containers on lease as a share of those not retired."));

		add(report("container-revenue", ReportCategory.CONTAINER, "Leasing returns by container")
			.describe("Returns credited to investors per container (the leasing income passed on to them).")
			.requires(CONTAINERS)
			.from("rent_credits rc JOIN holdings h ON h.id = rc.holding_id JOIN containers c ON c.id = h.container_id")
			.groupBy("c.id, c.container_number, c.container_type, rc.currency")
			.columns(
					text("number", "Container", "c.container_number"),
					status("type", "Type", "c.container_type"),
					number("credits", "Credits", "count(*)").summed(),
					money("returns", "Returns credited", "sum(rc.rent_amount)"),
					money("capital", "Capital returned", "sum(rc.capital_amount)"))
			.periodByDay("rc.day", "Day")
			.choice("type", "Type", "c.container_type", names(ContainerType.values()))
			.search("c.container_number")
			.currency("rc.currency")
			.sort("returns", true)
			.note("SeaLease records the returns credited to investors; lessee invoices and maintenance costs are not "
					+ "recorded in the platform."));
	}

	// ------------------------------------------------------------------------ operations

	private void operations() {
		add(report("order-register", ReportCategory.OPERATIONAL, "Orders register")
			.describe("Every order: investor, plans, units, amount and status.")
			.requires(ORDERS)
			.from("""
					orders o
					JOIN users u ON u.id = o.user_id
					LEFT JOIN LATERAL (SELECT sum(oi.units) AS units, string_agg(DISTINCT oi.product_code, ', ') AS plans
					                   FROM order_items oi WHERE oi.order_id = o.id) it ON true""")
			.columns(
					text("order", "Order", "o.order_number").linkTo("order", "orderId"),
					id("orderId", "o.id"),
					text("investor", "Investor ID", code("u")).linkTo("user", "userId"),
					id("userId", "u.id"),
					text("name", "Investor", name("u")),
					dateTime("createdAt", "Placed", "o.created_at"),
					text("plans", "Plans", "it.plans"),
					number("units", "Units", "it.units").summed(),
					money("total", "Amount", "o.total_amount"),
					status("status", "Status", "o.status"),
					dateTime("confirmedAt", "Confirmed", "o.confirmed_at"),
					text("closeReason", "Closed because", "o.close_reason"))
			.period("o.created_at", "Placed")
			.choice("status", "Status", "o.status", names(OrderStatus.values()))
			.search("o.order_number", code("u"), name("u"), "it.plans")
			.amount("o.total_amount")
			.currency("o.currency")
			.sort("createdAt", true));

		String fulfilment = """
				CASE WHEN f.investments = 0 THEN 'AWAITING_ASSIGNMENT'
				     WHEN f.assigned < f.investments THEN 'PARTLY_ASSIGNED'
				     WHEN f.accepted < f.agreements THEN 'AWAITING_ACCEPTANCE'
				     ELSE 'FULFILLED' END""";
		add(report("order-fulfilment", ReportCategory.OPERATIONAL, "Order fulfilment")
			.describe("Confirmed orders: containers assigned, agreements issued and accepted.")
			.requires(ORDERS)
			.from("""
					orders o
					JOIN users u ON u.id = o.user_id
					LEFT JOIN LATERAL (SELECT count(h.id) AS investments, count(h.container_id) AS assigned,
					                          count(la.id) AS agreements, count(ac.agreement_id) AS accepted
					                   FROM holdings h
					                   LEFT JOIN lease_agreements la ON la.holding_id = h.id
					                   LEFT JOIN lease_agreement_acceptances ac ON ac.agreement_id = la.id
					                   WHERE h.order_id = o.id) f ON true""")
			.where("o.status = 'CONFIRMED'")
			.columns(
					text("order", "Order", "o.order_number").linkTo("order", "orderId"),
					id("orderId", "o.id"),
					text("investor", "Investor ID", code("u")).linkTo("user", "userId"),
					id("userId", "u.id"),
					dateTime("confirmedAt", "Confirmed", "o.confirmed_at"),
					number("investments", "Investments", "f.investments").summed(),
					number("assigned", "Containers assigned", "f.assigned").summed(),
					number("agreements", "Agreements issued", "f.agreements").summed(),
					number("accepted", "Accepted", "f.accepted").summed(),
					status("fulfilment", "Fulfilment", fulfilment))
			.period("o.confirmed_at", "Confirmed")
			.choice("fulfilment", "Fulfilment", fulfilment,
					List.of("AWAITING_ASSIGNMENT", "PARTLY_ASSIGNED", "AWAITING_ACCEPTANCE", "FULFILLED"))
			.search("o.order_number", code("u"))
			.sort("confirmedAt", true));

		add(report("invoice-register", ReportCategory.OPERATIONAL, "Invoice register")
			.describe("Every invoice issued, with its order and buyer.")
			.requires(ORDERS)
			.from("invoices i JOIN orders o ON o.id = i.order_id LEFT JOIN users u ON u.id = i.user_id")
			.columns(
					text("invoice", "Invoice", "i.invoice_number"),
					text("order", "Order", "o.order_number").linkTo("order", "orderId"),
					id("orderId", "o.id"),
					text("investor", "Investor ID", code("u")).linkTo("user", "userId"),
					id("userId", "u.id"),
					text("buyer", "Buyer", "i.buyer_name"),
					dateTime("issuedAt", "Issued", "i.issued_at"),
					money("total", "Total", "i.total_amount"))
			.period("i.issued_at", "Issued")
			.search("i.invoice_number", "o.order_number", "i.buyer_name", code("u"))
			.amount("i.total_amount")
			.currency("i.currency")
			.sort("issuedAt", true));

		String acceptance = "CASE WHEN ac.agreement_id IS NOT NULL THEN 'ACCEPTED' WHEN la.acceptance_required THEN 'AWAITING_ACCEPTANCE' ELSE 'NOT_REQUIRED' END";
		add(report("agreement-register", ReportCategory.OPERATIONAL, "Agreement register")
			.describe("Every investment agreement: number, investor, container and acceptance.")
			.requires(AGREEMENTS)
			.from("lease_agreements la JOIN users u ON u.id = la.user_id LEFT JOIN lease_agreement_acceptances ac ON ac.agreement_id = la.id")
			.columns(
					text("agreement", "Agreement", "la.agreement_number"),
					text("investor", "Investor ID", code("u")).linkTo("user", "userId"),
					id("userId", "u.id"),
					text("name", "Investor", name("u")),
					text("container", "Container", "la.container_number"),
					dateTime("issuedAt", "Issued", "la.generated_at"),
					status("acceptance", "Acceptance", acceptance),
					dateTime("acceptedAt", "Accepted", "ac.accepted_at"),
					date("termStarts", "Term starts", "ac.lease_starts_on"),
					date("termEnds", "Term ends", "ac.lease_ends_on"))
			.period("la.generated_at", "Issued")
			.choice("acceptance", "Acceptance", acceptance, List.of("ACCEPTED", "AWAITING_ACCEPTANCE", "NOT_REQUIRED"))
			.search("la.agreement_number", code("u"), name("u"), "la.container_number")
			.sort("issuedAt", true));
	}

	// ------------------------------------------------------------------------------- crm

	private void crm() {
		add(report("lead-register", ReportCategory.CRM, "Lead register")
			.describe("Every lead: source, stage, owner, estimated and won amounts.")
			.requires(LEADS)
			.from("leads l LEFT JOIN users o ON o.id = l.owner_id")
			.columns(
					text("reference", "Lead", "l.reference"),
					text("name", "Name", "l.first_name || ' ' || l.last_name"),
					text("email", "Email", "l.email").maskedWithout("LEAD_VIEW").unsortable(),
					text("phone", "Phone", "l.phone").maskedWithout("LEAD_VIEW").unsortable(),
					status("source", "Source", "l.source"),
					status("stage", "Stage", "l.stage"),
					text("owner", "Owner", name("o")),
					money("estimated", "Estimated", "l.estimated_amount"),
					money("won", "Won", "l.won_amount"),
					dateTime("createdAt", "Created", "l.created_at"),
					dateTime("followUp", "Next follow-up", "l.next_follow_up_at"),
					dateTime("closedAt", "Closed", "l.closed_at"))
			.period("l.created_at", "Created")
			.choice("stage", "Stage", "l.stage", names(LeadStage.values()))
			.choice("source", "Source", "l.source", names(LeadSource.values()))
			.search("l.reference", "l.first_name || ' ' || l.last_name", name("o"))
			.amount("l.estimated_amount")
			.currency("coalesce(l.estimated_currency, l.won_currency, 'INR')")
			.sort("createdAt", true));

		String conversion = "round(100.0 * count(*) FILTER (WHERE l.stage = 'WON') / NULLIF(count(*), 0), 1)";
		add(report("lead-sources", ReportCategory.CRM, "Lead source performance")
			.describe("Leads, won, lost and conversion per source.")
			.requires(AGGREGATE)
			.from("leads l")
			.groupBy("l.source")
			.columns(
					status("source", "Source", "l.source"),
					number("leads", "Leads", "count(*)").summed(),
					number("open", "Open", "count(*) FILTER (WHERE l.stage NOT IN ('WON', 'LOST'))").summed(),
					number("won", "Won", "count(*) FILTER (WHERE l.stage = 'WON')").summed(),
					number("lost", "Lost", "count(*) FILTER (WHERE l.stage = 'LOST')").summed(),
					percent("conversion", "Conversion %", conversion),
					money("wonAmount", "Won amount", "coalesce(sum(l.won_amount), 0)"))
			.period("l.created_at", "Created")
			.sort("leads", true)
			.note("Conversion: leads won as a share of all leads from the source in the period."));

		add(report("sales-pipeline", ReportCategory.CRM, "Sales pipeline")
			.describe("Leads and estimated value per stage.")
			.requires(AGGREGATE)
			.from("leads l")
			.groupBy("l.stage")
			.columns(
					status("stage", "Stage", "l.stage"),
					number("leads", "Leads", "count(*)").summed(),
					money("estimated", "Estimated value", "coalesce(sum(l.estimated_amount), 0)"),
					money("won", "Won amount", "coalesce(sum(l.won_amount), 0)"))
			.period("l.created_at", "Created")
			.choice("source", "Source", "l.source", names(LeadSource.values()))
			.sort("leads", true));

		add(report("staff-performance", ReportCategory.CRM, "Staff performance")
			.describe("Leads, wins and conversion per owner.")
			.requires(LEADS)
			.from("leads l JOIN users o ON o.id = l.owner_id")
			.groupBy("o.id, o.first_name, o.last_name")
			.columns(
					text("owner", "Owner", name("o")),
					number("leads", "Leads", "count(*)").summed(),
					number("won", "Won", "count(*) FILTER (WHERE l.stage = 'WON')").summed(),
					number("lost", "Lost", "count(*) FILTER (WHERE l.stage = 'LOST')").summed(),
					percent("conversion", "Conversion %", conversion),
					money("wonAmount", "Won amount", "coalesce(sum(l.won_amount), 0)"))
			.period("l.created_at", "Created")
			.search(name("o"))
			.sort("won", true));
	}

	// -------------------------------------------------------------------------- helpdesk

	private void helpdesk() {
		String sla = """
				CASE WHEN t.first_responded_at IS NULL AND t.first_response_due_at < :now THEN 'BREACHED'
				     WHEN t.first_responded_at IS NULL THEN 'PENDING'
				     WHEN t.first_response_due_at IS NULL OR t.first_responded_at <= t.first_response_due_at THEN 'MET'
				     ELSE 'BREACHED' END""";
		add(report("ticket-register", ReportCategory.HELPDESK, "Ticket register")
			.describe("Every support ticket: category, priority, team, assignee, first-response SLA and resolution time.")
			.requires(TICKETS)
			.from("support_tickets t LEFT JOIN users u ON u.id = t.user_id LEFT JOIN users a ON a.id = t.assignee_id")
			.columns(
					text("reference", "Ticket", "t.reference"),
					text("subject", "Subject", "t.subject"),
					status("category", "Category", "t.category"),
					status("priority", "Priority", "t.priority"),
					status("status", "Status", "t.status"),
					status("team", "Team", "t.team"),
					text("customer", "Customer", code("u")).linkTo("user", "userId"),
					id("userId", "u.id"),
					text("assignee", "Assignee", name("a")),
					dateTime("createdAt", "Opened", "t.created_at"),
					dateTime("respondedAt", "First response", "t.first_responded_at"),
					status("sla", "First-response SLA", sla),
					dateTime("resolvedAt", "Resolved", "t.resolved_at"),
					number("resolutionHours", "Resolution (hours)",
							"round((extract(epoch FROM (t.resolved_at - t.created_at)) / 3600)::numeric, 1)").notSummed())
			.period("t.created_at", "Opened")
			.choice("status", "Status", "t.status", names(TicketStatus.values()))
			.choice("priority", "Priority", "t.priority", names(TicketPriority.values()))
			.choice("category", "Category", "t.category", names(TicketCategory.values()))
			.choice("team", "Team", "t.team", names(SupportTeam.values()))
			.choice("sla", "First-response SLA", sla, List.of("MET", "BREACHED", "PENDING"))
			.search("t.reference", "t.subject", code("u"), name("a"))
			.sort("createdAt", true));

		add(report("ticket-sla", ReportCategory.HELPDESK, "SLA performance")
			.describe("Per category: tickets, resolved, first-response SLA met, average response and resolution times.")
			.requires(AGGREGATE)
			.from("support_tickets t")
			.groupBy("t.category")
			.columns(
					status("category", "Category", "t.category"),
					number("tickets", "Tickets", "count(*)").summed(),
					number("open", "Open", "count(*) FILTER (WHERE t.status NOT IN ('RESOLVED', 'CLOSED'))").summed(),
					number("resolved", "Resolved", "count(*) FILTER (WHERE t.resolved_at IS NOT NULL)").summed(),
					percent("slaMet", "SLA met %", """
							round(100.0 * count(*) FILTER (WHERE t.first_responded_at IS NOT NULL
							      AND (t.first_response_due_at IS NULL OR t.first_responded_at <= t.first_response_due_at))
							      / NULLIF(count(*) FILTER (WHERE t.first_responded_at IS NOT NULL OR t.first_response_due_at < :now), 0), 1)"""),
					number("responseHours", "Avg first response (h)",
							"round((avg(extract(epoch FROM (t.first_responded_at - t.created_at))) / 3600)::numeric, 1)").notSummed(),
					number("resolutionHours", "Avg resolution (h)",
							"round((avg(extract(epoch FROM (t.resolved_at - t.created_at))) / 3600)::numeric, 1)").notSummed())
			.period("t.created_at", "Opened")
			.choice("team", "Team", "t.team", names(SupportTeam.values()))
			.choice("priority", "Priority", "t.priority", names(TicketPriority.values()))
			.sort("tickets", true)
			.note("SLA met: first responses on time as a share of tickets responded to or past their due time."));

		String resolutionSla = """
				CASE WHEN t.resolution_due_at IS NULL THEN 'NONE'
				     WHEN coalesce(t.resolved_at, t.closed_at) IS NULL AND t.resolution_due_at < :now AND t.sla_paused_at IS NULL THEN 'BREACHED'
				     WHEN coalesce(t.resolved_at, t.closed_at) IS NULL THEN 'PENDING'
				     WHEN coalesce(t.resolved_at, t.closed_at) <= t.resolution_due_at THEN 'MET'
				     ELSE 'BREACHED' END""";
		add(report("ticket-agent-performance", ReportCategory.HELPDESK, "Agent performance")
			.describe("Per agent: tickets handled, resolved, SLA breaches, reopens, response and resolution times, and customer rating.")
			.requires(TICKETS)
			.from("support_tickets t JOIN users a ON a.id = t.assignee_id LEFT JOIN support_feedback f ON f.ticket_id = t.id")
			.groupBy("a.id, a.first_name, a.last_name")
			.columns(
					text("agent", "Agent", name("a")),
					number("tickets", "Tickets", "count(*)").summed(),
					number("open", "Open", "count(*) FILTER (WHERE t.status NOT IN ('RESOLVED', 'CLOSED'))").summed(),
					number("resolved", "Resolved", "count(*) FILTER (WHERE t.resolved_at IS NOT NULL)").summed(),
					number("breaches", "SLA breaches", """
							count(*) FILTER (WHERE t.first_response_breached_at IS NOT NULL OR t.next_response_breached_at IS NOT NULL
							      OR t.resolution_breached_at IS NOT NULL)""").summed(),
					number("reopened", "Reopened", "count(*) FILTER (WHERE t.reopen_count > 0)").summed(),
					number("escalated", "Escalated", "count(*) FILTER (WHERE t.escalation_level > 0)").summed(),
					number("responseHours", "Avg first response (h)",
							"round((avg(extract(epoch FROM (t.first_responded_at - t.created_at))) / 3600)::numeric, 1)").notSummed(),
					number("resolutionHours", "Avg resolution (h)",
							"round((avg(extract(epoch FROM (t.resolved_at - t.created_at))) / 3600)::numeric, 1)").notSummed(),
					number("rating", "Avg rating (1-5)", "round(avg(f.rating)::numeric, 2)").notSummed(),
					number("ratings", "Ratings", "count(f.id)").summed())
			.period("t.created_at", "Opened")
			.choice("team", "Team", "t.team", names(SupportTeam.values()))
			.choice("priority", "Priority", "t.priority", names(TicketPriority.values()))
			.choice("category", "Category", "t.category", names(TicketCategory.values()))
			.sort("tickets", true)
			.note("Tickets assigned to each agent now, opened in the period."));

		add(report("ticket-team-performance", ReportCategory.HELPDESK, "Team performance")
			.describe("Per team and priority: tickets, resolution SLA met, breaches, escalations and times.")
			.requires(TICKETS)
			.from("support_tickets t")
			.groupBy("t.team, t.priority")
			.columns(
					status("team", "Team", "t.team"),
					status("priority", "Priority", "t.priority"),
					number("tickets", "Tickets", "count(*)").summed(),
					number("resolved", "Resolved", "count(*) FILTER (WHERE t.resolved_at IS NOT NULL)").summed(),
					percent("resolutionMet", "Resolution SLA met %", """
							round(100.0 * count(*) FILTER (WHERE t.resolution_due_at IS NOT NULL AND coalesce(t.resolved_at, t.closed_at) <= t.resolution_due_at)
							      / NULLIF(count(*) FILTER (WHERE t.resolution_due_at IS NOT NULL
							          AND (coalesce(t.resolved_at, t.closed_at) IS NOT NULL OR t.resolution_due_at < :now)), 0), 1)"""),
					number("breaches", "SLA breaches", """
							count(*) FILTER (WHERE t.first_response_breached_at IS NOT NULL OR t.next_response_breached_at IS NOT NULL
							      OR t.resolution_breached_at IS NOT NULL)""").summed(),
					number("escalated", "Escalated", "count(*) FILTER (WHERE t.escalation_level > 0)").summed(),
					number("resolutionHours", "Avg resolution (h)",
							"round((avg(extract(epoch FROM (t.resolved_at - t.created_at))) / 3600)::numeric, 1)").notSummed())
			.period("t.created_at", "Opened")
			.choice("category", "Category", "t.category", names(TicketCategory.values()))
			.sort("tickets", true));

		add(report("ticket-escalations", ReportCategory.HELPDESK, "Escalations")
			.describe("Every escalation: ticket, level reached, why, who escalated it and the ticket's status now.")
			.requires(TICKETS)
			.from("support_escalations e JOIN support_tickets t ON t.id = e.ticket_id LEFT JOIN users b ON b.id = e.escalated_by")
			.columns(
					text("reference", "Ticket", "t.reference"),
					text("subject", "Subject", "t.subject"),
					number("level", "Level", "e.level").notSummed(),
					number("previousLevel", "From level", "e.previous_level").notSummed(),
					status("trigger", "Trigger", "e.trigger"),
					text("reason", "Reason", "e.reason"),
					text("escalatedBy", "Escalated by", "coalesce(" + name("b") + ", 'Automatic')"),
					status("priority", "Priority", "t.priority"),
					status("status", "Status now", "t.status"),
					dateTime("escalatedAt", "Escalated", "e.created_at"))
			.period("e.created_at", "Escalated")
			.choice("trigger", "Trigger", "e.trigger", List.of("MANUAL", "FIRST_RESPONSE_AT_RISK", "FIRST_RESPONSE_BREACHED",
					"RESOLUTION_BREACHED", "CRITICAL_UNACKNOWLEDGED", "REPEATED_FOLLOW_UPS", "REPEATED_REOPENS", "AUTOMATION"))
			.choice("priority", "Priority", "t.priority", names(TicketPriority.values()))
			.search("t.reference", "t.subject", "e.reason")
			.sort("escalatedAt", true));

		add(report("ticket-aging", ReportCategory.HELPDESK, "Open ticket aging")
			.describe("Tickets not yet resolved: how long they have been open, their SLA state and who has them.")
			.requires(TICKETS)
			.from("support_tickets t LEFT JOIN users u ON u.id = t.user_id LEFT JOIN users a ON a.id = t.assignee_id")
			.where("t.status NOT IN ('RESOLVED', 'CLOSED')")
			.columns(
					text("reference", "Ticket", "t.reference"),
					text("subject", "Subject", "t.subject"),
					status("status", "Status", "t.status"),
					status("priority", "Priority", "t.priority"),
					status("category", "Category", "t.category"),
					text("customer", "Customer", code("u")).linkTo("user", "userId"),
					id("userId", "u.id"),
					text("assignee", "Assignee", "coalesce(" + name("a") + ", 'Unassigned')"),
					number("ageDays", "Age (days)", "round((extract(epoch FROM (CAST(:now AS timestamptz) - t.created_at)) / 86400)::numeric, 1)").notSummed(),
					status("ageBand", "Age band", """
							CASE WHEN t.created_at > CAST(:now AS timestamptz) - interval '1 day' THEN 'UNDER_1_DAY'
							     WHEN t.created_at > CAST(:now AS timestamptz) - interval '3 days' THEN '1_TO_3_DAYS'
							     WHEN t.created_at > CAST(:now AS timestamptz) - interval '7 days' THEN '3_TO_7_DAYS'
							     ELSE 'OVER_7_DAYS' END"""),
					status("resolutionSla", "Resolution SLA", resolutionSla),
					number("escalationLevel", "Escalation level", "t.escalation_level").notSummed(),
					dateTime("createdAt", "Opened", "t.created_at"))
			.period("t.created_at", "Opened")
			.choice("priority", "Priority", "t.priority", names(TicketPriority.values()))
			.choice("category", "Category", "t.category", names(TicketCategory.values()))
			.choice("team", "Team", "t.team", names(SupportTeam.values()))
			.search("t.reference", "t.subject", code("u"), name("a"))
			.sort("ageDays", true));

		add(report("ticket-reopened", ReportCategory.HELPDESK, "Reopened tickets")
			.describe("Tickets customers reopened after they were resolved, with how often.")
			.requires(TICKETS)
			.from("support_tickets t LEFT JOIN users u ON u.id = t.user_id LEFT JOIN users a ON a.id = t.assignee_id")
			.where("t.reopen_count > 0")
			.columns(
					text("reference", "Ticket", "t.reference"),
					text("subject", "Subject", "t.subject"),
					status("category", "Category", "t.category"),
					status("status", "Status", "t.status"),
					number("reopens", "Reopens", "t.reopen_count").summed(),
					text("customer", "Customer", code("u")).linkTo("user", "userId"),
					id("userId", "u.id"),
					text("assignee", "Assignee", "coalesce(" + name("a") + ", 'Unassigned')"),
					dateTime("createdAt", "Opened", "t.created_at"),
					dateTime("lastMessageAt", "Last message", "t.last_message_at"))
			.period("t.created_at", "Opened")
			.choice("category", "Category", "t.category", names(TicketCategory.values()))
			.search("t.reference", "t.subject", code("u"), name("a"))
			.sort("reopens", true));

		add(report("ticket-feedback", ReportCategory.HELPDESK, "Customer satisfaction")
			.describe("Ratings customers gave resolved tickets (1-5), with their comments.")
			.requires(TICKETS)
			.from("support_feedback f JOIN support_tickets t ON t.id = f.ticket_id LEFT JOIN users u ON u.id = t.user_id"
					+ " LEFT JOIN users a ON a.id = t.assignee_id")
			.columns(
					text("reference", "Ticket", "t.reference"),
					text("subject", "Subject", "t.subject"),
					status("category", "Category", "t.category"),
					number("rating", "Rating", "f.rating").notSummed(),
					text("comment", "Comment", "coalesce(f.comment, '')"),
					text("customer", "Customer", code("u")).linkTo("user", "userId"),
					id("userId", "u.id"),
					text("assignee", "Agent", "coalesce(" + name("a") + ", 'Unassigned')"),
					dateTime("ratedAt", "Rated", "f.created_at"))
			.period("f.created_at", "Rated")
			.choice("rating", "Rating", "CAST(f.rating AS varchar)", List.of("1", "2", "3", "4", "5"))
			.choice("category", "Category", "t.category", names(TicketCategory.values()))
			.search("t.reference", "t.subject", "f.comment", name("a"))
			.sort("ratedAt", true));

		add(report("ticket-category-performance", ReportCategory.HELPDESK, "Category performance")
			.describe("Per category and subcategory: volume, resolution SLA, reopens and customer rating.")
			.requires(AGGREGATE)
			.from("support_tickets t LEFT JOIN support_categories s ON s.id = t.subcategory_id LEFT JOIN support_feedback f ON f.ticket_id = t.id")
			.groupBy("t.category, s.name")
			.columns(
					status("category", "Category", "t.category"),
					text("subcategory", "Subcategory", "coalesce(s.name, '(none)')"),
					number("tickets", "Tickets", "count(*)").summed(),
					number("resolved", "Resolved", "count(*) FILTER (WHERE t.resolved_at IS NOT NULL)").summed(),
					percent("resolutionMet", "Resolution SLA met %", """
							round(100.0 * count(*) FILTER (WHERE t.resolution_due_at IS NOT NULL AND coalesce(t.resolved_at, t.closed_at) <= t.resolution_due_at)
							      / NULLIF(count(*) FILTER (WHERE t.resolution_due_at IS NOT NULL
							          AND (coalesce(t.resolved_at, t.closed_at) IS NOT NULL OR t.resolution_due_at < :now)), 0), 1)"""),
					number("reopened", "Reopened", "count(*) FILTER (WHERE t.reopen_count > 0)").summed(),
					number("rating", "Avg rating (1-5)", "round(avg(f.rating)::numeric, 2)").notSummed())
			.period("t.created_at", "Opened")
			.choice("team", "Team", "t.team", names(SupportTeam.values()))
			.sort("tickets", true));
	}

	// ------------------------------------------------------------------------ compliance

	private void compliance() {
		add(report("kyc-register", ReportCategory.COMPLIANCE, "KYC register")
			.describe("Identity verifications: status, submitted and reviewed, reviewer and rejection reason.")
			.requires(KYC)
			.from("kyc_submissions k JOIN users u ON u.id = k.user_id LEFT JOIN users r ON r.id = k.reviewed_by")
			.columns(
					text("investor", "Investor ID", code("u")).linkTo("user", "userId"),
					id("userId", "u.id"),
					text("name", "Legal name", "k.legal_first_name || ' ' || k.legal_last_name"),
					status("status", "Status", "k.status"),
					dateTime("submittedAt", "Submitted", "k.submitted_at"),
					dateTime("reviewedAt", "Reviewed", "k.reviewed_at"),
					text("reviewer", "Reviewer", name("r")),
					text("reason", "Rejection reason", "k.rejection_reason"),
					date("documentExpiry", "Document expiry", "k.document_expiry_date"))
			.period("k.submitted_at", "Submitted")
			.choice("status", "Status", "k.status", names(KycSubmissionStatus.values()))
			.search(code("u"), "k.legal_first_name || ' ' || k.legal_last_name", name("r"))
			.sort("submittedAt", true)
			.note("Identity numbers are never shown in reports."));

		add(report("expired-documents", ReportCategory.COMPLIANCE, "Expired identity documents")
			.describe("Verifications whose identity document has expired (earlier submissions with an expiry date).")
			.requires(KYC)
			.from("kyc_submissions k JOIN users u ON u.id = k.user_id")
			.where("k.document_expiry_date IS NOT NULL AND k.document_expiry_date < CAST(:now AS date)")
			.columns(
					text("investor", "Investor ID", code("u")).linkTo("user", "userId"),
					id("userId", "u.id"),
					text("name", "Legal name", "k.legal_first_name || ' ' || k.legal_last_name"),
					status("documentType", "Document", "k.document_type"),
					date("documentExpiry", "Expired", "k.document_expiry_date"),
					status("status", "Verification", "k.status"))
			.choice("status", "Verification", "k.status", names(KycSubmissionStatus.values()))
			.search(code("u"), "k.legal_first_name || ' ' || k.legal_last_name")
			.sort("documentExpiry", false));

		String auditFrom = "audit_logs l LEFT JOIN users u ON u.id = l.actor_user_id";
		List<String> actions = names(AuditAction.values());
		add(report("audit-trail", ReportCategory.COMPLIANCE, "Audit trail")
			.describe("Every recorded action: who, what, on which record and from where.")
			.requires(AUDIT)
			.from(auditFrom)
			.columns(auditColumns())
			.period("l.created_at", "When")
			.choice("action", "Action", "l.action", actions)
			.search("l.action", "l.entity_type", "l.entity_id", name("u"), code("u"))
			.sort("at", true));

		add(report("sensitive-access", ReportCategory.COMPLIANCE, "Sensitive document access")
			.describe("Views of identity documents, bank proofs and attachments, and exports of finance data and reports.")
			.requires(AUDIT)
			.from(auditFrom)
			.where("l.action IN " + quoted(SENSITIVE_ACCESS))
			.columns(auditColumns())
			.period("l.created_at", "When")
			.choice("action", "Action", "l.action", SENSITIVE_ACCESS)
			.search("l.entity_type", "l.entity_id", name("u"), code("u"))
			.sort("at", true));

		List<String> financialActions = actions.stream()
			.filter(a -> a.startsWith("PAYMENT") || a.startsWith("WITHDRAWAL") || a.startsWith("LEDGER")
					|| a.startsWith("PAYOUT") || a.startsWith("REFUND") || a.contains("ADJUST") || a.startsWith("BANK_ACCOUNT")
					|| a.startsWith("REFERRAL_RATE") || a.startsWith("COMPANY_BANK"))
			.toList();
		add(report("financial-audit-trail", ReportCategory.COMPLIANCE, "Financial audit trail")
			.describe("Recorded actions on payments, refunds, withdrawals, payouts, ledger adjustments and bank accounts.")
			.requires(AUDIT)
			.from(auditFrom)
			.where("l.action IN " + quoted(financialActions))
			.columns(auditColumns())
			.period("l.created_at", "When")
			.choice("action", "Action", "l.action", financialActions)
			.search("l.action", "l.entity_type", "l.entity_id", name("u"), code("u"))
			.sort("at", true));

		add(report("admin-activity", ReportCategory.COMPLIANCE, "Administrative activity")
			.describe("Actions taken by staff accounts.")
			.requires(AUDIT)
			.from(auditFrom)
			.where("l.actor_user_id IS NOT NULL AND NOT " + IS_INVESTOR.formatted("u"))
			.columns(auditColumns())
			.period("l.created_at", "When")
			.choice("action", "Action", "l.action", actions)
			.search("l.action", "l.entity_type", "l.entity_id", name("u"))
			.sort("at", true));
	}

	private static ReportColumn[] auditColumns() {
		return new ReportColumn[] {
				dateTime("at", "When", "l.created_at"),
				text("actor", "Who", "coalesce(" + name("u") + ", 'System')"),
				text("actorCode", "User ID", code("u")).linkTo("user", "actorId"),
				id("actorId", "u.id"),
				status("action", "Action", "l.action"),
				text("entityType", "Record", "l.entity_type"),
				text("entityId", "Record ID", "l.entity_id"),
				text("ip", "IP address", "l.ip_address").maskedWithout("SECURITY_VIEW", "AUDIT_VIEW").unsortable() };
	}

	/** For the catalogue endpoint: definitions grouped by category, in order. */
	public Map<ReportCategory, List<ReportDefinition>> byCategory() {
		Map<ReportCategory, List<ReportDefinition>> grouped = new LinkedHashMap<>();
		for (ReportCategory category : ReportCategory.values()) {
			List<ReportDefinition> inCategory = reports.values().stream().filter(r -> r.category() == category).toList();
			if (!inCategory.isEmpty()) {
				grouped.put(category, inCategory);
			}
		}
		return grouped;
	}

	static Set<String> permissionsUsed() {
		return Set.of("REPORT_VIEW", "REPORT_GENERATE", "REPORT_EXPORT", "FINANCIAL_REPORT_VIEW", "FINANCE_VIEW",
				"LEDGER_VIEW", "INVESTOR_ANALYTICS_VIEW", "INVESTMENT_VIEW", "INVESTOR_VIEW", "PAYOUT_VIEW", "PAYMENT_VIEW",
				"PAYMENT_SEARCH", "WITHDRAWAL_VIEW", "REFERRAL_COMMISSION_VIEW", "REFERRAL_VIEW", "OPERATIONAL_REPORT_VIEW",
				"CONTAINER_VIEW", "ORDER_VIEW", "AGREEMENT_VIEW", "LEAD_VIEW", "SUPPORT_TICKET_VIEW", "SUPPORT_VIEW", "KYC_VIEW",
				"KYC_REVIEW", "AUDIT_VIEW", "COMPLIANCE_CASE_VIEW", "INVESTOR_CONTACT_VIEW", "PHONE_NUMBER_VIEW", "SECURITY_VIEW", "USER_VIEW");
	}

}
