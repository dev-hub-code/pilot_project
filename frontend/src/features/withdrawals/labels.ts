import type { StatusTone } from "@/components/ui/status-badge";
import type { BatchStatus, WithdrawalStatus } from "@/types/withdrawal";

export const WITHDRAWAL_STATUS_LABEL: Record<WithdrawalStatus, string> = {
  PENDING_APPROVAL: "Awaiting approval",
  APPROVED: "Approved",
  BATCHED: "Scheduled for payout",
  PROCESSING: "Being paid",
  PAID: "Paid",
  FAILED: "Failed",
  REJECTED: "Rejected",
  CANCELLED: "Cancelled",
};

export const WITHDRAWAL_TONE: Record<WithdrawalStatus, StatusTone> = {
  PENDING_APPROVAL: "warning",
  APPROVED: "success",
  BATCHED: "neutral",
  PROCESSING: "warning",
  PAID: "success",
  FAILED: "danger",
  REJECTED: "danger",
  CANCELLED: "neutral",
};

export const BATCH_TONE: Record<BatchStatus, StatusTone> = {
  CREATED: "neutral",
  SENT: "warning",
  CLOSED: "success",
  CANCELLED: "neutral",
};
