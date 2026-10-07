package com.sealease.backend.referral.repository;

import java.math.BigDecimal;

/** Commission earned in one calendar month (UTC, {@code yyyy-MM}) at one level, in one currency. */
public interface MonthlyLevelTotal {

	String getMonth();

	Number getLevel();

	String getCurrency();

	BigDecimal getAmount();

}
