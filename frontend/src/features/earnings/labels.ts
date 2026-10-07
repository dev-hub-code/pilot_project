import type { StatusTone } from "@/components/ui/status-badge";
import type { AccountType, ReceiptStatus } from "@/types/earning";
import { formatDate } from "@/utils/format";

export const RECEIPT_STATUS_LABEL: Record<ReceiptStatus, string> = {
  RECORDED: "Awaiting approval",
  DISTRIBUTED: "Distributed",
  REJECTED: "Voided",
};

export const RECEIPT_TONE: Record<ReceiptStatus, StatusTone> = {
  RECORDED: "warning",
  DISTRIBUTED: "success",
  REJECTED: "neutral",
};

export const ACCOUNT_LABEL: Record<AccountType, string> = {
  RENTAL_CASH: "Rental cash received",
  INVESTOR_EARNINGS: "Investor earnings",
  PLATFORM_FEE_REVENUE: "Management fee revenue",
  PLATFORM_RETAINED: "Retained (unsold share & rounding)",
  PLATFORM_ADJUSTMENTS: "Adjustments",
};

/** Adds whole months to an ISO date the way java.time does: the day is clamped to the month's end. */
export function plusMonths(isoDate: string, months: number): string {
  const [y, m, d] = isoDate.split("-").map(Number) as [number, number, number];
  const target = new Date(Date.UTC(y, m - 1 + months, 1));
  const lastDay = new Date(Date.UTC(target.getUTCFullYear(), target.getUTCMonth() + 1, 0)).getUTCDate();
  target.setUTCDate(Math.min(d, lastDay));
  return target.toISOString().slice(0, 10);
}

/** A rental period as people read it: first day to last day, inclusive. Backend end dates are exclusive. */
export function formatPeriod(startsOn: string, endsOnExclusive: string): string {
  const last = new Date(`${endsOnExclusive}T00:00:00Z`);
  last.setUTCDate(last.getUTCDate() - 1);
  return `${formatDate(startsOn)} – ${formatDate(last.toISOString())}`;
}

export function todayUtc(): string {
  return new Date().toISOString().slice(0, 10);
}
