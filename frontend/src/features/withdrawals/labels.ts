import type { StatusTone } from "@/components/ui/status-badge";
import type { BatchStatus, WithdrawalKind, WithdrawalPayoutMethod, WithdrawalStatus } from "@/types/withdrawal";

export const WITHDRAWAL_KIND_LABEL: Record<WithdrawalKind, string> = { RENT: "Rent", REFERRAL: "Referral" };

/** Which sheet of a withdrawal run: paid into a bank account, or collected in cash at the office. */
export const PAYOUT_METHOD_LABEL: Record<WithdrawalPayoutMethod, string> = { BANK_TRANSFER: "Bank", OFFICE_CASH: "Cash at office" };

export const WITHDRAWAL_STATUS_LABEL: Record<WithdrawalStatus, string> = {
  PENDING_APPROVAL: "Awaiting approval",
  APPROVED: "Ready to collect",
  BATCHED: "In progress",
  PROCESSING: "Being paid",
  PAID: "Paid",
  FAILED: "Payment failed",
  RETURNED: "Returned to balance",
  REJECTED: "Cancelled",
  CANCELLED: "Cancelled",
};

export const WITHDRAWAL_TONE: Record<WithdrawalStatus, StatusTone> = {
  PENDING_APPROVAL: "warning",
  APPROVED: "success",
  BATCHED: "neutral",
  PROCESSING: "warning",
  PAID: "success",
  FAILED: "danger",
  RETURNED: "neutral",
  REJECTED: "neutral",
  CANCELLED: "neutral",
};

export const BATCH_TONE: Record<BatchStatus, StatusTone> = {
  CREATED: "neutral",
  SENT: "warning",
  CLOSED: "success",
  CANCELLED: "neutral",
};

const MONTH = new Intl.DateTimeFormat("en-GB", { month: "long", year: "numeric", timeZone: "UTC" });

/** "2026-10" → "October 2026". */
export function formatPeriod(period: string): string {
  return MONTH.format(new Date(`${period}-01T00:00:00Z`));
}

