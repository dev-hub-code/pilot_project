/** Mirrors of the backend's reconciliation and unmatched-receipt DTOs. */
import type { TrialBalanceLine } from "./earning";
import type { PaymentMethod } from "./order";
import type { Money } from "./plan";
import type { WithdrawalStatus } from "./withdrawal";

/** Money the gateway took for a reference that matches none of our payments. */
export interface UnmatchedReceipt {
  id: string;
  provider: string;
  /** The checkout order or virtual account the gateway named. */
  providerReference: string;
  gatewayPaymentId: string;
  /** Null when the gateway did not say. */
  amount: Money | null;
  receivedAt: string;
  resolvedBy: string | null;
  resolvedAt: string | null;
  resolution: string | null;
}

export type PayoutEventOutcome = "NEEDS_RECONCILIATION" | "AMOUNT_MISMATCH" | "UNKNOWN_WITHDRAWAL";

export interface ReconciliationReport {
  generatedAt: string;
  /** Everything listed below that needs attention; zero when it all reconciles. */
  issues: number;
  payoutEvents: {
    id: string;
    eventId: string;
    eventType: string;
    outcome: PayoutEventOutcome;
    providerReference: string | null;
    withdrawalReference: string | null;
    withdrawalStatus: WithdrawalStatus | null;
    receivedAt: string;
  }[];
  stuckWithdrawals: { id: string; reference: string; status: WithdrawalStatus; amount: Money; since: string }[];
  pendingRefunds: { paymentId: string; orderId: string; method: PaymentMethod; amount: Money; since: string }[];
  unmatchedReceipts: UnmatchedReceipt[];
  inTransit: { currency: string; ledger: Money; openWithdrawals: Money; matches: boolean }[];
  trialBalance: TrialBalanceLine[];
  unpostedDeposits: {
    paymentId: string;
    status: string;
    amount: Money;
    settledAt: string;
    missing: "DEPOSIT_RECEIVED" | "DEPOSIT_REFUNDED" | "REINVESTMENT";
  }[];
}

/** One daily reconciliation: when, and how many items needed attention then. */
export interface ReconciliationRun {
  id: string;
  runAt: string;
  issues: number;
}
