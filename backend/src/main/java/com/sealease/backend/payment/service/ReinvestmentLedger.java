package com.sealease.backend.payment.service;

import com.sealease.backend.common.exception.BusinessException;
import com.sealease.backend.common.exception.ErrorCode;
import com.sealease.backend.common.money.Money;
import com.sealease.backend.ledger.service.AccountType;
import com.sealease.backend.ledger.service.EarningsBalance;
import com.sealease.backend.ledger.service.LedgerService;
import com.sealease.backend.ledger.service.Posting;
import com.sealease.backend.ledger.service.TransactionType;
import com.sealease.backend.payment.entity.Payment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

/**
 * Paying for an investment from the investor's available balance, in the double-entry ledger: their earnings accounts
 * are debited and {@code INVESTOR_PRINCIPAL} credited, as one {@code REINVESTMENT} transaction referenced by the
 * payment id (so a payment is posted once). No money enters or leaves the company: what it owed the investor as
 * earnings it now holds as their invested capital.
 */
@Component
class ReinvestmentLedger {

	/**
	 * The balances that can pay, spent in this order: returns, then referral commission. Both are withdrawn to a bank
	 * account. Returns on units paid in cash at the office are collected in cash there, so they do not pay online.
	 */
	static final List<EarningsBalance> SPENDABLE = List.of(EarningsBalance.RENT_BANK, EarningsBalance.REFERRAL);

	private final LedgerService ledger;

	ReinvestmentLedger(LedgerService ledger) {
		this.ledger = ledger;
	}

	/** What the investor can reinvest now; for display only (it is checked again under the lock when spent). */
	@Transactional(readOnly = true)
	Money available(UUID userId, Currency currency) {
		Money total = Money.zero(currency);
		for (EarningsBalance balance : SPENDABLE) {
			total = total.plus(ledger.investorBalance(userId, currency, balance));
		}
		return total;
	}

	/**
	 * Spends the payment's amount from the investor's balances. Each balance is locked first, always in the same order,
	 * so this waits for (and never overlaps) a withdrawal or adjustment taking money from the same wallet; the amount is
	 * checked against the locked balances. Refused, changing nothing, when they do not cover it.
	 *
	 * @return the ledger transaction id
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	UUID spend(Payment payment, String orderNumber) {
		UUID userId = payment.getUserId();
		Money due = payment.amount();
		Currency currency = due.currency();
		List<Money> locked = new ArrayList<>();
		Money available = Money.zero(currency);
		for (EarningsBalance balance : SPENDABLE) {
			Money held = ledger.lockInvestorBalance(userId, currency, balance);
			Money usable = held.isPositive() ? held : Money.zero(currency);
			locked.add(usable);
			available = available.plus(usable);
		}
		if (available.isLessThan(due)) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Your available balance (" + available.display()
					+ ") does not cover this order (" + due.display() + ")");
		}

		List<Posting> postings = new ArrayList<>();
		Money remaining = due;
		for (int i = 0; i < SPENDABLE.size() && remaining.isPositive(); i++) {
			Money take = locked.get(i).isLessThan(remaining) ? locked.get(i) : remaining;
			if (take.isPositive()) {
				postings.add(Posting.debit(SPENDABLE.get(i).account(), userId, take));
				remaining = remaining.minus(take);
			}
		}
		postings.add(Posting.credit(AccountType.INVESTOR_PRINCIPAL, null, due));
		return ledger.post(TransactionType.REINVESTMENT, payment.getId().toString(),
				"Reinvested from available balance · order " + orderNumber, userId, postings);
	}

}
