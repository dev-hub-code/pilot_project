"use client";

import Link from "next/link";
import { useActionState, useEffect, useState } from "react";
import { ACCEPTED_FILE_TYPES } from "@/validators/profile";
import { FileField } from "@/components/ui/file-field";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SelectField } from "@/components/ui/select-field";
import { SubmitButton } from "@/components/ui/submit-button";
import type { FormState } from "@/validators/form-state";
import { MAX_ATTACHMENTS } from "@/validators/support";
import type { ArticleSummary, SupportCategory, TicketCategory } from "@/types/support";
import { CATEGORY_LABEL, CATEGORY_ORDER } from "./labels";

type Action = (state: FormState, formData: FormData) => Promise<FormState>;

function MessageBox({ id, label, defaultValue, error }: { id: string; label: string; defaultValue?: string; error?: string }) {
  return (
    <div className="space-y-1.5">
      <label htmlFor={id} className="block text-sm font-medium text-foreground">{label}</label>
      <textarea id={id} name={id === "message" ? "message" : "body"} rows={5} maxLength={8000} defaultValue={defaultValue}
        aria-invalid={error ? true : undefined}
        className="w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm outline-none focus:border-gold focus:ring-1 focus:ring-gold" />
      {error && <p className="text-xs text-rose-600 dark:text-rose-400">{error}</p>}
    </div>
  );
}

function Files({ error }: { error?: string }) {
  return (
    <FileField label="Attachments" name="files" multiple accept={ACCEPTED_FILE_TYPES} error={error}
      hint={`Up to ${MAX_ATTACHMENTS} JPG or PNG images, 2 MB each`} />
  );
}

export function OpenTicketForm({ action, related, categories }: {
  action: Action;
  related: { value: string; label: string }[];
  categories: SupportCategory[];
}) {
  const [state, formAction] = useActionState<FormState, FormData>(action, {});
  const e = state.fieldErrors ?? {};
  const [category, setCategory] = useState<TicketCategory>((state.values?.category as TicketCategory) ?? "OTHER");
  const [subject, setSubject] = useState(state.values?.subject ?? "");
  const subcategories = categories.filter((c) => c.parentId !== null && c.category === category);
  return (
    <form action={formAction} className="space-y-4" noValidate>
      <FormFeedback state={state} />
      <div className="space-y-1.5">
        <label htmlFor="subject" className="block text-sm font-medium text-foreground">Subject</label>
        <input id="subject" name="subject" value={subject} onChange={(ev) => setSubject(ev.target.value)} maxLength={200}
          aria-invalid={e.subject ? true : undefined}
          className="w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm outline-none focus:border-gold focus:ring-1 focus:ring-gold" />
        {e.subject && <p className="text-xs text-rose-600 dark:text-rose-400">{e.subject}</p>}
      </div>
      <SuggestedArticles query={subject} />
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-1.5">
          <label htmlFor="category" className="block text-sm font-medium text-foreground">It&apos;s about</label>
          <select id="category" name="category" value={category} onChange={(ev) => setCategory(ev.target.value as TicketCategory)}
            className="w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm outline-none focus:border-gold focus:ring-1 focus:ring-gold">
            {CATEGORY_ORDER.map((c) => <option key={c} value={c}>{CATEGORY_LABEL[c]}</option>)}
          </select>
        </div>
        {subcategories.length > 0 ? (
          <SelectField key={category} label="Topic (optional)" name="subcategoryId" placeholder="—"
            defaultValue={state.values?.category === category ? state.values?.subcategoryId ?? "" : ""}
            options={subcategories.map((c) => ({ value: c.id, label: c.name }))} />
        ) : <div />}
      </div>
      <SelectField label="Related to (optional)" name="related" defaultValue={state.values?.related ?? ""} placeholder="—" options={related} />
      <MessageBox id="message" label="How can we help?" defaultValue={state.values?.message} error={e.message} />
      <Files error={e.files} />
      <SubmitButton>Send to support</SubmitButton>
    </form>
  );
}

/** Help articles matching what the customer is typing, so they may find the answer without waiting. */
function SuggestedArticles({ query }: { query: string }) {
  const [articles, setArticles] = useState<ArticleSummary[]>([]);
  const q = query.trim();
  useEffect(() => {
    if (q.length < 4) return;
    const controller = new AbortController();
    const timer = setTimeout(() => {
      fetch(`/support/articles/suggest?${new URLSearchParams({ q })}`, { signal: controller.signal })
        .then((r) => (r.ok ? r.json() : []))
        .then((found: ArticleSummary[]) => setArticles(found))
        .catch(() => undefined);
    }, 350);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [q]);
  const shown = q.length < 4 ? [] : articles;
  if (shown.length === 0) return null;
  return (
    <div className="rounded-lg border border-border bg-background p-3 text-sm" aria-live="polite">
      <p className="font-medium">These articles may answer your question</p>
      <ul className="mt-2 space-y-1">
        {shown.map((a) => (
          <li key={a.id}>
            <Link href={`/support/articles/${a.slug}`} target="_blank" className="text-gold-text hover:underline">{a.title}</Link>
            <span className="text-muted"> · {a.summary}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

/** One to five stars and an optional comment, for a resolved request. */
export function RatingForm({ action }: { action: Action }) {
  const [state, formAction] = useActionState<FormState, FormData>(action, {});
  if (state.success) return <FormFeedback state={state} />;
  return (
    <form action={formAction} className="space-y-4" noValidate>
      <FormFeedback state={state} />
      <fieldset className="space-y-2">
        <legend className="text-sm font-medium">How satisfied are you with the help you received?</legend>
        <div className="flex flex-wrap gap-2">
          {[1, 2, 3, 4, 5].map((n) => (
            <label key={n} className="flex min-h-11 cursor-pointer items-center gap-2 rounded-lg border border-border px-3 has-checked:border-gold has-checked:bg-gold/10">
              <input type="radio" name="rating" value={n} defaultChecked={state.values?.rating === String(n)} className="size-4" />
              <span>{n}</span>
              <span className="text-xs text-muted">{["", "Very poor", "Poor", "Okay", "Good", "Excellent"][n]}</span>
            </label>
          ))}
        </div>
        {state.fieldErrors?.rating && <p className="text-xs text-rose-600 dark:text-rose-400">{state.fieldErrors.rating}</p>}
      </fieldset>
      <div className="space-y-1.5">
        <label htmlFor="comment" className="block text-sm font-medium">Anything to add? (optional)</label>
        <textarea id="comment" name="comment" rows={3} maxLength={1000} defaultValue={state.values?.comment}
          className="w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm outline-none focus:border-gold focus:ring-1 focus:ring-gold" />
      </div>
      <SubmitButton variant="secondary">Send rating</SubmitButton>
    </form>
  );
}

/** Reopens a resolved request (within 24 hours, before it closes); the reason is posted to the conversation. */
export function ReopenForm({ action }: { action: Action }) {
  const [state, formAction] = useActionState<FormState, FormData>(action, {});
  return (
    <form action={formAction} className="space-y-4" noValidate>
      <FormFeedback state={state} />
      <MessageBox id="body" label="What do you still need help with?" defaultValue={state.success ? "" : state.values?.body}
        error={state.fieldErrors?.body} />
      <SubmitButton pendingLabel="Reopening…">Reopen request</SubmitButton>
    </form>
  );
}

/** Reply box; staff forms add the internal-note switch through {@code extra}. */
export function ReplyForm({ action, extra, submitLabel = "Send reply" }: { action: Action; extra?: React.ReactNode; submitLabel?: string }) {
  const [state, formAction] = useActionState<FormState, FormData>(action, {});
  return (
    <form action={formAction} className="space-y-4" noValidate>
      <FormFeedback state={state} />
      <MessageBox id="body" label="Your message" defaultValue={state.success ? "" : state.values?.body} error={state.fieldErrors?.body} />
      <Files error={state.fieldErrors?.files} />
      {extra}
      <SubmitButton>{submitLabel}</SubmitButton>
    </form>
  );
}
