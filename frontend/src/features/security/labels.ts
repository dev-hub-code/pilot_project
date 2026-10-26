import type { AuditCategory, UserRef } from "@/types/security";
import { humanize } from "@/utils/format";

export const CATEGORY_LABEL: Record<AuditCategory, string> = {
  SIGN_IN: "Sign-in & passwords",
  ACCESS_CONTROL: "Access control",
  SENSITIVE_DATA: "Sensitive data access",
  VERIFICATION: "KYC & bank verification",
  MONEY: "Money",
  OPERATIONS: "Operations",
};

/**
 * The actions in each category, mirroring AuditAction and AuditCategory in the backend (which decides
 * the category; this list only fills the action filter).
 */
export const ACTIONS_BY_CATEGORY: Record<AuditCategory, readonly string[]> = {
  SIGN_IN: ["USER_REGISTERED", "EMAIL_VERIFIED", "EMAIL_VERIFICATION_SENT", "PASSWORD_RESET_REQUESTED", "PASSWORD_RESET",
    "LOGIN", "LOGIN_FAILED", "ACCOUNT_LOCKED", "LOGOUT", "LOGOUT_ALL", "TOKEN_REFRESH_REUSE_DETECTED", "PASSWORD_CHANGE",
    "TEMPORARY_PASSWORD_ISSUED"],
  ACCESS_CONTROL: ["ROLE_CREATED", "ROLE_UPDATED", "ROLE_DELETED", "USER_ROLES_CHANGED", "STAFF_ACCOUNT_CREATED",
    "USER_SUSPENDED", "USER_REACTIVATED", "USER_DISABLED", "USER_ENABLED", "INVESTOR_CLASSIFIED"],
  SENSITIVE_DATA: ["KYC_DOCUMENT_VIEWED", "KYC_SUBMISSION_VIEWED", "BANK_ACCOUNT_PROOF_VIEWED", "TICKET_ATTACHMENT_VIEWED",
    "REPORT_GENERATED", "AUDIT_LOG_EXPORTED"],
  VERIFICATION: ["KYC_SUBMITTED", "KYC_DOCUMENT_UPLOADED", "KYC_APPROVED", "KYC_REJECTED", "BANK_ACCOUNT_ADDED",
    "BANK_ACCOUNT_REMOVED", "BANK_ACCOUNT_PRIMARY_CHANGED", "BANK_ACCOUNT_VERIFIED", "BANK_ACCOUNT_REJECTED",
    "BANK_ACCOUNT_CHANGE_REQUESTED", "BANK_ACCOUNT_CHANGED", "KYC_ASSIGNED", "KYC_UNASSIGNED", "BANK_ACCOUNT_ASSIGNED",
    "BANK_ACCOUNT_UNASSIGNED"],
  MONEY: ["PAYMENT_STARTED", "PAYMENT_DETAILS_SUBMITTED", "PAYMENT_SUCCEEDED", "PAYMENT_FAILED", "PAYMENT_REJECTED",
    "PAYMENT_REFUND_REQUIRED", "PAYMENT_REFUNDED", "INVOICE_ISSUED", "PAYOUT_PAID", "PAYOUT_DAY_CHANGED", "RENT_CREDIT_INTERVAL_CHANGED",
    "RENT_RATE_TIERS_CHANGED", "MINIMUM_ORDER_CHANGED",
    "COMPANY_BANK_ACCOUNT_CREATED", "COMPANY_BANK_ACCOUNT_UPDATED", "COMPANY_BANK_ACCOUNT_ACTIVATED",
    "COMPANY_BANK_ACCOUNT_DEACTIVATED", "RENTAL_RECORDED", "RENTAL_DISTRIBUTED", "RENTAL_REJECTED",
    "LEDGER_ADJUSTMENT_POSTED", "REFERRAL_RATES_SCHEDULED", "REFERRAL_RATES_CANCELLED", "REFERRAL_COMMISSIONS_PAID",
    "WITHDRAWAL_REQUESTED", "WITHDRAWAL_CANCELLED", "WITHDRAWAL_APPROVED", "WITHDRAWAL_REJECTED", "WITHDRAWAL_PAID",
    "WITHDRAWAL_FAILED", "WITHDRAWAL_BATCH_CREATED", "WITHDRAWAL_BATCH_CANCELLED", "WITHDRAWAL_BATCH_EXPORTED",
    "WITHDRAWAL_BATCH_SENT", "WITHDRAWAL_BATCH_CLOSED", "WITHDRAWAL_BATCH_RECONCILED", "WITHDRAWAL_DAY_CHANGED",
    "SCHEDULED_WITHDRAWALS_RUN", "WITHDRAWAL_RETURNED", "REFERRAL_PAYOUT_MODE_CHOSEN"],
  OPERATIONS: ["PROFILE_UPDATED", "TAX_INFO_UPDATED", "CONTAINER_CREATED", "CONTAINER_UPDATED", "CONTAINER_STATUS_CHANGED",
    "CONTAINER_DOCUMENT_ADDED", "CONTAINER_DOCUMENT_VISIBILITY_CHANGED", "PRODUCT_CREATED", "PRODUCT_UPDATED",
    "PRODUCT_PUBLISHED", "PRODUCT_CANCELLED", "PRODUCT_CLOSED", "PRODUCT_LEASE_ACTIVATED", "PRODUCT_MATURED",
    "HOLDING_MATURED", "HOLDING_CONTAINER_ASSIGNED", "AGREEMENT_TEMPLATE_UPDATED", "LEASE_AGREEMENT_ISSUED", "LEASE_AGREEMENT_ACCEPTED",
    "PLAN_PHOTO_ADDED", "PLAN_PHOTO_REMOVED", "ORDER_PLACED",
    "ORDER_CANCELLED", "ORDER_EXPIRED", "ORDER_CONFIRMED", "REFERRAL_LINKED", "LEAD_CREATED", "LEAD_ASSIGNED", "LEAD_WON", "TICKET_OPENED",
    "TICKET_ASSIGNED", "TICKET_STATUS_CHANGED", "TICKET_REOPENED", "TICKET_PRIORITY_CHANGED"],
};

const ACTION_LABEL: Record<string, string> = {
  LOGIN: "Signed in",
  LOGIN_FAILED: "Failed sign-in",
  LOGOUT: "Signed out",
  LOGOUT_ALL: "Signed out everywhere",
  ACCOUNT_LOCKED: "Account locked",
  TOKEN_REFRESH_REUSE_DETECTED: "Stolen sign-in token detected",
  PASSWORD_CHANGE: "Password changed",
  PASSWORD_RESET: "Password reset",
  PASSWORD_RESET_REQUESTED: "Password reset requested",
  USER_ROLES_CHANGED: "Roles changed",
  KYC_DOCUMENT_VIEWED: "KYC document opened",
  KYC_SUBMISSION_VIEWED: "KYC submission opened",
  BANK_ACCOUNT_PROOF_VIEWED: "Bank proof opened",
  KYC_ASSIGNED: "KYC review taken",
  KYC_UNASSIGNED: "KYC review returned to the queue",
  BANK_ACCOUNT_ASSIGNED: "Bank verification taken",
  BANK_ACCOUNT_UNASSIGNED: "Bank verification returned to the queue",
  TICKET_ATTACHMENT_VIEWED: "Ticket attachment opened",
  REPORT_GENERATED: "Report downloaded",
  AUDIT_LOG_EXPORTED: "Audit log exported",
  AGREEMENT_TEMPLATE_UPDATED: "Agreement wording changed",
  LEASE_AGREEMENT_ISSUED: "Lease agreement issued",
  LEASE_AGREEMENT_ACCEPTED: "Lease agreement accepted",
};

export function actionLabel(action: string): string {
  return ACTION_LABEL[action] ?? humanize(action);
}

/** The events that call for a look: shown in red or amber. */
export function actionTone(action: string): "danger" | "warning" | "neutral" {
  if (action === "TOKEN_REFRESH_REUSE_DETECTED" || action === "ACCOUNT_LOCKED") return "danger";
  if (action === "LOGIN_FAILED" || action === "USER_SUSPENDED" || action === "USER_DISABLED" || action === "ROLE_DELETED") return "warning";
  return "neutral";
}

export const PERIODS = [
  { hours: 24, label: "24 hours" },
  { hours: 168, label: "7 days" },
  { hours: 720, label: "30 days" },
  { hours: 2160, label: "90 days" },
] as const;

/** Where a logged entity is shown in the admin area, if anywhere. */
export function entityHref(entityType: string, entityId: string | null): string | null {
  if (!entityId) return null;
  const base: Record<string, string> = {
    USER: "/admin/users",
    KYC_SUBMISSION: "/admin/kyc",
    BANK_ACCOUNT: "/admin/bank-accounts",
    INVESTMENT_PRODUCT: "/admin/products",
    ORDER: "/admin/orders",
    SUPPORT_TICKET: "/admin/support",
    LEAD: "/admin/leads",
    LEDGER_TRANSACTION: "/admin/ledger",
    WITHDRAWAL: "/admin/withdrawals",
    WITHDRAWAL_BATCH: "/admin/withdrawals/batches",
  };
  return base[entityType] ? `${base[entityType]}/${encodeURIComponent(entityId)}` : null;
}

/** "Name (email)", or what is known of a person. */
export function personLabel(person: UserRef | null, fallback = "System"): string {
  if (!person) return fallback;
  if (!person.email) return "Deleted account";
  return person.name ? `${person.name} (${person.email})` : person.email;
}
