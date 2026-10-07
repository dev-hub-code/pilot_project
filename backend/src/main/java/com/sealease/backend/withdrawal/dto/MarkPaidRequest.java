package com.sealease.backend.withdrawal.dto;

import jakarta.validation.constraints.Size;

/** @param payoutReference the bank's reference for the outgoing transfer, if known */
public record MarkPaidRequest(@Size(max = 100) String payoutReference) {
}
