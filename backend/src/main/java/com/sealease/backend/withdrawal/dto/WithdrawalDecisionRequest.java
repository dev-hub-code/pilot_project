package com.sealease.backend.withdrawal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A reason, required to reject, hold or ask for information; and the text of an internal note. */
public record WithdrawalDecisionRequest(@NotBlank @Size(max = 1000) String reason) {
}
