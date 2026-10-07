package com.sealease.backend.permission;

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

	USER_VIEW, USER_UPDATE, USER_SUSPEND, KYC_REVIEW, INVESTOR_CLASSIFY,

	ROLE_VIEW, ROLE_MANAGE, USER_ROLE_ASSIGN,

	INVESTMENT_VIEW, INVESTMENT_CREATE, INVESTMENT_UPDATE, INVESTMENT_APPROVE, ORDER_VIEW,

	FINANCE_VIEW, FINANCE_ADJUST, PAYMENT_CONFIRM, BANK_ACCOUNT_VERIFY, RENTAL_RECORD, RENTAL_APPROVE,
	WITHDRAWAL_VIEW, WITHDRAWAL_APPROVE, WITHDRAWAL_REJECT, WITHDRAWAL_PROCESS,
	REFERRAL_CONFIG_MANAGE,

	LEAD_VIEW, LEAD_CREATE, LEAD_ASSIGN, LEAD_UPDATE,

	SUPPORT_TICKET_VIEW, SUPPORT_TICKET_MANAGE,

	REPORT_VIEW, REPORT_GENERATE,

	AUDIT_VIEW, SYSTEM_SETTINGS_MANAGE;

	/**
	 * Whether a permission confers power over the platform or other accounts. Self-service
	 * permissions (the investor portal) do not, so they are ignored by escalation guards: an admin
	 * may grant the INVESTOR role or suspend an investor without being an investor.
	 */
	public static boolean isAdministrative(String code) {
		return !INVESTOR_PORTAL.name().equals(code);
	}

}
