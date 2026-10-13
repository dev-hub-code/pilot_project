package com.sealease.backend.withdrawal.dto;

import com.sealease.backend.withdrawal.entity.WithdrawalPayoutMethod;
import jakarta.validation.constraints.NotNull;

public record ReferralPayoutModeRequest(@NotNull WithdrawalPayoutMethod mode) {
}
