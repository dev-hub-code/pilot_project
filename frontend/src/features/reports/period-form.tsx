import Link from "next/link";
import { Button } from "@/components/ui/button";
import { TextField } from "@/components/ui/text-field";
import { periodPresets } from "./report-preview";
import { CalendarRange } from "lucide-react";

/** A plain GET form: the period lives in the URL, so a report can be bookmarked and shared with colleagues. */
export function PeriodForm({ action, from, to, hidden = {} }: {
  action: string;
  from: string;
  to: string;
  hidden?: Record<string, string>;
}) {
  const query = (f: string, t: string) => new URLSearchParams({ ...hidden, from: f, to: t }).toString();
  return (
    <div className="flex flex-wrap items-end justify-between gap-3 rounded-2xl border border-border bg-surface p-4 shadow-sm">
      <nav aria-label="Period" className="inline-flex max-w-full flex-wrap gap-1 rounded-xl border border-border bg-background/60 p-1 text-sm">
        {periodPresets().map((p) => {
          const on = p.from === from && p.to === to;
          return (
            <Link key={p.label} href={`${action}?${query(p.from, p.to)}`} aria-current={on ? "page" : undefined}
              className={`rounded-lg px-3 py-1.5 font-medium whitespace-nowrap transition-colors duration-200 ${on
                ? "bg-ink text-on-ink shadow-sm dark:bg-gold dark:text-on-gold"
                : "text-muted hover:bg-surface hover:text-foreground"}`}>
              {p.label}
            </Link>
          );
        })}
      </nav>
      <form action={action} className="flex flex-wrap items-end gap-2">
        {Object.entries(hidden).map(([k, v]) => <input key={k} type="hidden" name={k} value={v} />)}
        <div className="w-40"><TextField label="From" name="from" type="date" defaultValue={from} /></div>
        <div className="w-40"><TextField label="To" name="to" type="date" defaultValue={to} /></div>
        <Button type="submit" variant="secondary" className="gap-2"><CalendarRange aria-hidden="true" className="size-4" /> Show</Button>
      </form>
    </div>
  );
}
