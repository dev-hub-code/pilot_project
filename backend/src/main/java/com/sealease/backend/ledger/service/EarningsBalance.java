package com.sealease.backend.ledger.service;

import java.util.Arrays;
import java.util.List;

/**
 * An investor's three earnings balances, each its own ledger account, and how each may be withdrawn:
 * rent on containers paid by bank only to a bank account, rent on containers paid in cash at the office only
 * in cash there, and referral commission either way (the investor chooses). Stored by name.
 */
public enum EarningsBalance {

	/** Rent on containers whose order was paid by bank (and manual adjustments): paid into a bank account. */
	RENT_BANK(AccountType.INVESTOR_EARNINGS),
	/** Rent on containers whose order was paid in cash at the office: collected in cash at the office. */
	RENT_CASH(AccountType.INVESTOR_CASH_EARNINGS),
	/** Referral commission: paid into a bank account or collected in cash, as the investor chooses. */
	REFERRAL(AccountType.INVESTOR_REFERRAL_EARNINGS);

	/** Every per-investor earnings account type. */
	public static final List<AccountType> ACCOUNT_TYPES = Arrays.stream(values()).map(EarningsBalance::account).toList();

	private final AccountType account;

	EarningsBalance(AccountType account) {
		this.account = account;
	}

	public AccountType account() {
		return account;
	}

	public static EarningsBalance of(AccountType account) {
		return Arrays.stream(values()).filter(b -> b.account == account).findFirst()
			.orElseThrow(() -> new IllegalArgumentException(account + " is not an earnings balance"));
	}

}
