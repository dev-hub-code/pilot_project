"use client";

import { useActionState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SubmitButton } from "@/components/ui/submit-button";
import type { FormState } from "@/validators/form-state";

/** A comment box: for a comment on the task, or (optional) closing words when completing it. */
export function AgreementReviewNoteForm({ action, label, submitLabel, variant = "secondary", confirm, hint }: {
  action: (state: FormState, formData: FormData) => Promise<FormState>;
  label: string;
  submitLabel: string;
  variant?: "primary" | "secondary";
  confirm?: string;
  hint?: string;
}) {
  const [state, formAction] = useActionState<FormState, FormData>(action, {});
  const error = state.fieldErrors?.body;
  const id = `${submitLabel.replace(/\W+/g, "-").toLowerCase()}-body`;
  return (
    <form action={formAction} className="space-y-3" noValidate>
      <FormFeedback state={state} />
      <div className="space-y-1.5">
        <label className="block text-sm font-medium text-foreground" htmlFor={id}>{label}</label>
        <textarea id={id} name="body" rows={3} maxLength={2000} aria-invalid={error ? true : undefined}
          aria-describedby={hint ? `${id}-hint` : undefined}
          className="w-full rounded-none border border-border bg-surface px-3 py-2 text-sm outline-none focus:border-gold focus:ring-1 focus:ring-gold" />
        {hint && <p id={`${id}-hint`} className="text-xs text-muted">{hint}</p>}
        {error && <p className="text-xs text-rose-600 dark:text-rose-400">{error}</p>}
      </div>
      <SubmitButton variant={variant} confirm={confirm}>{submitLabel}</SubmitButton>
    </form>
  );
}
