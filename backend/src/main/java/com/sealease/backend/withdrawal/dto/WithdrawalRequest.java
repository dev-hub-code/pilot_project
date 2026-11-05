package com.sealease.backend.withdrawal.dto;

import com.sealease.backend.ledger.service.EarningsBalance;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * An online investor's request to withdraw from one wallet. Only the amount is taken from the client: charges, tax and
 * what is paid are worked out again by the backend.
 *
 * @param amount        what to take from the wallet, at least the minimum withdrawal
 * @param bankAccountId the verified bank account to pay (bank payouts); the primary one when left out
 * @param password      the investor's password, re-entered to confirm the withdrawal; never logged or stored
 */
public record WithdrawalRequest(
		@NotNull EarningsBalance balance,
		@NotNull @DecimalMin(value = "0.01") @Digits(integer = 13, fraction = 2) BigDecimal amount,
		UUID bankAccountId,
		@NotBlank @Size(max = 200) String password) {

	@Override
	public String toString() {
		return "WithdrawalRequest[balance=" + balance + ", amount=" + amount + ", bankAccountId=" + bankAccountId + "]";
	}

}
