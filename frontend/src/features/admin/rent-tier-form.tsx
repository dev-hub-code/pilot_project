"use client";

import { useActionState, useState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SubmitButton } from "@/components/ui/submit-button";
import type { RentTier } from "@/types/marketplace";
import type { FormState } from "@/validators/form-state";
import { setRentTiersAction } from "./rent-tier-actions";

interface Row {
  key: number;
  from: string;
  to: string;
  percent: string;
}

const inputClass = "h-11 w-full rounded-none border border-border bg-surface px-3 text-sm tabular-nums outline-none focus:border-gold focus:ring-1 focus:ring-gold";

/** Whole rupees, as typed into the boxes: "499999.00" -> "499999". */
const plain = (amount: string) => String(Number(amount));

/** The rent bands as editable rows: from and to (inclusive) order value and the monthly rent %; the last may have no "to". */
export function RentTierForm({ tiers }: { tiers: readonly RentTier[] }) {
  const [state, action] = useActionState<FormState, FormData>(setRentTiersAction, {});
  const [rows, setRows] = useState<Row[]>(() => tiers.map((t, i) => ({
    key: i,
    from: plain(t.fromAmount.amount),
    to: t.toAmount ? plain(t.toAmount.amount) : "",
    percent: String(t.monthlyRentPercent),
  })));
  const [nextKey, setNextKey] = useState(tiers.length);

  const change = (key: number, field: "from" | "to" | "percent", value: string) =>
    setRows((current) => current.map((r) => (r.key === key ? { ...r, [field]: value } : r)));
  // A new band starts the rupee after the last one ends.
  const add = () => {
    const last = rows.at(-1);
    const end = Number(last?.to);
    setRows((current) => [...current, { key: nextKey, from: last?.to && end >= 0 ? String(end + 1) : "", to: "", percent: "" }]);
    setNextKey((k) => k + 1);
  };
  const remove = (key: number) => setRows((current) => current.filter((r) => r.key !== key));

  return (
    <form action={action} className="space-y-4" noValidate>
      <FormFeedback state={state} />
      <table className="w-full text-sm">
        <thead>
          <tr className="text-left text-xs text-muted">
            <th className="pb-2 font-medium">Order value from (₹)</th>
            <th className="pb-2 pl-3 font-medium">To (₹, blank = and above)</th>
            <th className="pb-2 pl-3 font-medium">Monthly rent (%)</th>
            <th className="pb-2" />
          </tr>
        </thead>
        <tbody>
          {rows.map((row, i) => (
            <tr key={row.key}>
              <td className="py-1 align-middle">
                <input name="fromAmount" value={row.from} inputMode="decimal" placeholder="e.g. 100000"
                  aria-label={`Band ${i + 1}: order value from`} onChange={(e) => change(row.key, "from", e.target.value)}
                  className={inputClass} />
              </td>
              <td className="py-1 pl-3 align-middle">
                <input name="toAmount" value={row.to} inputMode="decimal" placeholder={i === rows.length - 1 ? "and above" : "e.g. 499999"}
                  aria-label={`Band ${i + 1}: order value to`} onChange={(e) => change(row.key, "to", e.target.value)}
                  className={inputClass} />
              </td>
              <td className="py-1 pl-3 align-middle">
                <input name="monthlyRentPercent" value={row.percent} inputMode="decimal" placeholder="e.g. 5"
                  aria-label={`Band ${i + 1}: monthly rent percent`} onChange={(e) => change(row.key, "percent", e.target.value)}
                  className={inputClass} />
              </td>
              <td className="py-1 pl-3 text-right align-middle">
                {rows.length > 1 && (
                  <button type="button" onClick={() => remove(row.key)} className="text-xs text-muted underline hover:text-foreground">
                    Remove
                  </button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <div className="flex flex-wrap gap-3">
        <button type="button" onClick={add} disabled={rows.length >= 20}
          className="min-h-11 border border-dashed border-border px-4 text-sm hover:border-foreground/40 disabled:opacity-60">
          + Add band
        </button>
        <SubmitButton variant="secondary"
          confirm="Change the rent rates? Orders placed from now on earn the new rates; existing orders keep theirs.">
          Save rates
        </SubmitButton>
      </div>
    </form>
  );
}
