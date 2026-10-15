package com.sealease.backend.withdrawal.dto;

import java.math.BigDecimal;

/**
 * How investors are paid, shown on their Withdrawals page. Withdrawals are made in runs our team starts: each run
 * withdraws every balance at least {@code minimumAmount} into a bank sheet or a cash sheet.
 *
 * @param minimumAmount a run withdraws an investor's balances only when they come to at least this
 * @param officeAddress where cash is collected; {@code officeHours} when
 */
public record WithdrawalPolicy(BigDecimal minimumAmount, String officeAddress, String officeHours) {
}
