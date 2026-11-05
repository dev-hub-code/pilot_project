import type { StatusTone } from "@/components/ui/status-badge";
import type { BatchStatus, WithdrawalKind, WithdrawalPayoutMethod, WithdrawalStatus } from "@/types/withdrawal";

export const WITHDRAWAL_KIND_LABEL: Record<WithdrawalKind, string> = { RENT: "Returns", REFERRAL: "Referral" };

/** Which sheet of a withdrawal run: paid into a bank account, or collected in cash at the office. */
export const PAYOUT_METHOD_LABEL: Record<WithdrawalPayoutMethod, string> = { BANK_TRANSFER: "Bank", OFFICE_CASH: "Cash" };

export const WITHDRAWAL_STATUS_LABEL: Record<WithdrawalStatus, string> = {
  SUBMITTED: "Submitted",
  UNDER_REVIEW: "Under review",
  ON_HOLD: "On hold",
  PENDING_APPROVAL: "Awaiting second approval",
  APPROVED: "Approved",
  BATCHED: "Payment initiated",
  PROCESSING: "Payment pending",
  PAID: "Paid",
  FAILED: "Payment failed",
  RETURNED: "Returned to balance",
  REJECTED: "Rejected",
  CANCELLED: "Cancelled",
};

/** Investor-facing wording where it differs from staff's. */
export function investorStatusLabel(status: WithdrawalStatus, infoRequested = false): string {
  if (status === "ON_HOLD" && infoRequested) return "Information needed";
  if (status === "PENDING_APPROVAL") return "Under review";
  return WITHDRAWAL_STATUS_LABEL[status];
}

/** The steps an investor's request goes through, for the progress tracker. */
export const TRACK_STEPS: { label: string; reached: WithdrawalStatus[] }[] = [
  { label: "Submitted", reached: ["SUBMITTED", "UNDER_REVIEW", "ON_HOLD", "PENDING_APPROVAL", "APPROVED", "BATCHED", "PROCESSING", "PAID", "FAILED"] },
  { label: "Under review", reached: ["UNDER_REVIEW", "ON_HOLD", "PENDING_APPROVAL", "APPROVED", "BATCHED", "PROCESSING", "PAID", "FAILED"] },
  { label: "Approved", reached: ["APPROVED", "BATCHED", "PROCESSING", "PAID", "FAILED"] },
  { label: "Payment initiated", reached: ["BATCHED", "PROCESSING", "PAID", "FAILED"] },
  { label: "Paid", reached: ["PAID"] },
];

export const OPEN_STATUSES: WithdrawalStatus[] = ["SUBMITTED", "UNDER_REVIEW", "ON_HOLD", "PENDING_APPROVAL", "APPROVED", "BATCHED", "PROCESSING", "FAILED"];
export const CANCELLABLE: WithdrawalStatus[] = ["SUBMITTED", "UNDER_REVIEW", "ON_HOLD", "PENDING_APPROVAL"];

export const WITHDRAWAL_TONE: Record<WithdrawalStatus, StatusTone> = {
  SUBMITTED: "neutral",
  UNDER_REVIEW: "warning",
  ON_HOLD: "warning",
  PENDING_APPROVAL: "warning",
  APPROVED: "success",
  BATCHED: "neutral",
  PROCESSING: "warning",
  PAID: "success",
  FAILED: "danger",
  RETURNED: "neutral",
  REJECTED: "danger",
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

