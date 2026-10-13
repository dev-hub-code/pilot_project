package com.sealease.backend.withdrawal.dto;

import com.sealease.backend.ledger.service.EarningsBalance;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Cash withdrawn at the office: the investor and which balance (RENT_CASH or REFERRAL); the whole balance is paid. */
public record OfficeWithdrawalRequest(@NotNull UUID userId, @NotNull EarningsBalance balance) {
}
