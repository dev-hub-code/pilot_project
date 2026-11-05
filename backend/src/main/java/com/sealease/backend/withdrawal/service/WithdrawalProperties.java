package com.sealease.backend.withdrawal.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;

/**
 * Amounts are in the withdrawal's own currency.
 *
 * @param minimumAmount         smallest amount an investor may withdraw
 * @param dualApprovalThreshold withdrawals above this need a second, different approver
 * @param maxBatchSize          withdrawals taken into one payout batch
 * @param payoutProvider        who approved withdrawals are handed to for payment (see {@code PayoutProvider})
 * @param payoutWebhookSecret   signs payout-status webhooks (HMAC-SHA256 of the body, hex); blank turns the webhook off
 */
@ConfigurationProperties(prefix = "app.withdrawals")
public record WithdrawalProperties(
		@DefaultValue("50") BigDecimal minimumAmount,
		@DefaultValue("10000") BigDecimal dualApprovalThreshold,
		@DefaultValue("500") int maxBatchSize,
		@DefaultValue("BANK_SHEET") String payoutProvider,
		@DefaultValue("") String payoutWebhookSecret) {
}
