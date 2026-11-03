"use client";

import { useActionState, useEffect, useState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SelectField } from "@/components/ui/select-field";
import { SubmitButton } from "@/components/ui/submit-button";
import { CATEGORY_LABEL, CATEGORY_ORDER, PRIORITY_LABEL, PRIORITY_ORDER } from "@/features/support/labels";
import type { CustomerOption, SupportCategory, TicketCategory } from "@/types/support";
import type { FormState } from "@/validators/form-state";

type Action = (state: FormState, formData: FormData) => Promise<FormState>;

const input = "w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm outline-none focus:border-gold focus:ring-1 focus:ring-gold";

/** A ticket opened on a customer's behalf; the message is visible to them. */
export function StaffTicketForm({ action, categories, agents }: {
  action: Action;
  categories: SupportCategory[];
  agents: { value: string; label: string }[];
}) {
  const [state, formAction] = useActionState<FormState, FormData>(action, {});
  const e = state.fieldErrors ?? {};
  const [category, setCategory] = useState<TicketCategory>((state.values?.category as TicketCategory) ?? "OTHER");
  const subcategories = categories.filter((c) => c.parentId !== null && c.category === category && c.active);
  return (
    <form action={formAction} className="space-y-4" noValidate>
      <FormFeedback state={state} />
      <CustomerPicker error={e.customerId} />
      <div className="space-y-1.5">
        <label htmlFor="subject" className="block text-sm font-medium">Subject</label>
        <input id="subject" name="subject" maxLength={200} defaultValue={state.values?.subject} className={input}
          aria-invalid={e.subject ? true : undefined} />
        {e.subject && <p className="text-xs text-rose-600 dark:text-rose-400">{e.subject}</p>}
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-1.5">
          <label htmlFor="category" className="block text-sm font-medium">Category</label>
          <select id="category" name="category" value={category} onChange={(ev) => setCategory(ev.target.value as TicketCategory)} className={input}>
            {CATEGORY_ORDER.map((c) => <option key={c} value={c}>{CATEGORY_LABEL[c]}</option>)}
          </select>
        </div>
        <SelectField key={category} label="Subcategory" name="subcategoryId" placeholder="—"
          defaultValue={state.values?.category === category ? state.values?.subcategoryId ?? "" : ""}
          options={subcategories.map((c) => ({ value: c.id, label: c.name }))} />
        <SelectField label="Priority" name="priority" placeholder="Category default" defaultValue={state.values?.priority ?? ""}
          options={PRIORITY_ORDER.map((p) => ({ value: p, label: PRIORITY_LABEL[p] }))} />
        <SelectField label="Assign to" name="assigneeId" placeholder="Automatic / unassigned" defaultValue={state.values?.assigneeId ?? ""}
          options={agents} />
      </div>
      <div className="space-y-1.5">
        <label htmlFor="message" className="block text-sm font-medium">Message to the customer</label>
        <textarea id="message" name="message" rows={6} maxLength={8000} defaultValue={state.values?.message} className={input}
          aria-invalid={e.message ? true : undefined} />
        <p className="text-xs text-muted">The customer sees this message and is notified. Use an internal note on the ticket for anything private.</p>
        {e.message && <p className="text-xs text-rose-600 dark:text-rose-400">{e.message}</p>}
      </div>
      <SubmitButton>Open ticket</SubmitButton>
    </form>
  );
}

function CustomerPicker({ error }: { error?: string }) {
  const [q, setQ] = useState("");
  const [found, setFound] = useState<CustomerOption[]>([]);
  const [chosen, setChosen] = useState<CustomerOption | null>(null);
  const term = q.trim();
  useEffect(() => {
    if (chosen || term.length < 2) return;
    const controller = new AbortController();
    const timer = setTimeout(() => {
      fetch(`/admin/support/new/customers?${new URLSearchParams({ q: term })}`, { signal: controller.signal })
        .then((r) => (r.ok ? r.json() : []))
        .then((list: CustomerOption[]) => setFound(list))
        .catch(() => undefined);
    }, 300);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [term, chosen]);
  if (chosen) {
    return (
      <div className="space-y-1.5">
        <span className="block text-sm font-medium">Customer</span>
        <input type="hidden" name="customerId" value={chosen.id} />
        <div className="flex items-center justify-between gap-3 rounded-lg border border-border px-3 py-2 text-sm">
          <span>{chosen.name}{chosen.userCode && <span className="ml-2 font-mono text-xs text-muted">{chosen.userCode}</span>}
            {chosen.email && <span className="ml-2 text-muted">{chosen.email}</span>}</span>
          <button type="button" onClick={() => setChosen(null)} className="text-gold-text hover:underline">Change</button>
        </div>
      </div>
    );
  }
  const results = term.length < 2 ? [] : found;
  return (
    <div className="space-y-1.5">
      <label htmlFor="customer-search" className="block text-sm font-medium">Customer</label>
      <input id="customer-search" type="search" value={q} onChange={(ev) => setQ(ev.target.value)} maxLength={100}
        placeholder="Name, email or customer code" className={input} aria-invalid={error ? true : undefined} autoComplete="off" />
      {results.length > 0 && (
        <ul className="divide-y divide-border rounded-lg border border-border text-sm" role="listbox" aria-label="Matching customers">
          {results.map((c) => (
            <li key={c.id}>
              <button type="button" role="option" aria-selected={false} onClick={() => setChosen(c)}
                className="block w-full px-3 py-2 text-left hover:bg-background">
                {c.name}{c.userCode && <span className="ml-2 font-mono text-xs text-muted">{c.userCode}</span>}
                {c.email && <span className="ml-2 text-muted">{c.email}</span>}
              </button>
            </li>
          ))}
        </ul>
      )}
      {term.length >= 2 && results.length === 0 && <p className="text-xs text-muted">No matching customer yet…</p>}
      {error && <p className="text-xs text-rose-600 dark:text-rose-400">{error}</p>}
    </div>
  );
}
