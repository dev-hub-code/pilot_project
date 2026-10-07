import type { Money } from "@/types/marketplace";
import { CURRENCY_SYMBOL } from "@/lib/currency";

const formatters = new Map<string, Intl.NumberFormat>();

/**
 * Formats a backend Money value with its symbol and Indian digit grouping, e.g. "₹1,23,456.00"
 * (compact: "₹1.2L"). The amount string is handed to Intl as a decimal string, so it is never
 * converted to a binary float.
 */
export function formatMoney(money: Money | null | undefined, options: { compact?: boolean } = {}): string {
  if (!money) return "—";
  const key = `${money.currency}:${options.compact ? "c" : "f"}`;
  let formatter = formatters.get(key);
  if (!formatter) {
    formatter = new Intl.NumberFormat("en-IN", {
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

/** The symbol of a currency, e.g. "₹" for INR. */
export function currencySymbol(code: string): string {
  return new Intl.NumberFormat("en-IN", { style: "currency", currency: code }).formatToParts(0)
    .find((part) => part.type === "currency")?.value ?? code;
}

/** Label suffix for amount inputs, e.g. "Amount (₹)". */
export function currencyLabel(label: string): string {
  return `${label} (${CURRENCY_SYMBOL})`;
}

export function formatPercent(value: number, digits = 2): string {
  return `${value.toFixed(digits)}%`;
}

export const FREQUENCY_LABEL = { MONTHLY: "month", QUARTERLY: "quarter" } as const;
