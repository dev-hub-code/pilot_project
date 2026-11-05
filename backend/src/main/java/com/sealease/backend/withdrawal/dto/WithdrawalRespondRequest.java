package com.sealease.backend.withdrawal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The investor's answer when asked for more information about a withdrawal. */
public record WithdrawalRespondRequest(@NotBlank @Size(max = 1000) String message) {
}
