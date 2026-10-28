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

	ROLE_VIEW, ROLE_MANAGE, USER_ROLE_ASSIGN,

	INVESTMENT_VIEW, INVESTMENT_CREATE, INVESTMENT_UPDATE, INVESTMENT_APPROVE, ORDER_VIEW, RECORD_BROWSE,
	AGREEMENT_TEMPLATE_MANAGE, AGREEMENT_REVIEW,

	FINANCE_VIEW, FINANCE_ADJUST, PAYMENT_CONFIRM, PAYMENT_SEARCH, COMPANY_BANK_ACCOUNT_MANAGE, COMPANY_BANK_ACCOUNT_VIEW, BANK_ACCOUNT_VERIFY,
	PAYOUT_PROCESS,
	CONTAINER_ASSIGN,
	WITHDRAWAL_VIEW, WITHDRAWAL_APPROVE, WITHDRAWAL_REJECT, WITHDRAWAL_PROCESS, FINANCE_TASK_PROCESS,
	REFERRAL_CONFIG_MANAGE, REFERRAL_TREE_MANAGE,

	LEAD_VIEW, LEAD_CREATE, LEAD_ASSIGN, LEAD_UPDATE,

	SUPPORT_TICKET_VIEW, SUPPORT_TICKET_MANAGE, FINANCE_TICKET_HANDLE,

	REPORT_VIEW, REPORT_GENERATE, DASHBOARD_FIGURES_VIEW,

	AUDIT_VIEW, SYSTEM_SETTINGS_MANAGE;

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
