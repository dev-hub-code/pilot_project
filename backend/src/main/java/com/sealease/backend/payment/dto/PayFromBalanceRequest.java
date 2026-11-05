package com.sealease.backend.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Paying an order from the investor's available balance: confirmed with their password, as a withdrawal is. */
public record PayFromBalanceRequest(@NotBlank @Size(max = 200) String password) {

	@Override
	public String toString() {
		return "PayFromBalanceRequest[password=***]";
	}

}
