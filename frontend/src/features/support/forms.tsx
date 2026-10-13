"use client";

import { useActionState } from "react";
import { FileField } from "@/components/ui/file-field";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SelectField } from "@/components/ui/select-field";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextField } from "@/components/ui/text-field";
import type { FormState } from "@/validators/form-state";
import { MAX_ATTACHMENTS } from "@/validators/support";
import { CATEGORY_LABEL } from "./labels";

type Action = (state: FormState, formData: FormData) => Promise<FormState>;

function MessageBox({ id, label, defaultValue, error }: { id: string; label: string; defaultValue?: string; error?: string }) {
  return (
    <div className="space-y-1.5">
      <label htmlFor={id} className="block text-sm font-medium text-foreground">{label}</label>
      <textarea id={id} name={id === "message" ? "message" : "body"} rows={5} maxLength={8000} defaultValue={defaultValue}
        aria-invalid={error ? true : undefined}
        className="w-full rounded-none border border-border bg-surface px-3 py-2 text-sm outline-none focus:border-gold focus:ring-1 focus:ring-gold" />
      {error && <p className="text-xs text-rose-600 dark:text-rose-400">{error}</p>}
    </div>
  );
}

function Files({ error }: { error?: string }) {
  return (
    <FileField label="Attachments" name="files" multiple accept="application/pdf,image/jpeg,image/png" error={error}
      hint={`Up to ${MAX_ATTACHMENTS} PDF, JPEG or PNG files, 5 MB each`} />
  );
}

export function OpenTicketForm({ action, related }: { action: Action; related: { value: string; label: string }[] }) {
  const [state, formAction] = useActionState<FormState, FormData>(action, {});
  const e = state.fieldErrors ?? {};
  return (
    <form action={formAction} className="space-y-4" noValidate>
      <FormFeedback state={state} />
      <TextField label="Subject" name="subject" defaultValue={state.values?.subject} error={e.subject} />
      <div className="grid gap-4 sm:grid-cols-2">
        <SelectField label="It's about" name="category" defaultValue={state.values?.category ?? "OTHER"}
          options={Object.entries(CATEGORY_LABEL).map(([value, label]) => ({ value, label }))} />
        <SelectField label="Related to (optional)" name="related" defaultValue={state.values?.related ?? ""} placeholder="—" options={related} />
      </div>
      <MessageBox id="message" label="How can we help?" defaultValue={state.values?.message} error={e.message} />
      <Files error={e.files} />
      <SubmitButton>Send to support</SubmitButton>
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
