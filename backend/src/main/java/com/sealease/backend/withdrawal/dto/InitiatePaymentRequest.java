package com.sealease.backend.withdrawal.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Approved withdrawals to pay: handed to the payout provider together, grouped by kind and payout method. */
public record InitiatePaymentRequest(@NotEmpty @Size(max = 500) List<UUID> withdrawalIds) {
}
