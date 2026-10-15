import type { StatusTone } from "@/components/ui/status-badge";
import type { AccountType, EarningsBalance, LeaseRent, LeaseRentStatus } from "@/types/earning";
import { formatDate, formatDateTime } from "@/utils/format";

/** A lease's rent is credited hour by hour while it runs; COMPLETED once its last hour has been. */
export const LEASE_STATUS_LABEL: Record<LeaseRentStatus, string> = { RUNNING: "Earning", COMPLETED: "Completed" };

export const LEASE_TONE: Record<LeaseRentStatus, StatusTone> = { RUNNING: "success", COMPLETED: "neutral" };

/** How much of the lease has been credited, by time: 0-100. */
export function leasePercent(lease: LeaseRent): number {
  const start = new Date(lease.startsAt).getTime();
  const span = new Date(lease.endsAt).getTime() - start;
  return span <= 0 ? 100 : Math.min(100, Math.max(0, ((new Date(lease.creditedUntil).getTime() - start) / span) * 100));
}

const HOUR_MS = 60 * 60 * 1000;

/**
 * Where a lease's rent stands: starting (at the full hour after the container was assigned), waiting for its first
 * hour to end, credited up to an hour, or ended.
 */
export function leaseNote(lease: LeaseRent): string {
  if (lease.status === "COMPLETED") return `Ended ${formatDate(lease.endsAt)}`;
  const start = new Date(lease.startsAt).getTime();
  if (start > Date.now()) return `Starts ${formatDateTime(lease.startsAt)}`;
  if (lease.creditedUntil === lease.startsAt) return `First credit ${formatDateTime(new Date(start + HOUR_MS).toISOString())}`;
  return `Credited to ${formatDateTime(lease.creditedUntil)}`;
}

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

/**
 * When rent next reaches the wallet: each hour is credited once it has ended, from the first full hour after a
 * container is assigned.
 */
export function nextCreditNote(nextCreditAt: string | null): string {
  if (!nextCreditAt) return "Credited every hour once a container is assigned";
  return new Date(nextCreditAt).getTime() <= Date.now()
    ? "Next hourly credit any minute now"
    : `Next hourly credit ${formatDateTime(nextCreditAt)}`;
}
