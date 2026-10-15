package com.sealease.backend.withdrawal.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Finance's update of a task.
 *
 * @param outcome   PAID (paid into the account, or the cash collected) or FAILED (could not be paid or collected)
 * @param reference PAID: the bank reference (UTR) or cash receipt number; optional
 * @param reason    FAILED: why; required
 */
public record FinanceTaskUpdateRequest(@NotNull Outcome outcome, @Size(max = 100) String reference,
		@Size(max = 500) String reason) {

	public enum Outcome {
		PAID, FAILED
	}

}
