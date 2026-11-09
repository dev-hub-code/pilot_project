"use client";

import { useActionState, useEffect, useState } from "react";
import Link from "next/link";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SubmitButton } from "@/components/ui/submit-button";
import type { FormState } from "@/validators/form-state";

type Action = (state: FormState, formData: FormData) => Promise<FormState>;

export const BULK_FORM = "bulk-assign";

/**
 * Assigns the tickets ticked in the queue (their checkboxes belong to this form) to one agent, or spreads them by
 * round-robin or least-loaded.
 */
export function BulkAssignForm({ action, agents }: { action: Action; agents: { value: string; label: string }[] }) {
  const [state, formAction] = useActionState<FormState, FormData>(action, {});
  return (
    <form id={BULK_FORM} action={formAction} className="flex flex-wrap items-end gap-3 rounded-2xl border border-gold/30 bg-gold/5 p-4 text-sm shadow-sm">
      <label className="flex min-h-10 items-center gap-2 self-center"><SelectAll /> All on this page</label>
      <div className="min-w-0 flex-1 space-y-1 sm:flex-none">
        <label htmlFor="bulk-target" className="block font-medium">Assign ticked tickets to</label>
        <select id="bulk-target" name="target" defaultValue=""
          className="w-full min-w-0 rounded-lg border sm:w-auto sm:min-w-56 border-border bg-surface px-3 py-2 outline-none focus:border-gold focus:ring-1 focus:ring-gold">
          <option value="" disabled>Choose…</option>
          <optgroup label="Spread automatically">
            <option value="strategy:LEAST_LOADED">Least-loaded agent</option>
            <option value="strategy:ROUND_ROBIN">Round-robin</option>
          </optgroup>
          {agents.length > 0 && (
            <optgroup label="Agent">
              {agents.map((a) => <option key={a.value} value={`agent:${a.value}`}>{a.label}</option>)}
            </optgroup>
          )}
        </select>
      </div>
      <SubmitButton variant="secondary">Assign</SubmitButton>
      <div className="w-full empty:hidden"><FormFeedback state={state} /></div>
    </form>
  );
}

/** Ticks every ticket on the page. */
export function SelectAll() {
  return (
    <input type="checkbox" className="size-4 accent-[var(--gold)]"
      onChange={(e) => document.querySelectorAll<HTMLInputElement>(`input[form="${BULK_FORM}"][name="ticketId"]`)
        .forEach((box) => { box.checked = e.target.checked; })} />
  );
}

const KEY = "support.recentSearches";

function readRecent(): string[] {
  try {
    const value = JSON.parse(window.localStorage.getItem(KEY) ?? "[]");
    return Array.isArray(value) ? value.filter((v): v is string => typeof v === "string").slice(0, 5) : [];
  } catch {
    return [];
  }
}

/** The last searches typed in the queue, kept in this browser only. */
export function RecentSearches({ current }: { current: string }) {
  const [recent, setRecent] = useState<string[]>([]);
  useEffect(() => {
    const list = readRecent();
    const next = current ? [current, ...list.filter((q) => q !== current)].slice(0, 5) : list;
    try {
      window.localStorage.setItem(KEY, JSON.stringify(next));
    } catch {
      // Storage unavailable (private mode): recent searches are a convenience only.
    }
    // Reading browser storage after mount: there is no server value to render first.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setRecent(next.filter((q) => q !== current));
  }, [current]);
  if (recent.length === 0) return null;
  return (
    <p className="flex flex-wrap items-center gap-2 text-xs text-muted">
      Recent:
      {recent.map((q) => (
        <Link key={q} href={`/platform/support?${new URLSearchParams({ view: "all", q })}`} className="inline-flex min-h-7 items-center rounded-full border border-border bg-background/60 px-2.5 transition-colors hover:border-gold/40 hover:text-foreground">{q}</Link>
      ))}
    </p>
  );
}
