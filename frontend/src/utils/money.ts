import type { Money } from "@/types/marketplace";

const formatters = new Map<string, Intl.NumberFormat>();

/**
 * Formats a backend Money value. The amount string is handed to Intl as a decimal string, so it is
 * never converted to a binary float.
 */
export function formatMoney(money: Money | null | undefined, options: { compact?: boolean } = {}): string {
  if (!money) return "—";
  const key = `${money.currency}:${options.compact ? "c" : "f"}`;
  let formatter = formatters.get(key);
  if (!formatter) {
    formatter = new Intl.NumberFormat("en-US", {
      style: "currency",
      currency: money.currency,
      notation: options.compact ? "compact" : "standard",
      maximumFractionDigits: options.compact ? 1 : 2,
      minimumFractionDigits: options.compact ? 0 : 2,
    });
    formatters.set(key, formatter);
  }
  return formatter.format(money.amount as Intl.StringNumericLiteral);
}

export function formatPercent(value: number, digits = 2): string {
  return `${value.toFixed(digits)}%`;
}

export const FREQUENCY_LABEL = { MONTHLY: "month", QUARTERLY: "quarter" } as const;
