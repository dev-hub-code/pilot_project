package com.sealease.backend.payment.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * How online payments run, as an admin sets them. Every value is required: the form shows them all.
 *
 * @param onlinePayments           online investors pay through Razorpay (checkout and bank transfer); off, they pay by
 *                                 bank transfer to a company account or from their balance
 * @param virtualAccounts          online investors may pay by bank transfer into a Razorpay virtual account (needs
 *                                 Smart Collect activated on the Razorpay account)
 * @param maxAmount                the most one checkout payment can be, in rupees; 0 for no limit
 * @param checkoutWindowMinutes    how long an order stays reserved once the investor opens the checkout
 * @param virtualAccountWindowHours how long an order stays reserved, and its virtual account open, for a bank transfer
 */
public record PaymentGatewaySettingsRequest(
		@NotNull Boolean onlinePayments,
		@NotNull Boolean virtualAccounts,
		@NotNull @DecimalMin("0") @DecimalMax("1000000000") @Digits(integer = 10, fraction = 2) BigDecimal maxAmount,
		@NotNull @Min(5) @Max(1440) Integer checkoutWindowMinutes,
		@NotNull @Min(1) @Max(720) Integer virtualAccountWindowHours) {
}
