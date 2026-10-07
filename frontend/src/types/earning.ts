/** Mirrors of the backend's Phase 6 DTOs (rental receipts, earnings and the ledger). */
import type { Money } from "./marketplace";

export type ReceiptStatus = "RECORDED" | "DISTRIBUTED" | "REJECTED";

export interface RentalReceipt {
  id: string;
  productId: string;
  productCode: string;
  productTitle: string;
  periodNumber: number;
  periodCount: number;
  periodStartsOn: string;
  /** Exclusive: the first day of the next period, when this period's rent fell due. */
  periodEndsOn: string;
  amount: Money;
  expectedAmount: Money;
  receivedOn: string;
  externalReference: string;
  note: string | null;
  status: ReceiptStatus;
  recordedBy: string;
  decidedBy: string | null;
  decidedAt: string | null;
  rejectionReason: string | null;
  managementFeePercent: number;
  createdAt: string;
}

export interface DistributionLine {
  holdingId: string;
  userId: string;
  ownershipPercent: number;
  gross: Money;
  fee: Money;
  net: Money;
}

/** toInvestors + fees + retained = the amount received. Empty for rejected receipts. */
export interface RentalReceiptDetail {
  receipt: RentalReceipt;
  preview: boolean;
  distribution: DistributionLine[];
  toInvestors: Money | null;
  fees: Money | null;
  retained: Money | null;
}

export interface DuePeriod {
  productId: string;
  productCode: string;
  productTitle: string;
  periodNumber: number;
  periodCount: number;
  periodStartsOn: string;
  dueOn: string;
  expectedAmount: Money;
  daysOverdue: number;
}

export interface Earning {
  id: string;
  holdingId: string;
  productId: string;
  productCode: string;
  productTitle: string;
  periodNumber: number;
  periodStartsOn: string;
  periodEndsOn: string;
  ownershipPercent: number;
  gross: Money;
  fee: Money;
  net: Money;
  paidAt: string;
}

export interface EarningsSummary {
  balances: Money[];
  totalEarned: Money[];
  holdings: { holdingId: string; productId: string; earned: Money; payments: number; lastPaidAt: string }[];
}

export type AccountType =
  | "RENTAL_CASH"
  | "INVESTOR_EARNINGS"
  | "PLATFORM_FEE_REVENUE"
  | "PLATFORM_RETAINED"
  | "PLATFORM_ADJUSTMENTS"
  | "PLATFORM_REFERRAL_EXPENSE"
  | "WITHDRAWALS_IN_TRANSIT";
export type Direction = "DEBIT" | "CREDIT";

export interface LedgerAccount {
  id: string;
  accountType: AccountType;
  ownerUserId: string | null;
  normalBalance: Direction;
  balance: Money;
  createdAt: string;
}

export interface LedgerEntry {
  id: string;
  transactionId: string;
  transactionType:
    | "RENTAL_DISTRIBUTION"
    | "ADJUSTMENT"
    | "REFERRAL_COMMISSION"
    | "WITHDRAWAL_RESERVE"
    | "WITHDRAWAL_RELEASE"
    | "WITHDRAWAL_PAYOUT";
  reference: string;
  description: string;
  direction: Direction;
  amount: Money;
  createdAt: string;
}

export interface TrialBalanceLine {
  currency: string;
  debits: Money;
  credits: Money;
  balanced: boolean;
}
