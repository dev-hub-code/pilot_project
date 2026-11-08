import { EmptyState } from "@/components/ui/empty-state";
import { ProgressBar } from "@/components/ui/progress-bar";
import { leasePercent } from "@/features/earnings/labels";
import type { LeaseRent } from "@/types/earning";
import { formatDate } from "@/utils/format";
import { formatMoney } from "@/utils/money";

/** How far each running term has come, and what it has paid of what it will pay. */
export function TermProgress({ leases }: { leases: readonly LeaseRent[] }) {
  if (leases.length === 0) {
    return <EmptyState title="No terms running yet" description="Your term starts once you accept your investment agreement." />;
  }
  return (
    <ul className="space-y-3">
      {leases.map((lease) => {
        const percent = Math.round(leasePercent(lease));
        return (
          <li key={lease.holdingId} className="space-y-3 rounded-xl border border-border/70 bg-background/60 p-4 text-sm transition-colors hover:border-gold/30 hover:bg-background">
            <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-1">
              <span className="font-semibold">{lease.productTitle ?? "Investment"}</span>
              <span className="rounded-full bg-gold/10 px-2.5 py-0.5 text-xs font-semibold text-gold-text tabular-nums">{percent}%</span>
            </div>
            <ProgressBar percent={leasePercent(lease)} label={`${lease.productTitle ?? "Investment"} term progress`} />
            <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 text-xs text-muted">
              <span className="tabular-nums">
                <span className="font-medium text-foreground">{formatMoney(lease.rentCredited)}</span> of {formatMoney(lease.totalRent)}
              </span>
              <span>Ends {formatDate(lease.endsAt)}</span>
            </div>
          </li>
        );
      })}
    </ul>
  );
}
