import { EmptyState } from "@/components/ui/empty-state";
import { CURRENCY } from "@/lib/currency";
import type { PayoutMonth } from "@/types/earning";
import { formatMoney } from "@/utils/money";

const monthName = (month: string) => new Date(`${month}-01T00:00:00Z`)
  .toLocaleDateString("en-IN", { month: "long", year: "numeric", timeZone: "UTC" });

/** The next months' scheduled returns, so an investor sees what is coming and when. */
export function UpcomingReturns({ months, fromMonth }: { months: readonly PayoutMonth[]; fromMonth: string }) {
  const upcoming = months
    .map((m) => ({ month: m.month, amount: Number(m.rentScheduled.amount) + Number(m.capitalScheduled.amount) }))
    .filter((m) => m.month >= fromMonth && m.amount > 0)
    .slice(0, 6);
  if (upcoming.length === 0) {
    return <EmptyState title="Nothing scheduled yet" description="Returns appear here once a term is running." />;
  }
  return (
    <ol className="space-y-4">
      {upcoming.map((m, index) => (
        <li key={m.month} className="flex items-center gap-3 text-sm">
          <span aria-hidden="true" className={`size-2 shrink-0 rounded-full ${index === 0 ? "bg-gold" : "bg-border"}`} />
          <span className="flex-1">{monthName(m.month)}</span>
          <span className="font-medium tabular-nums">{formatMoney({ amount: m.amount.toFixed(2), currency: CURRENCY })}</span>
        </li>
      ))}
    </ol>
  );
}
