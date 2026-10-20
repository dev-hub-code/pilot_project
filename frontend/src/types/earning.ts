/** Mirrors of the backend's rent, earnings and ledger DTOs. */
import type { Money } from "./marketplace";

export type LeaseRentStatus = "RUNNING" | "COMPLETED";

/** The rent of one container's lease, credited every hour from its start to its end. */
export interface LeaseRent {
  holdingId: string;
  /** Staff views only. */
  userId: string | null;
  productId: string;
  productCode: string | null;
  productTitle: string | null;
  containerNumber: string | null;
  tenureMonths: number;
  startsAt: string;
  endsAt: string;
  /** Credited up to this hour. */
  creditedUntil: string;
  monthlyRent: Money;
  /** Monthly rent × tenure: what the whole lease pays. */
  totalRent: Money;
  rentCredited: Money;
  /** Capital returned over the lease (containers sold before capital returns ended); zero otherwise. */
  capitalTotal: Money;
  capitalCredited: Money;
  status: LeaseRentStatus;
  completedAt: string | null;
}

/**
 * An investor's three earnings balances: RENT_BANK (rent on containers paid by bank) is paid into a bank account,
 * RENT_CASH (rent on containers paid in cash at the office) is collected in cash there, and REFERRAL either way.
 */
export type EarningsBalance = "RENT_BANK" | "RENT_CASH" | "REFERRAL";

export interface EarningsSummary {
  /** The investor's wallet: what the platform owes them now, all balances added up per currency. */
  balances: Money[];
  /** The wallet per earnings balance and currency. */
  wallet: { balance: EarningsBalance; amount: Money }[];
  rentPaid: Money[];
  capitalReturned: Money[];
  /** Commission on referrals' rent, already included in the wallet balance. */
  referralEarned: Money[];
  /** The rent of each container's lease, oldest lease first. */
  leases: LeaseRent[];
  /** Withdrawn from the wallet by a withdrawal run and not yet paid out. */
  withdrawalInProgress: Money[];
  /** Paid out to the investor over all time. */
  totalWithdrawn: Money[];
  /** When the next hour of rent ends and is credited (a minute or so after); null with no lease running or starting. */
  nextCreditAt: string | null;
}

/** Leases with ended hours not credited yet (crediting is behind), in one currency, and what those hours come to. */
export interface DuePayouts {
  count: number;
  total: Money;
}

export type AccountType =
  | "RENTAL_CASH"
  | "INVESTOR_EARNINGS"
  | "INVESTOR_CASH_EARNINGS"
  | "INVESTOR_REFERRAL_EARNINGS"
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

/** How often rent is credited, as admins set it. */
export interface RentCreditSettings {
  intervalMinutes: number;
  /** e.g. "every hour". */
  label: string;
  /** The configured default, which applies until an admin sets one. */
  defaultMinutes: number;
  options: { minutes: number; label: string }[];
  /** Null while the default applies. */
  updatedAt: string | null;
  updatedByName: string | null;
}
