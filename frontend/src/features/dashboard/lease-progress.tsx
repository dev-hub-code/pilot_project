import Link from "next/link";
import { leaseNote, leasePercent } from "@/features/earnings/labels";
import type { LeaseRent } from "@/types/earning";
import { formatDate } from "@/utils/format";
import { formatMoney } from "@/utils/money";

const SHOWN = 4;

/** How far each running lease has been credited, the one ending soonest first. */
export function LeaseProgress({ leases }: { leases: readonly LeaseRent[] }) {
  const active = leases.filter((l) => l.status === "RUNNING").sort((a, b) => a.endsAt.localeCompare(b.endsAt));
  const shown = active.slice(0, SHOWN);

  return (
    <div className="space-y-5">
      <ul className="space-y-5">
        {shown.map((l) => {
          const percent = Math.round(leasePercent(l));
          return (
            <li key={l.holdingId} className="space-y-2">
              <div className="flex items-baseline justify-between gap-3 text-sm">
                <span className="font-mono text-xs">{l.containerNumber ?? "Container"}</span>
                <span className="text-xs text-muted tabular-nums">{percent}% of the lease</span>
              </div>
              <div role="progressbar" aria-label={`${l.containerNumber ?? "Container"} lease credited`}
                aria-valuemin={0} aria-valuemax={100} aria-valuenow={percent}>
                <svg className="block h-2 w-full" aria-hidden="true">
                  <rect width="100%" height="100%" rx="4" className="fill-border" />
                  {percent > 0 && <rect width={`${percent}%`} height="100%" rx="4" className="fill-chart-1" />}
                </svg>
              </div>
              <p className="flex justify-between gap-3 text-xs text-muted">
                <span>{formatMoney(l.rentCredited)} of {formatMoney(l.totalRent)} credited</span>
                <span>{leaseNote(l).startsWith("Credited") ? `Ends ${formatDate(l.endsAt)}` : leaseNote(l)}</span>
              </p>
            </li>
          );
        })}
      </ul>
      {active.length > SHOWN && (
        <Link href="/earnings" className="inline-block text-sm text-gold-text hover:underline">
          All {active.length} containers →
        </Link>
      )}
    </div>
  );
}
