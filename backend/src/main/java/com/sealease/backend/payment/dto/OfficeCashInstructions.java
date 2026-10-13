package com.sealease.backend.payment.dto;

import com.sealease.backend.common.money.MoneyResponse;

import java.time.Instant;

/**
 * How to pay an order in cash at the office, while the payment is pending.
 *
 * @param reference to quote at the counter
 * @param payBy     the order lapses if unpaid by then
 */
public record OfficeCashInstructions(String reference, MoneyResponse amount, String address, String hours, Instant payBy) {
}
