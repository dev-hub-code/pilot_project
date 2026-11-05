package com.sealease.backend.withdrawal.service;

import com.sealease.backend.bankaccount.entity.BankAccountStatus;
import com.sealease.backend.bankaccount.service.BankAccountService;
import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyProperties;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.earning.dto.EarningsSummary;
import com.sealease.backend.earning.dto.LeaseRentResponse;
import com.sealease.backend.earning.service.PayoutService;
import com.sealease.backend.investment.dto.HoldingResponse;
import com.sealease.backend.investment.entity.HoldingStatus;
import com.sealease.backend.investment.service.HoldingService;
import com.sealease.backend.ledger.service.EarningsBalance;
import com.sealease.backend.referral.dto.ReferralScheme;
import com.sealease.backend.referral.service.ReferralSchemeService;
import com.sealease.backend.withdrawal.dto.WithdrawalOverview;
import com.sealease.backend.withdrawal.dto.WithdrawalSettingsResponse;
import com.sealease.backend.withdrawal.entity.Withdrawal;
import com.sealease.backend.withdrawal.entity.WithdrawalStatus;
import com.sealease.backend.withdrawal.repository.WithdrawalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The investor's withdrawal dashboard: what they invested and earned, what they can withdraw now, what is locked and
 * why, per investment, and the accounts they can be paid into. Read from the ledger (wallets), the investments and
 * their leases; nothing here is a hardcoded rule: what is withdrawable is what the ledger says is in the wallets.
 *
 * <p>Principal is never withdrawable: an investment either earns rent only (its principal stays invested), or, under
 * earlier terms, pays its principal back month by month into the wallet, from where it is withdrawn like any earnings.
 */
@Service
public class WithdrawalOverviewService {

	private final PayoutService payouts;
	private final HoldingService holdings;
	private final BankAccountService bankAccounts;
	private final WithdrawalRepository withdrawals;
	private final WithdrawalSettingsService settings;
	private final ReferralPayoutModeService payoutModes;
	private final ReferralSchemeService schemes;
	private final MoneyProperties money;
	private final Clock clock;

	public WithdrawalOverviewService(PayoutService payouts, HoldingService holdings, BankAccountService bankAccounts,
			WithdrawalRepository withdrawals, WithdrawalSettingsService settings, ReferralPayoutModeService payoutModes,
			ReferralSchemeService schemes, MoneyProperties money, Clock clock) {
		this.payouts = payouts;
		this.holdings = holdings;
		this.bankAccounts = bankAccounts;
		this.withdrawals = withdrawals;
		this.settings = settings;
		this.payoutModes = payoutModes;
		this.schemes = schemes;
		this.money = money;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public WithdrawalOverview overview(UUID userId) {
		Currency currency = money.defaultCurrency();
		String code = currency.getCurrencyCode();
		Money zero = Money.zero(currency);
		EarningsSummary earnings = payouts.summary(userId);
		List<HoldingResponse> investments = holdings.portfolio(userId).holdings().stream()
			.filter(h -> code.equals(h.amount().currency()))
			.toList();
		Map<UUID, LeaseRentResponse> leases = earnings.leases().stream()
			.collect(Collectors.toMap(LeaseRentResponse::holdingId, Function.identity(), (a, b) -> a));

		Money invested = zero;
		Money locked = zero;
		int active = 0;
		List<WithdrawalOverview.Investment> cards = new ArrayList<>();
		for (HoldingResponse h : investments) {
			Money amount = money(h.amount());
			LeaseRentResponse lease = leases.get(h.id());
			Money rent = lease == null ? zero : money(lease.rentCredited());
			Money capitalBack = lease == null ? zero : money(lease.capitalCredited());
			Money stillLocked = amount.minus(capitalBack);
			if (stillLocked.isNegative()) {
				stillLocked = zero;
			}
			invested = invested.plus(amount);
			locked = locked.plus(stillLocked);
			if (h.status() == HoldingStatus.ACTIVE) {
				active++;
			}
			cards.add(new WithdrawalOverview.Investment(h.id(), h.productCode(), h.productTitle(), h.status(),
					h.amount(), MoneyResponse.from(rent.plus(capitalBack)), MoneyResponse.from(stillLocked),
					lockReason(h, stillLocked), h.leaseStartsOn(), h.leaseEndsOn(), nextUnlock(h, stillLocked)));
		}

		Money earned = sum(earnings.rentPaid(), code, zero).plus(sum(earnings.capitalReturned(), code, zero))
			.plus(sum(earnings.referralEarned(), code, zero));
		Map<EarningsBalance, Withdrawal> open = withdrawals.findByUserIdAndStatusIn(userId, WithdrawalStatus.OPEN).stream()
			.filter(w -> code.equals(w.getCurrency()))
			.collect(Collectors.toMap(Withdrawal::getBalance, Function.identity(), (a, b) -> a));
		List<WithdrawalOverview.Wallet> wallets = new ArrayList<>();
		Money available = zero;
		for (EarningsBalance balance : EarningsBalance.values()) {
			Money in = earnings.wallet().stream()
				.filter(w -> w.balance() == balance && code.equals(w.amount().currency()))
				.map(w -> WithdrawalRunService.whole(money(w.amount())))
				.reduce(zero, Money::plus);
			available = available.plus(in);
			Withdrawal pending = open.get(balance);
			wallets.add(new WithdrawalOverview.Wallet(balance, MoneyResponse.from(in),
					pending == null ? null : pending.getId(), pending == null ? null : pending.getReference()));
		}

		WithdrawalSettingsResponse s = settings.get();
		List<WithdrawalOverview.BankAccount> accounts = bankAccounts.listFor(userId).stream()
			.filter(a -> a.status() == BankAccountStatus.VERIFIED && code.equals(a.currency()))
			.map(a -> new WithdrawalOverview.BankAccount(a.id(), a.accountHolderName(), a.bankName(),
					a.accountNumberMasked(), a.primary(), a.status()))
			.toList();
		return new WithdrawalOverview(code, MoneyResponse.from(invested), MoneyResponse.from(earned),
				MoneyResponse.from(available), MoneyResponse.from(locked),
				MoneyResponse.from(sum(earnings.withdrawalInProgress(), code, zero)),
				MoneyResponse.from(sum(earnings.totalWithdrawn(), code, zero)), active,
				schemes.schemeOf(userId) == ReferralScheme.ONLINE, payoutModes.current(userId).mode(),
				MoneyResponse.from(Money.of(s.minimumAmount(), currency)), s.feeFlat(), s.feePercent(), s.taxPercent(),
				s.processingDays(), wallets, cards, accounts);
	}

	private static String lockReason(HoldingResponse h, Money stillLocked) {
		if (!stillLocked.isPositive()) {
			return "Nothing locked: the principal has been paid back into your wallet";
		}
		if (h.status() == HoldingStatus.AWAITING_ACCEPTANCE) {
			return "The term starts once you accept the investment agreement; returns are credited from then";
		}
		if (h.monthlyCapitalReturn() != null && new BigDecimal(h.monthlyCapitalReturn().amount()).signum() > 0) {
			return "Paid back into your wallet month by month over the term, with the rent";
		}
		return "Stays invested under the plan's terms: you earn rent on it, credited to your wallet";
	}

	/** The first of next month while principal is still being paid back, under plans that pay it back. */
	private LocalDate nextUnlock(HoldingResponse h, Money stillLocked) {
		if (!stillLocked.isPositive() || h.status() != HoldingStatus.ACTIVE || h.monthlyCapitalReturn() == null
				|| new BigDecimal(h.monthlyCapitalReturn().amount()).signum() <= 0) {
			return null;
		}
		LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
		LocalDate next = today.withDayOfMonth(1).plusMonths(1);
		return h.leaseEndsOn() != null && next.isAfter(h.leaseEndsOn()) ? h.leaseEndsOn() : next;
	}

	private static Money money(MoneyResponse m) {
		return Money.of(new BigDecimal(m.amount()), Currency.getInstance(m.currency()));
	}

	private static Money sum(Collection<MoneyResponse> amounts, String code, Money zero) {
		return amounts.stream().filter(m -> code.equals(m.currency())).map(WithdrawalOverviewService::money)
			.reduce(zero, Money::plus);
	}

}
