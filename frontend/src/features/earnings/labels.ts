import type { StatusTone } from "@/components/ui/status-badge";
import type { AccountType, EarningsBalance, PayoutStatus } from "@/types/earning";
import { formatDate } from "@/utils/format";

/** A payout is one month of a lease, credited day by day; PAID once the whole month has been. */
export const PAYOUT_STATUS_LABEL: Record<PayoutStatus, string> = { SCHEDULED: "Being credited", PAID: "Credited" };

export const PAYOUT_TONE: Record<PayoutStatus, StatusTone> = { SCHEDULED: "neutral", PAID: "success" };

export const ACCOUNT_LABEL: Record<AccountType, string> = {
  RENTAL_CASH: "Client money (cash)",
  INVESTOR_EARNINGS: "Investor rent (bank)",
  INVESTOR_CASH_EARNINGS: "Investor rent (cash)",
  INVESTOR_REFERRAL_EARNINGS: "Investor referral earnings",
  PLATFORM_ADJUSTMENTS: "Adjustments",
  PLATFORM_REFERRAL_EXPENSE: "Referral commissions",
  WITHDRAWALS_IN_TRANSIT: "Withdrawals in transit",
  PLATFORM_RENT_EXPENSE: "Rent paid to investors",
  PLATFORM_CAPITAL_RETURNS: "Capital returned to investors",
};

export const EARNINGS_BALANCE_LABEL: Record<EarningsBalance, string> = {
  RENT_BANK: "Rent (bank)",
  RENT_CASH: "Rent (cash)",
  REFERRAL: "Referral earnings",
};

/** How each earnings balance may be withdrawn. */
export const EARNINGS_BALANCE_NOTE: Record<EarningsBalance, string> = {
  RENT_BANK: "On containers paid by bank; paid into your bank account",
  RENT_CASH: "On containers paid in cash; collected in cash at our office",
  REFERRAL: "Paid into your bank account or in cash, as you choose",
};

/** Adds whole months to an ISO date the way java.time does: the day is clamped to the month's end. */
export function plusMonths(isoDate: string, months: number): string {
  const [y, m, d] = isoDate.split("-").map(Number) as [number, number, number];
  const target = new Date(Date.UTC(y, m - 1 + months, 1));
  const lastDay = new Date(Date.UTC(target.getUTCFullYear(), target.getUTCMonth() + 1, 0)).getUTCDate();
  target.setUTCDate(Math.min(d, lastDay));
  return target.toISOString().slice(0, 10);
}

/** A lease as people read it: first day to last day, inclusive. Backend end dates are exclusive. */
export function formatPeriod(startsOn: string, endsOnExclusive: string): string {
  const last = new Date(`${endsOnExclusive}T00:00:00Z`);
  last.setUTCDate(last.getUTCDate() - 1);
  return `${formatDate(startsOn)} – ${formatDate(last.toISOString())}`;
}

export function todayUtc(): string {
  return new Date().toISOString().slice(0, 10);
}
