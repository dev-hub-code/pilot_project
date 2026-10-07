/** Permission codes the UI reacts to. Mirrors backend PermissionCode; display logic only. */
export const Permission = {
  INVESTOR_PORTAL: "INVESTOR_PORTAL",
  ROLE_VIEW: "ROLE_VIEW",
  ROLE_MANAGE: "ROLE_MANAGE",
  USER_ROLE_ASSIGN: "USER_ROLE_ASSIGN",
  USER_VIEW: "USER_VIEW",
  USER_SUSPEND: "USER_SUSPEND",
  KYC_REVIEW: "KYC_REVIEW",
  INVESTOR_CLASSIFY: "INVESTOR_CLASSIFY",
  BANK_ACCOUNT_VERIFY: "BANK_ACCOUNT_VERIFY",
  FINANCE_VIEW: "FINANCE_VIEW",
  FINANCE_ADJUST: "FINANCE_ADJUST",
  PAYMENT_CONFIRM: "PAYMENT_CONFIRM",
  RENTAL_RECORD: "RENTAL_RECORD",
  RENTAL_APPROVE: "RENTAL_APPROVE",
  ORDER_VIEW: "ORDER_VIEW",
  INVESTMENT_VIEW: "INVESTMENT_VIEW",
  INVESTMENT_CREATE: "INVESTMENT_CREATE",
  INVESTMENT_UPDATE: "INVESTMENT_UPDATE",
  INVESTMENT_APPROVE: "INVESTMENT_APPROVE",
} as const;

/** Any permission beyond the investor portal grants access to the staff console. */
export function hasStaffAccess(permissions: readonly string[]): boolean {
  return permissions.some((p) => p !== Permission.INVESTOR_PORTAL);
}

export function hasPermission(permissions: readonly string[], permission: string): boolean {
  return permissions.includes(permission);
}
