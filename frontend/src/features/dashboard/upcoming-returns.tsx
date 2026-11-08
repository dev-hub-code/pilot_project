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
    <ol className="relative space-y-1 before:absolute before:top-4 before:bottom-4 before:left-[7px] before:w-px before:bg-border">
      {upcoming.map((m, index) => (
        <li key={m.month}
          className={`relative flex items-center gap-3 rounded-xl py-2.5 pr-3 pl-0 text-sm transition-colors ${index === 0 ? "bg-gold/[0.06]" : "hover:bg-background"}`}>
          <span aria-hidden="true" className={`relative z-10 size-[15px] shrink-0 rounded-full border-[3px] border-surface ${index === 0 ? "bg-gold ring-4 ring-gold/15" : "bg-border"}`} />
          <span className="flex-1">
            {monthName(m.month)}
            {index === 0 && <span className="ml-2 rounded-full bg-gold px-2 py-0.5 text-[10px] font-semibold tracking-wide text-on-gold uppercase">Next</span>}
          </span>
          <span className={`tabular-nums ${index === 0 ? "font-semibold text-gold-text" : "font-medium"}`}>
            {formatMoney({ amount: m.amount.toFixed(2), currency: CURRENCY })}
          </span>
        </li>
      ))}
    </ol>
  );
}
