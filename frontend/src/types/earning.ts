/** Mirrors of the backend's payout, earnings and ledger DTOs. */
import type { Money } from "./marketplace";

export type PayoutStatus = "SCHEDULED" | "PAID";

/** One monthly payout of one container: rent plus capital returned. */
export interface Payout {
  id: string;
  holdingId: string;
  /** Staff views only. */
  userId: string | null;
  productId: string;
  productCode: string;
  productTitle: string;
  containerNumber: string;
  installmentNumber: number;
  installmentCount: number;
  dueOn: string;
  rent: Money;
  capital: Money;
  total: Money;
  status: PayoutStatus;
  paidAt: string | null;
}

export interface HoldingPayouts {
  holdingId: string;
  productId: string;
  containerNumber: string | null;
  paid: number;
  installments: number;
  received: Money;
  nextDueOn: string | null;
  nextAmount: Money | null;
  lastPaidAt: string | null;
}

export interface EarningsSummary {
  /** The investor's wallet: what the platform owes them now. */
  balances: Money[];
  rentPaid: Money[];
  capitalReturned: Money[];
  /** Commission on referrals' rent, already included in the wallet balance. */
  referralEarned: Money[];
  nextPayout: { dueOn: string; total: Money } | null;
  holdings: HoldingPayouts[];
}

/** Scheduled payouts that have fallen due and are not paid yet, in one currency. */
export interface DuePayouts {
  count: number;
  total: Money;
}

export type AccountType =
  | "RENTAL_CASH"
  | "INVESTOR_EARNINGS"
  | "PLATFORM_ADJUSTMENTS"
  | "PLATFORM_REFERRAL_EXPENSE"
  | "WITHDRAWALS_IN_TRANSIT"
  | "PLATFORM_RENT_EXPENSE"
  | "PLATFORM_CAPITAL_RETURNS";
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
    | "INVESTOR_PAYOUT"
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

/** Payouts falling due in one calendar month ("yyyy-MM"), paid and still scheduled. */
export interface PayoutMonth {
  month: string;
  rentPaid: Money;
  capitalPaid: Money;
  rentScheduled: Money;
  capitalScheduled: Money;
}
