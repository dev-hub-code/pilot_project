package com.sealease.backend.withdrawal.service;

import com.sealease.backend.audit.AuditAction;
import com.sealease.backend.audit.AuditRecord;
import com.sealease.backend.audit.AuditService;
import com.sealease.backend.bankaccount.dto.PayoutAccount;
import com.sealease.backend.bankaccount.service.BankAccountService;
import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.exception.ResourceNotFoundException;
import com.sealease.backend.common.money.Money;
import com.sealease.backend.common.money.MoneyProperties;
import com.sealease.backend.common.money.MoneyResponse;
import com.sealease.backend.kafka.KafkaTopics;
import com.sealease.backend.ledger.service.AccountType;
import com.sealease.backend.ledger.service.EarningsBalance;
import com.sealease.backend.ledger.service.LedgerService;
import com.sealease.backend.ledger.service.Posting;
import com.sealease.backend.ledger.service.TransactionType;
import com.sealease.backend.referral.dto.ReferralScheme;
import com.sealease.backend.referral.service.ReferralSchemeService;
import com.sealease.backend.user.service.UserAccountService;
import com.sealease.backend.withdrawal.dto.WithdrawalQuoteResponse;
import com.sealease.backend.withdrawal.dto.WithdrawalRequest;
import com.sealease.backend.withdrawal.dto.WithdrawalResponse;
import com.sealease.backend.withdrawal.entity.Withdrawal;
import com.sealease.backend.withdrawal.entity.WithdrawalPayoutMethod;
import com.sealease.backend.withdrawal.entity.WithdrawalStatus;
import com.sealease.backend.withdrawal.repository.WithdrawalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * An online investor's own withdrawals: a quote for an amount, the request itself (confirmed with their password), and
 * cancelling it or answering a reviewer's question while it is being reviewed.
 *
 * <p>On submission the amount is taken from the wallet into "withdrawals in transit" in the same transaction as the
 * withdrawal is saved, under a lock on the wallet, so two requests can never spend the same money and a wallet never
 * goes below zero. Charges and tax are worked out here from the settings in force and kept on the withdrawal; they come
 * out of the amount when it is paid. A retried request with the same {@code Idempotency-Key} returns the same withdrawal.
 */
@Service
public class WithdrawalRequestService {

	private final WithdrawalRepository withdrawals;
	private final WithdrawalService withdrawalService;
	private final WithdrawalRunService runs;
	private final WithdrawalSettingsService settings;
	private final WithdrawalHistory history;
	private final ReferralPayoutModeService payoutModes;
	private final ReferralSchemeService schemes;
	private final BankAccountService bankAccounts;
	private final UserAccountService accounts;
	private final LedgerService ledger;
	private final AuditService audit;
	private final MoneyProperties money;
	private final Clock clock;

	public WithdrawalRequestService(WithdrawalRepository withdrawals, WithdrawalService withdrawalService,
			WithdrawalRunService runs, WithdrawalSettingsService settings, WithdrawalHistory history,
			ReferralPayoutModeService payoutModes, ReferralSchemeService schemes, BankAccountService bankAccounts,
			UserAccountService accounts, LedgerService ledger, AuditService audit, MoneyProperties money, Clock clock) {
		this.withdrawals = withdrawals;
		this.withdrawalService = withdrawalService;
		this.runs = runs;
		this.settings = settings;
		this.history = history;
		this.payoutModes = payoutModes;
		this.schemes = schemes;
		this.bankAccounts = bankAccounts;
		this.accounts = accounts;
		this.ledger = ledger;
		this.audit = audit;
		this.money = money;
		this.clock = clock;
	}

	/** What withdrawing {@code amount} from {@code balance} would pay now. Changes nothing. */
	@Transactional(readOnly = true)
	public WithdrawalQuoteResponse quote(UUID userId, EarningsBalance balance, BigDecimal amount) {
		Currency currency = money.defaultCurrency();
		Money available = WithdrawalRunService.whole(ledger.investorBalance(userId, currency, balance));
		WithdrawalCalculator.Quote q = WithdrawalCalculator.quote(available, amount,
				Money.of(settings.minimumAmount(), currency), settings.charges());
		String problem = q.problem();
		if (problem == null && withdrawals.existsByUserIdAndCurrencyAndBalanceAndStatusIn(userId,
				currency.getCurrencyCode(), balance, WithdrawalStatus.OPEN)) {
			problem = "A withdrawal from this wallet is already in progress";
		}
		return new WithdrawalQuoteResponse(balance, payoutModes.current(userId).mode(), MoneyResponse.from(q.available()),
				MoneyResponse.from(q.amount()), MoneyResponse.from(q.fee()), MoneyResponse.from(q.tax()),
				MoneyResponse.from(q.net()), MoneyResponse.from(q.remaining()), MoneyResponse.from(q.minimum()),
				problem == null, problem);
	}

	/**
	 * Submits a request for review. The password is checked first (a wrong one counts towards the sign-in lockout);
	 * everything else is decided under the wallet's lock.
	 */
	@Transactional
	public WithdrawalResponse submit(UUID userId, WithdrawalRequest request, String idempotencyKey) {
		Optional<Withdrawal> earlier = withdrawals.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
		if (earlier.isPresent()) {
			return WithdrawalResponse.forInvestor(earlier.get());
		}
		if (schemes.schemeOf(userId) != ReferralScheme.ONLINE) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"Network investors are paid when our team makes a withdrawal");
		}
		accounts.confirmPassword(userId, request.password());

		Currency currency = money.defaultCurrency();
		EarningsBalance balance = request.balance();
		WithdrawalPayoutMethod method = payoutModes.current(userId).mode();
		Optional<PayoutAccount> account = method == WithdrawalPayoutMethod.BANK_TRANSFER
				? payoutAccount(userId, request.bankAccountId())
				: Optional.empty();
		// Serialises requests and runs on this wallet; everything below is decided under the lock.
		Money available = WithdrawalRunService.whole(ledger.lockInvestorBalance(userId, currency, balance));
		earlier = withdrawals.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
		if (earlier.isPresent()) {
			return WithdrawalResponse.forInvestor(earlier.get());
		}
		String reason = runs.ineligible(userId, method, account, currency);
		if (reason != null) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, reason);
		}
		if (withdrawals.existsByUserIdAndCurrencyAndBalanceAndStatusIn(userId, currency.getCurrencyCode(), balance,
				WithdrawalStatus.OPEN)) {
			throw new BusinessException(ErrorCode.CONFLICT, "A withdrawal from this wallet is already in progress");
		}
		WithdrawalCalculator.Quote quote = WithdrawalCalculator.quote(available, request.amount(),
				Money.of(settings.minimumAmount(), currency), settings.charges());
		if (!quote.allowed()) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, quote.problem());
		}
		Money amount = quote.amount();
		int requiredApprovals = amount.amount().compareTo(settings.dualApprovalThreshold()) > 0 ? 2 : 1;
		Instant now = clock.instant();
		String reference = "WD-" + withdrawals.nextNumber();
		Withdrawal withdrawal = withdrawals.saveAndFlush(method == WithdrawalPayoutMethod.BANK_TRANSFER
				? Withdrawal.requested(reference, userId, amount, quote.fee(), quote.tax(), idempotencyKey, balance,
						account.orElseThrow().id(), account.get().holderName(), account.get().bankName(),
						account.get().last4(), requiredApprovals, now)
				: Withdrawal.requestedCash(reference, userId, amount, quote.fee(), quote.tax(), idempotencyKey, balance,
						requiredApprovals, now));
		ledger.post(TransactionType.WITHDRAWAL_RESERVE, withdrawal.getId().toString(),
				(balance == EarningsBalance.REFERRAL ? "Referral" : "Returns") + " withdrawal " + reference
						+ " requested by the investor",
				userId, List.of(Posting.debit(balance.account(), userId, amount),
						Posting.credit(AccountType.WITHDRAWALS_IN_TRANSIT, null, amount)));
		audit.record(AuditRecord.of(userId, AuditAction.WITHDRAWAL_REQUESTED, WithdrawalService.ENTITY, withdrawal.getId())
			.withNewValue(Map.of("reference", reference, "amount", amount.toString(), "fee", quote.fee().toString(),
					"tax", quote.tax().toString(), "net", quote.net().toString(), "balance", balance,
					"payoutMethod", method, "requiredApprovals", requiredApprovals)));
		history.record(withdrawal, null, userId, null, true, WithdrawalHistory.Notice.SUBMITTED);
		withdrawalService.publish(KafkaTopics.WITHDRAWAL_REQUESTED, "WithdrawalRequested", withdrawal, Map.of());
		return WithdrawalResponse.forInvestor(withdrawal);
	}

	/** The investor withdraws their request while it is still being reviewed; the amount goes back to the wallet. */
	@Transactional
	public WithdrawalResponse cancel(UUID userId, UUID withdrawalId) {
		Withdrawal withdrawal = own(userId, withdrawalId);
		if (!WithdrawalStatus.IN_REVIEW.contains(withdrawal.getStatus())) {
			throw new BusinessException(ErrorCode.CONFLICT,
					"Only a withdrawal still being reviewed can be cancelled; this one is " + withdrawal.getStatus());
		}
		WithdrawalStatus before = withdrawal.cancel(clock.instant());
		withdrawal.releaseReview();
		withdrawals.flush();
		withdrawalService.release(withdrawal, userId, "cancelled by the investor");
		audit.record(AuditRecord.of(userId, AuditAction.WITHDRAWAL_CANCELLED, WithdrawalService.ENTITY, withdrawalId)
			.withOldValue(Map.of("status", before)).withNewValue(Map.of("status", withdrawal.getStatus())));
		history.record(withdrawal, before, userId, "Cancelled by you", true, WithdrawalHistory.Notice.CANCELLED);
		return WithdrawalResponse.forInvestor(withdrawal);
	}

	/** The investor answers a request for more information: the withdrawal goes back to its reviewer. */
	@Transactional
	public WithdrawalResponse respond(UUID userId, UUID withdrawalId, String message) {
		Withdrawal withdrawal = own(userId, withdrawalId);
		if (withdrawal.getStatus() != WithdrawalStatus.ON_HOLD || !withdrawal.isInfoRequested()) {
			throw new BusinessException(ErrorCode.CONFLICT, "No information has been asked for on this withdrawal");
		}
		String text = message.strip();
		WithdrawalStatus before = withdrawal.resume(clock.instant());
		withdrawals.flush();
		audit.record(AuditRecord.of(userId, AuditAction.WITHDRAWAL_INFO_PROVIDED, WithdrawalService.ENTITY, withdrawalId)
			.withNewValue(Map.of("message", text)));
		history.record(withdrawal, before, userId, "Your reply: " + text, true, null);
		return WithdrawalResponse.forInvestor(withdrawal);
	}

	/** One of the investor's own withdrawals, locked. Someone else's is "not found", not "forbidden". */
	private Withdrawal own(UUID userId, UUID withdrawalId) {
		return withdrawals.findByIdForUpdate(withdrawalId).filter(w -> w.getUserId().equals(userId))
			.orElseThrow(() -> new ResourceNotFoundException("Withdrawal", withdrawalId));
	}

	/** The verified account to pay: the one chosen, which must be the investor's own, or their primary one. */
	private Optional<PayoutAccount> payoutAccount(UUID userId, UUID chosen) {
		if (chosen == null) {
			return bankAccounts.verifiedPrimaryAccount(userId).flatMap(a -> bankAccounts.payoutAccount(a.id()));
		}
		PayoutAccount account = bankAccounts.payoutAccount(chosen).filter(a -> a.userId().equals(userId))
			.orElseThrow(() -> new ResourceNotFoundException("Bank account", chosen));
		if (!account.isVerified()) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "This bank account is not verified yet");
		}
		return Optional.of(account);
	}

}
