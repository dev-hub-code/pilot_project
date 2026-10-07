import Link from "next/link";
import type { HoldingPayouts } from "@/types/earning";
import { formatDate } from "@/utils/format";
import { formatMoney } from "@/utils/money";

const SHOWN = 4;

/** How far each container's lease has paid out, soonest payout first. */
export function LeaseProgress({ holdings }: { holdings: readonly HoldingPayouts[] }) {
  const active = holdings
    .filter((h) => h.paid < h.installments)
    .sort((a, b) => (a.nextDueOn ?? "").localeCompare(b.nextDueOn ?? ""));
  const shown = active.slice(0, SHOWN);

  return (
    <div className="space-y-5">
      <ul className="space-y-5">
        {shown.map((h) => {
          const percent = Math.round((h.paid / h.installments) * 100);
          return (
            <li key={h.holdingId} className="space-y-2">
              <div className="flex items-baseline justify-between gap-3 text-sm">
                <span className="font-mono text-xs">{h.containerNumber ?? "Container"}</span>
                <span className="text-xs text-muted tabular-nums">{h.paid} of {h.installments} months paid</span>
              </div>
              <div role="progressbar" aria-label={`${h.containerNumber ?? "Container"} lease paid out`}
                aria-valuemin={0} aria-valuemax={h.installments} aria-valuenow={h.paid}>
                <svg className="block h-2 w-full" aria-hidden="true">
                  <rect width="100%" height="100%" rx="4" className="fill-border" />
                  {h.paid > 0 && <rect width={`${percent}%`} height="100%" rx="4" className="fill-chart-1" />}
                </svg>
              </div>
              <p className="flex justify-between gap-3 text-xs text-muted">
                <span>{formatMoney(h.received)} received</span>
                {h.nextDueOn && h.nextAmount && <span>Next {formatMoney(h.nextAmount)} on {formatDate(h.nextDueOn)}</span>}
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
