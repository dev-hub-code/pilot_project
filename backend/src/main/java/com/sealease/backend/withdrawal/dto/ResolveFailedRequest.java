package com.sealease.backend.withdrawal.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * How a failed bank payment was settled.
 *
 * @param outcome PAID when finance paid it by hand (note: the bank reference), or RETURNED to the investor's balance
 *                to be paid with a later month (note: optional, shown to the investor)
 */
public record ResolveFailedRequest(@NotNull Outcome outcome, @Size(max = 200) String note) {

	public enum Outcome {
		PAID,
		RETURNED
	}

}
