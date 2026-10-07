/** Mirrors of the backend's Phase 8 DTOs (withdrawals and payout batches). */
import type { Money } from "./marketplace";

export type WithdrawalStatus =
  | "PENDING_APPROVAL"
  | "APPROVED"
  | "BATCHED"
  | "PROCESSING"
  | "PAID"
  | "FAILED"
  | "REJECTED"
  | "CANCELLED";

/** Approver/rejecter ids and batchId are only present in staff views. */
export interface Withdrawal {
  id: string;
  reference: string;
  userId: string;
  amount: Money;
  status: WithdrawalStatus;
  bankHolderName: string;
  bankName: string;
  bankAccountMasked: string;
  requiredApprovals: number;
  approvals: number;
  firstApprovedBy: string | null;
  firstApprovedAt: string | null;
  secondApprovedBy: string | null;
  secondApprovedAt: string | null;
  rejectedBy: string | null;
  rejectionReason: string | null;
  batchId: string | null;
  payoutReference: string | null;
  failureReason: string | null;
  createdAt: string;
  closedAt: string | null;
}

export interface WithdrawalPolicy {
  minimumAmount: number;
  dualApprovalThreshold: number;
}

export type BatchStatus = "CREATED" | "SENT" | "CLOSED" | "CANCELLED";

export interface PayoutBatch {
  id: string;
  reference: string;
  currency: string;
  status: BatchStatus;
  itemCount: number;
  total: Money;
  paid: number;
  failed: number;
  outstanding: number;
  createdBy: string;
  createdAt: string;
  sentAt: string | null;
  closedAt: string | null;
}

export interface PayoutBatchDetail {
  batch: PayoutBatch;
  items: Withdrawal[];
}
