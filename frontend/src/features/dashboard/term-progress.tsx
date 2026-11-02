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
    <ul className="space-y-5">
      {leases.map((lease) => (
        <li key={lease.holdingId} className="space-y-2 text-sm">
          <div className="flex flex-wrap items-baseline justify-between gap-x-4">
            <span className="font-medium">{lease.productTitle ?? "Investment"}</span>
            <span className="text-muted tabular-nums">
              {formatMoney(lease.rentCredited)} of {formatMoney(lease.totalRent)}
            </span>
          </div>
          <ProgressBar percent={leasePercent(lease)} label={`${lease.productTitle ?? "Investment"} term progress`} />
          <p className="text-xs text-muted">{Math.round(leasePercent(lease))}% through · ends {formatDate(lease.endsAt)}</p>
        </li>
      ))}
    </ul>
  );
}
