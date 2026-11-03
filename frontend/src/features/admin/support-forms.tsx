"use client";

import { useActionState, useId } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SelectField } from "@/components/ui/select-field";
import { SubmitButton } from "@/components/ui/submit-button";
import type { FormState } from "@/validators/form-state";

type Action = (state: FormState, formData: FormData) => Promise<FormState>;

/** One setting (status, priority, assignee) changed with a select and a button. */
export function SettingForm({ action, label, options, defaultValue, placeholder, submitLabel }: {
  action: Action;
  label: string;
  options: { value: string; label: string }[];
  defaultValue?: string;
  placeholder?: string;
  submitLabel: string;
}) {
  const [state, formAction] = useActionState<FormState, FormData>(action, {});
  // Several of these share a page: each label must point at its own select.
  const id = useId();
  return (
    <form action={formAction} className="space-y-3" noValidate>
      <FormFeedback state={state} />
      <SelectField id={id} label={label} name="value" defaultValue={defaultValue} placeholder={placeholder} options={options} />
      <SubmitButton variant="secondary">{submitLabel}</SubmitButton>
    </form>
  );
}

/** Escalates a ticket: why, to which level (next by default) and, optionally, who owns it from here. */
export function EscalateForm({ action, levels, agents, current }: {
  action: Action;
  levels: { level: number; name: string }[];
  agents: { value: string; label: string }[];
  current: number;
}) {
  const [state, formAction] = useActionState<FormState, FormData>(action, {});
  const higher = levels.filter((l) => l.level > current);
  if (higher.length === 0 && levels.length > 0) return <p className="text-sm text-muted">Already at the highest level.</p>;
  return (
    <form action={formAction} className="space-y-3" noValidate>
      <FormFeedback state={state} />
      <SelectField label="Level" name="level" defaultValue={state.values?.level ?? ""} placeholder="Next level"
        options={higher.map((l) => ({ value: String(l.level), label: `L${l.level} · ${l.name}` }))} />
      {agents.length > 0 && (
        <SelectField label="Owner (optional)" name="ownerId" defaultValue={state.values?.ownerId ?? ""} placeholder="Keep current assignee"
          options={agents} />
      )}
      <div className="space-y-1.5">
        <label htmlFor="escalate-reason" className="block text-sm font-medium">Reason</label>
        <textarea id="escalate-reason" name="reason" rows={3} maxLength={500} defaultValue={state.success ? "" : state.values?.reason}
          aria-invalid={state.fieldErrors?.reason ? true : undefined}
          className="w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm outline-none focus:border-gold focus:ring-1 focus:ring-gold" />
        {state.fieldErrors?.reason && <p className="text-xs text-rose-600 dark:text-rose-400">{state.fieldErrors.reason}</p>}
      </div>
      <SubmitButton variant="secondary">Escalate</SubmitButton>
    </form>
  );
}
