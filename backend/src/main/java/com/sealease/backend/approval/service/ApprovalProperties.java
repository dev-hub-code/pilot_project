package com.sealease.backend.approval.service;

import com.sealease.backend.approval.entity.ApprovalType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Set;

/**
 * @param required operations that must go through an approval request. Set per deployment rather than in the admin
 *                 panel, so nobody can switch off the second person from inside the app.
 */
@ConfigurationProperties("app.approvals")
public record ApprovalProperties(@DefaultValue({ "LEDGER_ADJUSTMENT", "PAYMENT_REFUND" }) Set<ApprovalType> required) {
}
