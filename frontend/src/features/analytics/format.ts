import type { ColumnType } from "@/types/analytics";
import { formatDate, formatDateTime, formatMonth, humanize } from "@/utils/format";
import { formatMoney } from "@/utils/money";

/** A report value for the table: money with ₹ and Indian grouping (from the decimal string), dates in the display zone. */
export function formatCell(type: ColumnType, value: string | number | null, currency: string): string {
  if (value === null || value === undefined || value === "") return "—";
  if (value === "Restricted") return "Restricted";
  const s = String(value);
  switch (type) {
    case "MONEY": return formatMoney({ amount: s, currency });
    case "PERCENT": return `${s}%`;
    case "NUMBER": return new Intl.NumberFormat("en-IN", { maximumFractionDigits: 2 }).format(s as Intl.StringNumericLiteral);
    case "DATE": return formatDate(s);
    case "MONTH": return formatMonth(s.slice(0, 7));
    case "DATETIME": return formatDateTime(s);
    case "STATUS": return humanize(s);
    default: return s;
  }
}

export const numeric = (type: ColumnType) => type === "MONEY" || type === "NUMBER" || type === "PERCENT";
