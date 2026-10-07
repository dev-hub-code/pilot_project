package com.sealease.backend.referral.repository;

import java.math.BigDecimal;
import java.util.UUID;

/** Commission earned from one referred investor, in one currency. */
public record ReferralTotal(UUID sourceUserId, String currency, BigDecimal amount) {
}
