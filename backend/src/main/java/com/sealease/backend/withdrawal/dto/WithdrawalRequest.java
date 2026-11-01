package com.sealease.backend.withdrawal.dto;

import com.sealease.backend.ledger.service.EarningsBalance;
import jakarta.validation.constraints.NotNull;

/** An online investor's request to withdraw the whole of one wallet. */
public record WithdrawalRequest(@NotNull EarningsBalance balance) {
}
