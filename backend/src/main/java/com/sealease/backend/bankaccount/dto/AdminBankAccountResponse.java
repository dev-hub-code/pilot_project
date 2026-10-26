package com.sealease.backend.bankaccount.dto;

import com.sealease.backend.common.api.Assignee;

import java.util.UUID;

/**
 * Reviewer view. {@code otherUsersWithSameAccount > 0} means the same bank account is registered
 * by other users - a fraud signal to investigate before verifying. {@code replaces} is the account a change
 * request would retire, so the reviewer sees both. {@code assignee} is the reviewer the verification is assigned to.
 */
public record AdminBankAccountResponse(UUID userId, BankAccountResponse account, UUID reviewedBy,
		long otherUsersWithSameAccount, BankAccountResponse replaces, Assignee assignee, Ifsc ifsc) {

	/** The account's IFSC, for the reviewer to check against the proof. */
	public record Ifsc(String code) {
	}

}
