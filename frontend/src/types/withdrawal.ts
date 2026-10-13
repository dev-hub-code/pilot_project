/** Mirrors of the backend's Phase 8 DTOs (withdrawals and payout batches). */
import type { EarningsBalance } from "./earning";
import type { Money } from "./marketplace";

export type WithdrawalStatus =
  | "PENDING_APPROVAL"
  | "APPROVED"
  | "BATCHED"
  | "PROCESSING"
  | "PAID"
  | "FAILED"
  | "RETURNED"
  | "REJECTED"
  | "CANCELLED";

/** Approver/rejecter ids and batchId are only present in staff views. */
export interface Withdrawal {
  id: string;
  reference: string;
  userId: string;
  amount: Money;
  /** SCHEDULED by a monthly run, OFFICE when finance made it at the office; INVESTOR for earlier requests. */
  origin: "INVESTOR" | "SCHEDULED" | "OFFICE";
  status: WithdrawalStatus;
  /** BANK_TRANSFER into the account shown, or OFFICE_CASH collected at the office (no bank details). */
  payoutMethod: WithdrawalPayoutMethod;
  /** The earnings balance it draws from. */
  balance: EarningsBalance;
  bankHolderName: string | null;
  bankName: string | null;
  bankAccountMasked: string | null;
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
  /** The finance user who made it at the office (staff views only). */
  createdBy: string | null;
  /** The support ticket opened when its bank payment failed. */
  ticketId: string | null;
  createdAt: string;
  closedAt: string | null;
}

export type WithdrawalPayoutMethod = "BANK_TRANSFER" | "OFFICE_CASH";

export interface WithdrawalPolicy {
  /** A monthly bank withdrawal is made only for a balance of at least this. */
  minimumAmount: number;
  /** Rent is paid into bank accounts, or collected in cash at the office, from this day of the month. */
  rentWithdrawalDay: number;
  /** Likewise for referral earnings. */
  referralWithdrawalDay: number;
  /** Where cash is collected, and when. */
  officeAddress: string;
  officeHours: string;
}

/** The two monthly bank withdrawal runs, each with its own Excel file. */
export type WithdrawalKind = "RENT" | "REFERRAL";

/** How referral earnings are paid this year: chosen once a year, otherwise the default. */
export interface ReferralPayoutMode {
  year: number;
  mode: WithdrawalPayoutMethod;
  chosen: boolean;
  chosenAt: string | null;
}

/** A balance finance may withdraw in cash at the office for an investor today. */
export interface OfficeWithdrawalOption {
  balance: EarningsBalance;
  /** The whole balance, which is what would be withdrawn. */
  available: Money;
  /** Why it cannot be withdrawn today; null when it can. */
  refusal: string | null;
}

export type BatchStatus = "CREATED" | "SENT" | "CLOSED" | "CANCELLED";

export interface PayoutBatch {
  id: string;
  reference: string;
  /** Which monthly run made it; null for batches made by hand before the runs. */
  kind: WithdrawalKind | null;
  /** "yyyy-MM" for a batch made by a monthly run; null when made by hand. */
  period: string | null;
  currency: string;
  status: BatchStatus;
  itemCount: number;
  total: Money;
  paid: number;
  failed: number;
  outstanding: number;
  createdBy: string | null;
  createdAt: string;
  sentAt: string | null;
  closedAt: string | null;
}

export interface PayoutBatchDetail {
  batch: PayoutBatch;
  items: Withdrawal[];
}

/** A file kept with a batch: its bank file, or a reconciliation file uploaded back. */
export interface BatchFile {
  id: string;
  kind: "PAYMENT" | "RECONCILIATION";
  filename: string;
  sizeBytes: number;
  uploadedBy: string | null;
  paidCount: number | null;
  failedCount: number | null;
  createdAt: string;
}

export interface ReconciliationResult {
  paid: number;
  failed: number;
  unchanged: number;
  batch: PayoutBatchDetail;
}

export interface WithdrawalSettings {
  rentWithdrawalDay: number;
  referralWithdrawalDay: number;
  /** ISO dates the next files will be made (the day before each withdrawal day). */
  nextRentFileOn: string;
  nextReferralFileOn: string;
  updatedAt: string;
}

/** One month's rent or referral withdrawal run. */
export interface ScheduledRun {
  id: string;
  kind: WithdrawalKind;
  period: string;
  currency: string;
  runAt: string;
  triggeredBy: string | null;
  batchId: string | null;
  batchReference: string | null;
  withdrawalCount: number;
  total: Money;
  skipped: { userId: string; reason: string }[];
}
