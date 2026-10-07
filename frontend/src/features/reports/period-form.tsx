import Link from "next/link";
import { Button } from "@/components/ui/button";
import { TextField } from "@/components/ui/text-field";
import { periodPresets } from "./report-preview";

/** A plain GET form: the period lives in the URL, so a report can be bookmarked and shared with colleagues. */
export function PeriodForm({ action, from, to, hidden = {} }: {
  action: string;
  from: string;
  to: string;
  hidden?: Record<string, string>;
}) {
  const query = (f: string, t: string) => new URLSearchParams({ ...hidden, from: f, to: t }).toString();
  return (
    <div className="space-y-3">
      <div className="flex flex-wrap gap-2 text-sm">
        {periodPresets().map((p) => (
          <Link key={p.label} href={`${action}?${query(p.from, p.to)}`}
            aria-current={p.from === from && p.to === to ? "page" : undefined}
            className={`rounded-full border px-3 py-1 ${p.from === from && p.to === to ? "border-foreground text-foreground" : "border-border text-muted"}`}>
            {p.label}
          </Link>
        ))}
      </div>
      <form action={action} className="flex flex-wrap items-end gap-3">
        {Object.entries(hidden).map(([k, v]) => <input key={k} type="hidden" name={k} value={v} />)}
        <div className="w-44"><TextField label="From" name="from" type="date" defaultValue={from} /></div>
        <div className="w-44"><TextField label="To" name="to" type="date" defaultValue={to} /></div>
        <Button type="submit" variant="secondary">Show</Button>
      </form>
    </div>
  );
}
