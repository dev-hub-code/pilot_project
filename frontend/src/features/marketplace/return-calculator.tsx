"use client";

import { useEffect, useRef, useState } from "react";
import { SelectField } from "@/components/ui/select-field";
import { TextField } from "@/components/ui/text-field";
import { leaseOptions, yearsLabel } from "@/features/orders/lease-years";
import type { ReturnProjection } from "@/types/marketplace";
import { formatMoney, formatPercent } from "@/utils/money";
import { projectReturnAction } from "./actions";
import { useInvestmentPlan } from "./investment-plan";

const CONTAINERS = /^\d{1,2}$/;
const DEBOUNCE_MS = 350;

/**
 * "Estimate your return": recalculates as the investor changes the containers or the lease, without reloading the
 * page. The figures come from the backend (rent by order value); the URL keeps the choice so a reload shows it again.
 */
export function ReturnCalculator({ productId, minYears, maxYears, initial }: {
  productId: string;
  minYears: number;
  maxYears: number;
  initial: ReturnProjection;
}) {
  const plan = useInvestmentPlan();
  const [containers, setContainers] = useState(String(initial.containers));
  const [years, setYears] = useState(String(initial.tenureYears));
  const [projection, setProjection] = useState(initial);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout>>(undefined);
  const latest = useRef(0);

  useEffect(() => () => clearTimeout(timer.current), []);

  function recalculate(nextContainers: string, nextYears: string) {
    clearTimeout(timer.current);
    latest.current++;
    setPending(false);
    // Still typing (empty): wait for a number before saying anything.
    if (nextContainers === "") {
      setError(null);
      plan?.setReady(false);
      return;
    }
    const count = Number(nextContainers);
    if (!CONTAINERS.test(nextContainers) || count < 1 || count > 50) {
      setError("Enter 1 to 50 containers.");
      plan?.setReady(false);
      return;
    }
    setError(null);
    plan?.setReady(true);
    plan?.setQuantity(nextContainers);
    plan?.setYears(nextYears);
    window.history.replaceState(null, "", `?containers=${count}&years=${nextYears}`);
    timer.current = setTimeout(async () => {
      const request = latest.current;
      setPending(true);
      const result = await projectReturnAction(productId, count, Number(nextYears));
      if (request !== latest.current) return; // a newer change is on its way
      setPending(false);
      if (result) setProjection(result);
      else setError("Couldn't calculate right now. Please try again.");
    }, DEBOUNCE_MS);
  }

  return (
    <>
      <div className="grid grid-cols-2 items-start gap-3 [&>*]:min-w-0">
        <TextField id="calculator" label="Containers" name="containers" type="number" inputMode="numeric" min={1} max={50}
          value={containers} error={error ?? undefined}
          onChange={(e) => {
            setContainers(e.target.value);
            recalculate(e.target.value, years);
          }} />
        <SelectField label="Lease" name="years" value={years} options={leaseOptions(minYears, maxYears)}
          onChange={(e) => {
            setYears(e.target.value);
            recalculate(containers, e.target.value);
          }} />
      </div>
      <div aria-live="polite" aria-busy={pending} className={`space-y-5 transition-opacity ${pending ? "opacity-50" : ""}`}>
        <dl className="grid grid-cols-[repeat(auto-fit,minmax(10rem,1fr))] gap-4 text-sm">
          <Projection label="You invest" value={formatMoney(projection.amount)} />
          <Projection label="Rent every month" value={formatMoney(projection.monthlyRent)}
            note={`${formatPercent(projection.monthlyRentPercent)} of ${formatMoney(projection.amount)}`} />
          <Projection label="Lease" value={`${yearsLabel(projection.tenureYears)} (${projection.payouts} payouts)`} />
        </dl>
        <p className="border-t border-border pt-3 text-sm">
          Total rent over the tenure: <strong className="font-display text-lg">{formatMoney(projection.totalRent)}</strong>
        </p>
        {projection.problems.length > 0 && (
          <ul className="space-y-1 text-sm text-amber-700 dark:text-amber-300">
            {projection.problems.map((problem) => <li key={problem}>• {problem}</li>)}
          </ul>
        )}
      </div>
    </>
  );
}

function Projection({ label, value, note }: { label: string; value: string; note?: string }) {
  return (
    <div>
      <dt className="text-[11px] tracking-[0.1em] text-muted uppercase">{label}</dt>
      <dd className="mt-1 font-display text-xl font-semibold">{value}</dd>
      {note && <dd className="mt-0.5 text-xs text-muted">{note}</dd>}
    </div>
  );
}
