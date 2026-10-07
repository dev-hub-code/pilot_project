package com.sealease.backend.ledger.service;

/**
 * Ledger account types. Stored by name, so constants must never be renamed once released.
 * {@link #normalBalance} is the side that increases the account: balances are reported positive
 * on that side.
 */
public enum AccountType {

	/** Asset: the client-money bank account withdrawals are paid out of. */
	RENTAL_CASH(Direction.DEBIT, false),
	/** Liability: earnings owed to one investor, withdrawable later. */
	INVESTOR_EARNINGS(Direction.CREDIT, true),
	/** Expense: manual corrections credited to (or recovered from) investors. */
	PLATFORM_ADJUSTMENTS(Direction.DEBIT, false),
	/** Expense: referral commissions paid to uplines on their downline's rental income. */
	PLATFORM_REFERRAL_EXPENSE(Direction.DEBIT, false),
	/** Liability: withdrawals requested and not yet paid out or returned; reserved so they cannot be spent twice. */
	WITHDRAWALS_IN_TRANSIT(Direction.CREDIT, false),
	/** Expense: the monthly rent paid to investors on their containers. */
	PLATFORM_RENT_EXPENSE(Direction.DEBIT, false),
	/** Expense: the monthly part of their capital returned to investors. */
	PLATFORM_CAPITAL_RETURNS(Direction.DEBIT, false);

	private final Direction normalBalance;
	private final boolean perInvestor;

	AccountType(Direction normalBalance, boolean perInvestor) {
		this.normalBalance = normalBalance;
		this.perInvestor = perInvestor;
	}

	public Direction normalBalance() {
		return normalBalance;
	}

	public boolean isPerInvestor() {
		return perInvestor;
	}

}
