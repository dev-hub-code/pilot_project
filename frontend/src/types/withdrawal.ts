import type { Assignee } from "@/types/user";
/** Mirrors of the backend's Phase 8 DTOs (withdrawals and payout batches). */
import type { EarningsBalance } from "./earning";
import type { Money } from "./plan";

export type WithdrawalStatus =
  | "SUBMITTED"
  | "UNDER_REVIEW"
  | "ON_HOLD"
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
  /** SCHEDULED by a run, OFFICE when finance made it at the office; REQUESTED by an online investor; INVESTOR for earlier requests. */
  origin: "INVESTOR" | "SCHEDULED" | "OFFICE" | "REQUESTED";
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
  /** Charges and tax taken from the amount; net is what is paid. */
  fee: Money;
  tax: Money;
  net: Money;
  /** Who is reviewing it (staff views only). */
  reviewerId: string | null;
  reviewStartedAt: string | null;
  /** Why it is on hold; infoRequested when the investor is asked for more information. */
  holdReason: string | null;
  infoRequested: boolean;
  statusChangedAt: string;
  paymentInitiatedAt: string | null;
  /** Staff views only. */
  payoutProvider: string | null;
  /** Staff lists only. */
  investorCode: string | null;
  investorName: string | null;
}

/** One step of a withdrawal's timeline. Investors see only their steps, without staff names. */
export interface WithdrawalEvent {
  from: WithdrawalStatus | null;
  to: WithdrawalStatus;
  actorId: string | null;
  actorName: string | null;
  reason: string | null;
  visibleToInvestor: boolean;
  at: string;
}

export interface WithdrawalNote {
  id: string;
  authorId: string;
  authorName: string;
  body: string;
  createdAt: string;
}

export interface WithdrawalAdminDetail {
  withdrawal: Withdrawal;
  investor: { userCode: string | null; name: string; email: string; kycStatus: string };
  walletNow: Money;
  batchReference: string | null;
  timeline: WithdrawalEvent[];
  notes: WithdrawalNote[];
}

export interface WithdrawalFigure {
  count: number;
  amount: Money;
}

export interface WithdrawalDashboard {
  submitted: WithdrawalFigure;
  underReview: WithdrawalFigure;
  onHold: WithdrawalFigure;
  pendingSecondApproval: WithdrawalFigure;
  approved: WithdrawalFigure;
  inPayment: WithdrawalFigure;
  failed: WithdrawalFigure;
  paidToday: WithdrawalFigure;
  paidThisMonth: WithdrawalFigure;
  rejectedThisMonth: WithdrawalFigure;
  avgHoursToPay: number | null;
}

/** What withdrawing an amount would pay, worked out by the backend. */
export interface WithdrawalQuote {
  balance: EarningsBalance;
  payoutMethod: WithdrawalPayoutMethod;
  available: Money;
  amount: Money;
  fee: Money;
  tax: Money;
  net: Money;
  remaining: Money;
  minimum: Money;
  allowed: boolean;
  problem: string | null;
}

export interface WithdrawalOverview {
  currency: string;
  totalInvestment: Money;
  totalEarned: Money;
  available: Money;
  locked: Money;
  pending: Money;
  withdrawn: Money;
  activePlans: number;
  /** Online investors request their own withdrawals; network investors are paid by withdrawal runs. */
  canRequest: boolean;
  payoutMethod: WithdrawalPayoutMethod;
  minimum: Money;
  feeFlat: number;
  feePercent: number;
  taxPercent: number;
  processingDays: number | null;
  wallets: { balance: EarningsBalance; available: Money; openWithdrawalId: string | null; openWithdrawalReference: string | null }[];
  investments: {
    holdingId: string;
    productCode: string;
    productTitle: string;
    status: "AWAITING_ACCEPTANCE" | "ACTIVE" | "MATURED";
    invested: Money;
    earned: Money;
    lockedPrincipal: Money;
    lockReason: string;
    leaseStartsOn: string | null;
    leaseEndsOn: string | null;
    nextUnlock: string | null;
  }[];
  bankAccounts: { id: string; holderName: string; bankName: string | null; accountMasked: string; primary: boolean }[];
}

export type WithdrawalPayoutMethod = "BANK_TRANSFER" | "OFFICE_CASH";

export interface WithdrawalPolicy {
  /** A withdrawal run pays an investor's balances only when they come to at least this. */
  minimumAmount: number;
  /** Where cash is collected, and when. */
  officeAddress: string;
  officeHours: string;
}

/** Withdrawal settings an admin sets; until then the configured defaults apply. */
export interface WithdrawalSettings {
  minimumAmount: number;
  currency: string;
  defaultAmount: number;
  feeFlat: number;
  feePercent: number;
  taxPercent: number;
  /** Above this, a second, different approver is needed. */
  dualApprovalThreshold: number;
  defaultDualApprovalThreshold: number;
  /** Working days shown to investors; null when not set. */
  processingDays: number | null;
  /** When an admin last set it; null while the default applies. */
  updatedAt: string | null;
}

/** Which monthly run made a batch, for batches from before admin-made withdrawal runs. */
export type WithdrawalKind = "RENT" | "REFERRAL";

/** How referral earnings are paid this year: chosen once a year, otherwise the default. */
export interface ReferralPayoutMode {
  year: number;
  mode: WithdrawalPayoutMethod;
  chosen: boolean;
  chosenAt: string | null;
  /** Network investors may change it at any time; online investors are always paid by bank. */
  changeable: boolean;
}

export type BatchStatus = "CREATED" | "SENT" | "CLOSED" | "CANCELLED";

export interface PayoutBatch {
  id: string;
  reference: string;
  /** The bank sheet (BANK_TRANSFER) or the cash sheet (OFFICE_CASH) of a withdrawal run. */
  payoutMethod: WithdrawalPayoutMethod;
  /** Which monthly run made it, for batches from before admin-made runs; otherwise null. */
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
  /** Who the batch pays; the phone only for cash at the office. */
  investors: { userId: string; userCode: string | null; name: string | null; phone: string | null }[];
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

/** A finance task: one withdrawal of a run, to pay by bank or hand over in cash, then mark paid or failed. */
export interface FinanceTask {
  id: string;
  /** The withdrawal's reference ("WD-100001"). */
  reference: string;
  kind: WithdrawalKind;
  payoutMethod: WithdrawalPayoutMethod;
  batchId: string;
  batchReference: string | null;
  userId: string;
  userCode: string | null;
  name: string | null;
  /** Cash tasks only. */
  phone: string | null;
  bankName: string | null;
  accountHolder: string | null;
  /** In full while a bank task is open; masked once done; null for cash. */
  accountNumber: string | null;
  ifsc: string | null;
  amount: Money;
  /** BATCHED/PROCESSING while open; then PAID, FAILED or RETURNED. */
  status: WithdrawalStatus;
  payoutReference: string | null;
  failureReason: string | null;
  ticketId: string | null;
  createdAt: string;
  closedAt: string | null;
  /** The finance member who took the task; only they may mark it paid or failed. */
  assignee: Assignee | null;
}

/** One withdrawal run: every eligible investor's rent and referral balances, in a bank sheet and a cash sheet. */
export interface WithdrawalRun {
  /** Null only in the answer to a run that withdrew nothing: such a run is not recorded. */
  id: string | null;
  /** "WR-00001"; null like {@link id}. */
  reference: string | null;
  /** RENT or REFERRAL; null for runs from before they were separate (both). */
  kind: WithdrawalKind | null;
  currency: string;
  runAt: string;
  triggeredBy: string;
  bankBatchId: string | null;
  bankBatchReference: string | null;
  bankInvestors: number;
  bankTotal: Money;
  cashBatchId: string | null;
  cashBatchReference: string | null;
  cashInvestors: number;
  cashTotal: Money;
  /** Investors with money to withdraw who were left out, how they would have been paid, and why. */
  skipped: { userId: string; userCode: string | null; payoutMethod: WithdrawalPayoutMethod; reason: string }[];
}
