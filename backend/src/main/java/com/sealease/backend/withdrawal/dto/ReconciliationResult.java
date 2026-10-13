package com.sealease.backend.withdrawal.dto;

/**
 * What an uploaded reconciliation file did.
 *
 * @param paid      withdrawals it completed
 * @param failed    withdrawals it failed (their amounts went back to the investors' balances)
 * @param unchanged rows left blank, or already in the state the file gives
 */
public record ReconciliationResult(int paid, int failed, int unchanged, BatchDetail batch) {
}
