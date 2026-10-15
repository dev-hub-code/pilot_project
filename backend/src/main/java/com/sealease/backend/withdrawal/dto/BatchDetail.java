package com.sealease.backend.withdrawal.dto;

import java.util.List;
import java.util.UUID;

/** @param investors the investors the batch pays, to know who is who (their phone for cash at the office) */
public record BatchDetail(BatchResponse batch, List<WithdrawalResponse> items, List<Investor> investors) {

	/** @param phone cash batches only: to call the investor in to collect */
	public record Investor(UUID userId, String userCode, String name, String phone) {
	}

}
