package com.sealease.backend.bankaccount.dto;

import java.util.UUID;

/**
 * Reviewer view. {@code otherUsersWithSameAccount > 0} means the same bank account is registered
 * by other users - a fraud signal to investigate before verifying. {@code replaces} is the account a change
 * request would retire, so the reviewer sees both.
 */
public record AdminBankAccountResponse(UUID userId, BankAccountResponse account, UUID reviewedBy,
		long otherUsersWithSameAccount, BankAccountResponse replaces) {
}
