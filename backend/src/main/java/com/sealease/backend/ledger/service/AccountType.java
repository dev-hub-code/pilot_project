package com.sealease.backend.ledger.service;

/**
 * Ledger account types. Stored by name, so constants must never be renamed once released.
 * {@link #normalBalance} is the side that increases the account: balances are reported positive
 * on that side.
 */
public enum AccountType {

	/** Asset: lessee rent received into the client-money account. */
	RENTAL_CASH(Direction.DEBIT, false),
	/** Liability: earnings owed to one investor, withdrawable later. */
	INVESTOR_EARNINGS(Direction.CREDIT, true),
	/** Income: management fees deducted from rental. */
	PLATFORM_FEE_REVENUE(Direction.CREDIT, false),
	/** Income: rental on the unsold share of an offering, and rounding left over from distributions. */
	PLATFORM_RETAINED(Direction.CREDIT, false),
	/** Expense: manual corrections credited to (or recovered from) investors. */
	PLATFORM_ADJUSTMENTS(Direction.DEBIT, false),
	/** Expense: referral commissions paid to uplines on their downline's rental income. */
	PLATFORM_REFERRAL_EXPENSE(Direction.DEBIT, false);

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
