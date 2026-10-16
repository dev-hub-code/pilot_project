package com.sealease.backend.audit;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * Groups of audit actions, for filtering the log and for the security dashboard. Every action is in
 * exactly one category; {@link #OPERATIONS} takes whatever no other category names.
 */
public enum AuditCategory {

	/** Signing up, in and out, passwords and session security. */
	SIGN_IN(EnumSet.of(AuditAction.USER_REGISTERED, AuditAction.EMAIL_VERIFIED, AuditAction.EMAIL_VERIFICATION_SENT,
			AuditAction.PASSWORD_RESET_REQUESTED, AuditAction.PASSWORD_RESET, AuditAction.LOGIN,
			AuditAction.LOGIN_FAILED, AuditAction.ACCOUNT_LOCKED, AuditAction.LOGOUT, AuditAction.LOGOUT_ALL,
			AuditAction.TOKEN_REFRESH_REUSE_DETECTED, AuditAction.PASSWORD_CHANGE,
			AuditAction.TEMPORARY_PASSWORD_ISSUED)),

	/** Who may do what: roles, staff accounts, suspensions. */
	ACCESS_CONTROL(EnumSet.of(AuditAction.ROLE_CREATED, AuditAction.ROLE_UPDATED, AuditAction.ROLE_DELETED,
			AuditAction.ROLE_DASHBOARD_CARDS_CHANGED,
			AuditAction.USER_ROLES_CHANGED, AuditAction.STAFF_ACCOUNT_CREATED, AuditAction.USER_SUSPENDED,
			AuditAction.USER_REACTIVATED, AuditAction.INVESTOR_CLASSIFIED)),

	/** Staff opening personal documents or taking data out of the platform. */
	SENSITIVE_DATA(EnumSet.of(AuditAction.KYC_DOCUMENT_VIEWED, AuditAction.KYC_SUBMISSION_VIEWED,
			AuditAction.BANK_ACCOUNT_PROOF_VIEWED, AuditAction.TICKET_ATTACHMENT_VIEWED, AuditAction.REPORT_GENERATED,
			AuditAction.AUDIT_LOG_EXPORTED)),

	/** Identity and bank-account verification. */
	VERIFICATION(EnumSet.of(AuditAction.KYC_SUBMITTED, AuditAction.KYC_DOCUMENT_UPLOADED, AuditAction.KYC_APPROVED,
			AuditAction.KYC_REJECTED, AuditAction.BANK_ACCOUNT_ADDED, AuditAction.BANK_ACCOUNT_REMOVED,
			AuditAction.BANK_ACCOUNT_PRIMARY_CHANGED, AuditAction.BANK_ACCOUNT_VERIFIED,
			AuditAction.BANK_ACCOUNT_REJECTED, AuditAction.BANK_ACCOUNT_CHANGE_REQUESTED,
			AuditAction.BANK_ACCOUNT_CHANGED, AuditAction.KYC_ASSIGNED, AuditAction.KYC_UNASSIGNED,
			AuditAction.BANK_ACCOUNT_ASSIGNED, AuditAction.BANK_ACCOUNT_UNASSIGNED)),

	/** Money moving: payments, payouts, ledger, withdrawals, commissions and the accounts behind them. */
	MONEY(EnumSet.of(AuditAction.PAYMENT_STARTED, AuditAction.PAYMENT_DETAILS_SUBMITTED,
			AuditAction.PAYMENT_SUCCEEDED, AuditAction.PAYMENT_FAILED, AuditAction.PAYMENT_REJECTED,
			AuditAction.PAYMENT_REFUND_REQUIRED, AuditAction.PAYMENT_REFUNDED, AuditAction.INVOICE_ISSUED,
			AuditAction.PAYOUT_PAID, AuditAction.PAYOUT_DAY_CHANGED, AuditAction.COMPANY_BANK_ACCOUNT_CREATED,
			AuditAction.COMPANY_BANK_ACCOUNT_UPDATED, AuditAction.COMPANY_BANK_ACCOUNT_ACTIVATED,
			AuditAction.COMPANY_BANK_ACCOUNT_DEACTIVATED, AuditAction.RENTAL_RECORDED,
			AuditAction.RENTAL_DISTRIBUTED, AuditAction.RENTAL_REJECTED, AuditAction.LEDGER_ADJUSTMENT_POSTED,
			AuditAction.REFERRAL_RATES_SCHEDULED, AuditAction.REFERRAL_RATES_CANCELLED,
			AuditAction.REFERRAL_COMMISSIONS_PAID, AuditAction.WITHDRAWAL_REQUESTED,
			AuditAction.WITHDRAWAL_CANCELLED, AuditAction.WITHDRAWAL_APPROVED, AuditAction.WITHDRAWAL_REJECTED,
			AuditAction.WITHDRAWAL_PAID, AuditAction.WITHDRAWAL_FAILED, AuditAction.WITHDRAWAL_BATCH_CREATED,
			AuditAction.WITHDRAWAL_BATCH_CANCELLED, AuditAction.WITHDRAWAL_BATCH_EXPORTED,
			AuditAction.WITHDRAWAL_BATCH_SENT, AuditAction.WITHDRAWAL_BATCH_CLOSED,
			AuditAction.WITHDRAWAL_BATCH_RECONCILED, AuditAction.WITHDRAWAL_DAY_CHANGED,
			AuditAction.SCHEDULED_WITHDRAWALS_RUN, AuditAction.WITHDRAWAL_RETURNED,
			AuditAction.REFERRAL_PAYOUT_MODE_CHOSEN)),

	/** Everything else: plans, containers, orders, support, leads, profiles. */
	OPERATIONS(EnumSet.noneOf(AuditAction.class));

	static {
		Set<AuditAction> rest = EnumSet.allOf(AuditAction.class);
		for (AuditCategory category : values()) {
			rest.removeAll(category.actions);
		}
		OPERATIONS.actions.addAll(rest);
	}

	private final Set<AuditAction> actions;

	AuditCategory(Set<AuditAction> actions) {
		this.actions = actions;
	}

	/** The actions in this category (a copy). */
	public Set<AuditAction> actions() {
		return EnumSet.copyOf(actions);
	}

	public static AuditCategory of(AuditAction action) {
		return Arrays.stream(values()).filter(c -> c.actions.contains(action)).findFirst().orElse(OPERATIONS);
	}

}
