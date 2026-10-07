package com.sealease.backend.withdrawal.dto;

import java.math.BigDecimal;

/** Limits shown to investors before they request a withdrawal; amounts are in the withdrawal's currency. */
public record WithdrawalPolicy(BigDecimal minimumAmount, BigDecimal dualApprovalThreshold) {
}
