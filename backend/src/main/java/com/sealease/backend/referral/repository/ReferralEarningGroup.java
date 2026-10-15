package com.sealease.backend.referral.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The commission one upline earned at one level on one referred holding's rent in one calendar month (UTC):
 * commission is credited hourly, so this adds up its rows.
 */
public interface ReferralEarningGroup {

	UUID getHoldingId();

	/** {@code yyyy-MM}. */
	String getMonth();

	Number getLevel();

	UUID getSourceUserId();

	UUID getBeneficiaryUserId();

	UUID getProductId();

	String getCurrency();

	BigDecimal getBaseAmount();

	BigDecimal getRatePercent();

	BigDecimal getAmount();

	Instant getLastCreditedAt();

}
