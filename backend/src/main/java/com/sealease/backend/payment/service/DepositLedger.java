package com.sealease.backend.payment.service;

import com.sealease.backend.common.money.Money;
import com.sealease.backend.ledger.service.AccountType;
import com.sealease.backend.ledger.service.LedgerService;
import com.sealease.backend.ledger.service.Posting;
import com.sealease.backend.ledger.service.TransactionType;
import com.sealease.backend.payment.entity.Payment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Money received for investments, in the double-entry ledger: received money is held as {@code DEPOSITS_RECEIVED}
 * against what it paid for ({@code INVESTOR_PRINCIPAL}) or what must go back ({@code DEPOSITS_TO_REFUND}). Posted in
 * the payment's transaction, referenced by the payment id, so each payment is posted once.
 */
@Component
class DepositLedger {

	private final LedgerService ledger;

	DepositLedger(LedgerService ledger) {
		this.ledger = ledger;
	}

	/** The money arrived: it paid for the order ({@code accepted}), or it is held to be refunded. */
	@Transactional(propagation = Propagation.MANDATORY)
	void received(Payment payment, Money received, boolean accepted, UUID actorId) {
		Money amount = received != null && received.isPositive() ? received : payment.amount();
		ledger.post(TransactionType.DEPOSIT_RECEIVED, payment.getId().toString(),
				(accepted ? "Investment paid" : "Received, to refund") + " · payment " + payment.getId(), actorId,
				List.of(Posting.debit(AccountType.DEPOSITS_RECEIVED, null, amount),
						Posting.credit(accepted ? AccountType.INVESTOR_PRINCIPAL : AccountType.DEPOSITS_TO_REFUND, null,
								amount)));
	}

	/**
	 * The money held to refund was paid back: the amount posted when it was received. A payment received before
	 * deposits were posted to the ledger has nothing to reverse.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	void refunded(Payment payment, UUID actorId) {
		ledger.postedAmount(TransactionType.DEPOSIT_RECEIVED, payment.getId().toString()).ifPresent(amount ->
				ledger.post(TransactionType.DEPOSIT_REFUNDED, payment.getId().toString(),
						"Refunded · payment " + payment.getId(), actorId,
						List.of(Posting.debit(AccountType.DEPOSITS_TO_REFUND, null, amount),
								Posting.credit(AccountType.DEPOSITS_RECEIVED, null, amount))));
	}

}
