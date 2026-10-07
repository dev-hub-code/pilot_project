"use client";

import { useActionState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SelectField } from "@/components/ui/select-field";
import { SubmitButton } from "@/components/ui/submit-button";
import type { FormState } from "@/validators/form-state";

type ReasonAction = (state: FormState, formData: FormData) => Promise<FormState>;
type PlainAction = (state: FormState) => Promise<FormState>;

/** A decision that needs a written, audited reason (reject, suspend, ...). */
export function ReasonForm({ action, label, submitLabel, variant = "danger", confirm }: {
  action: ReasonAction;
  label: string;
  submitLabel: string;
  variant?: "primary" | "secondary" | "danger";
  confirm?: string;
}) {
  const [state, formAction] = useActionState<FormState, FormData>(action, {});
  const error = state.fieldErrors?.reason;
  return (
    <form action={formAction} className="space-y-3" noValidate>
      <FormFeedback state={state} />
      <div className="space-y-1.5">
        <label className="block text-xs font-medium uppercase tracking-[0.08em] text-muted" htmlFor={`${submitLabel}-reason`}>{label}</label>
        <textarea id={`${submitLabel}-reason`} name="reason" rows={2} maxLength={500}
          aria-invalid={error ? true : undefined}
          className="w-full rounded-none border border-border bg-surface px-3 py-2 text-sm outline-none focus:border-gold focus:ring-1 focus:ring-gold" />
        {error && <p className="text-xs text-rose-600 dark:text-rose-400">{error}</p>}
      </div>
      <SubmitButton variant={variant} confirm={confirm}>{submitLabel}</SubmitButton>
    </form>
  );
}

/** A one-click decision (approve, verify) with confirmation. */
export function ConfirmForm({ action, submitLabel, confirm }: {
  action: PlainAction;
  submitLabel: string;
  confirm: string;
}) {
  const [state, formAction] = useActionState<FormState>(action, {});
  return (
    <form action={formAction} className="space-y-3">
      <FormFeedback state={state} />
      <SubmitButton confirm={confirm}>{submitLabel}</SubmitButton>
    </form>
  );
}

export function ClassifyForm({ action, current }: { action: ReasonAction; current: "RETAIL" | "HNI" }) {
  const [state, formAction] = useActionState<FormState, FormData>(action, {});
  return (
    <form action={formAction} className="space-y-3" noValidate>
      <FormFeedback state={state} />
      <SelectField label="Classification" name="investorType" defaultValue={current === "HNI" ? "RETAIL" : "HNI"}
        options={[{ value: "RETAIL", label: "Retail" }, { value: "HNI", label: "HNI (high net worth)" }]} />
      <div className="space-y-1.5">
        <label className="block text-xs font-medium uppercase tracking-[0.08em] text-muted" htmlFor="classify-reason">Evidence / reason</label>
        <textarea id="classify-reason" name="reason" rows={2} maxLength={500}
          className="w-full rounded-none border border-border bg-surface px-3 py-2 text-sm outline-none focus:border-gold focus:ring-1 focus:ring-gold" />
        {state.fieldErrors?.reason && <p className="text-xs text-rose-600 dark:text-rose-400">{state.fieldErrors.reason}</p>}
      </div>
      <SubmitButton variant="quiet">Update classification</SubmitButton>
    </form>
  );
}
