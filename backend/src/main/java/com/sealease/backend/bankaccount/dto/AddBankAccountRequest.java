package com.sealease.backend.bankaccount.dto;

import com.sealease.backend.common.validation.IsoCountry;
import com.sealease.backend.common.validation.PlatformCurrency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The account holder is not part of the request: it is always the investor's own registered name, so
 * payouts only go to accounts in their name.
 *
 * @param accountNumber the account number: 8 to 15 digits, nothing else
 * @param routingCode   the branch's IFSC code: 4 letters, a zero, then 6 letters or digits (e.g. HDFC0001234)
 */
public record AddBankAccountRequest(
		@NotBlank @Size(max = 140) String bankName,
		@NotBlank @IsoCountry String country,
		@NotBlank @PlatformCurrency String currency,
		@NotBlank @Pattern(regexp = "^[0-9]{8,15}$", message = "must be 8 to 15 digits, with no spaces or other characters")
		String accountNumber,
		@NotBlank @Pattern(regexp = "^[A-Za-z]{4}0[A-Za-z0-9]{6}$",
				message = "must be an IFSC code: 4 letters, a zero, then 6 letters or digits (e.g. HDFC0001234)")
		String routingCode) {

	@Override
	public String toString() {
		return "AddBankAccountRequest[bankName=" + bankName + ", country=" + country + ", accountNumber=***]";
	}

}
