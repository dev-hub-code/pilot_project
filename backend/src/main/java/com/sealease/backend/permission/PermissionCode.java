package com.sealease.backend.permission;

import java.util.Collection;

/**
 * Every permission the code checks. Each constant must have a matching row in the
 * {@code permissions} table (seeded by migrations); {@link com.sealease.backend.permission.service.PermissionCatalogVerifier}
 * fails startup otherwise.
 *
 * <p>Endpoints check them with {@code @PreAuthorize("hasAuthority('ROLE_MANAGE')")}. Permissions are
 * granted to roles at runtime; code never checks role names.
 */
public enum PermissionCode {

	INVESTOR_PORTAL,

	USER_VIEW, USER_UPDATE, USER_SUSPEND, USER_DISABLE, KYC_REVIEW, STAFF_ACCOUNT_VIEW, PHONE_NUMBER_VIEW, DEMO_ACCOUNT_MANAGE,

	ROLE_VIEW, ROLE_MANAGE, USER_ROLE_ASSIGN, INVESTOR_ACCOUNT_CREATE,

	INVESTMENT_VIEW, INVESTMENT_CREATE, INVESTMENT_UPDATE, INVESTMENT_APPROVE, ORDER_VIEW, RECORD_BROWSE,
	AGREEMENT_TEMPLATE_MANAGE, TERMS_MANAGE,

	FINANCE_VIEW, FINANCE_ADJUST, PAYMENT_CONFIRM, PAYMENT_SEARCH, COMPANY_BANK_ACCOUNT_MANAGE, COMPANY_BANK_ACCOUNT_VIEW, BANK_ACCOUNT_VERIFY,
	PAYOUT_PROCESS,
	
	WITHDRAWAL_VIEW, WITHDRAWAL_APPROVE, WITHDRAWAL_REJECT, WITHDRAWAL_PROCESS, FINANCE_TASK_PROCESS,
	REFERRAL_CONFIG_MANAGE, REFERRAL_TREE_MANAGE,

	LEAD_VIEW, LEAD_CREATE, LEAD_ASSIGN, LEAD_UPDATE,

	SUPPORT_TICKET_VIEW, SUPPORT_TICKET_MANAGE, FINANCE_TICKET_HANDLE, ANNOUNCEMENT_MANAGE,
	// New, unassigned by default: grant through role management.
	SUPPORT_CONFIG_MANAGE, KNOWLEDGE_BASE_MANAGE, SUPPORT_ANALYTICS_VIEW,

	REPORT_VIEW, REPORT_GENERATE,

	AUDIT_VIEW, SYSTEM_SETTINGS_MANAGE,

	// Investor
	INVESTOR_VIEW, INVESTOR_CREATE, INVESTOR_UPDATE, INVESTOR_SUSPEND, INVESTOR_DISABLE, INVESTOR_CONTACT_VIEW, INVESTOR_DOCUMENT_VIEW, INVESTOR_EXPORT,

	// Investment
	INVESTMENT_PUBLISH, INVESTMENT_CLOSE,

	// Orders
	ORDER_CREATE, ORDER_UPDATE, ORDER_CANCEL, ORDER_EXPORT,

	// Agreements
	AGREEMENT_VIEW, AGREEMENT_CREATE, AGREEMENT_UPDATE, AGREEMENT_APPROVE,

	// Finance
	PAYMENT_VIEW, PAYMENT_REJECT, FINANCE_EXPORT, LEDGER_VIEW, LEDGER_EXPORT, LEDGER_RECONCILE,

	// Payout
	PAYOUT_VIEW, PAYOUT_CALCULATE, PAYOUT_APPROVE,

	// Bank Accounts
	BANK_ACCOUNT_VIEW, BANK_ACCOUNT_MANAGE,

	// Compliance
	KYC_VIEW, KYC_APPROVE, KYC_REJECT, BANK_VERIFICATION_VIEW, BANK_VERIFICATION_APPROVE, BANK_VERIFICATION_REJECT, COMPLIANCE_CASE_VIEW, COMPLIANCE_CASE_CREATE, COMPLIANCE_CASE_UPDATE, COMPLIANCE_CASE_CLOSE,

	// Crm
	LEAD_EXPORT,

	// Support
	SUPPORT_VIEW, SUPPORT_CREATE, SUPPORT_ASSIGN, SUPPORT_REPLY, SUPPORT_CLOSE, SUPPORT_ESCALATE, SUPPORT_ATTACHMENT_VIEW,

	// Referrals
	REFERRAL_VIEW, REFERRAL_TREE_VIEW, REFERRAL_COMMISSION_VIEW, REFERRAL_COMMISSION_ADJUST,

	// Reports
	REPORT_EXPORT, FINANCIAL_REPORT_VIEW, OPERATIONAL_REPORT_VIEW, INVESTOR_ANALYTICS_VIEW,

	// Security
	AUDIT_EXPORT, SECURITY_VIEW, SESSION_MANAGE,

	// Access
	DEPARTMENT_VIEW, DEPARTMENT_MANAGE, APPROVAL_VIEW, APPROVAL_DECIDE;

	/**
	 * Whether a permission confers power over the platform or other accounts. Self-service
	 * permissions (the investor portal) do not, so they are ignored by escalation guards: an admin
	 * may grant the INVESTOR role or suspend an investor without being an investor.
	 */
	public static boolean isAdministrative(String code) {
		return !INVESTOR_PORTAL.name().equals(code);
	}

	/**
	 * An account is either an investor or staff, never both: a set of permissions may not combine
	 * the investor portal with administrative permissions.
	 */
	public static boolean mixesInvestorAndStaff(Collection<String> codes) {
		return codes.contains(INVESTOR_PORTAL.name()) && codes.stream().anyMatch(PermissionCode::isAdministrative);
	}

}
