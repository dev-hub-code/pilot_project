package com.sealease.backend.referral.entity;

/** How a commission was earned. Stored by name, so constants must never be renamed once released. */
public enum ReferralEarningKind {

	/** Network scheme: on rent credited, each month of the lease. */
	MONTHLY,
	/** Online scheme: once per referred person, on their first order. */
	ONE_TIME

}
