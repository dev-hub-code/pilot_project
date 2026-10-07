import type { ColumnDatum } from "@/components/charts/column-chart";
import type { Money } from "@/types/marketplace";

const short = new Intl.DateTimeFormat("en-GB", { month: "short", timeZone: "UTC" });
const long = new Intl.DateTimeFormat("en-GB", { month: "long", year: "numeric", timeZone: "UTC" });

/** "yyyy-MM" of the month `offset` months from the current one (UTC). */
export function monthKey(offset: number, now = new Date()): string {
  const date = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() + offset, 1));
  return date.toISOString().slice(0, 7);
}

/**
 * One column per month from `from` to `to` months around the current one, in the given currency;
 * months without data are zero. January is labelled with its year so the axis reads across a new year.
 */
export function monthColumns<T extends { month: string }>(
  rows: readonly T[], from: number, to: number, currency: string,
  values: (row: T) => Record<string, Money>, now = new Date(),
): ColumnDatum[] {
  const byMonth = new Map(rows.map((row) => [row.month, row]));
  const columns: ColumnDatum[] = [];
  for (let offset = from; offset <= to; offset++) {
    const key = monthKey(offset, now);
    const date = new Date(`${key}-01T00:00:00Z`);
    const row = byMonth.get(key);
    const amounts: Record<string, number> = {};
    if (row) {
      for (const [series, money] of Object.entries(values(row))) {
        if (money.currency === currency) amounts[series] = Number(money.amount);
      }
    }
    const label = short.format(date);
    columns.push({
      label: date.getUTCMonth() === 0 ? `${label} ’${key.slice(2, 4)}` : label,
      title: long.format(date),
      values: amounts,
    });
  }
  return columns;
}
