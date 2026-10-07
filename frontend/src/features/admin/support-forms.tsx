"use client";

import { useActionState } from "react";
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
  return (
    <form action={formAction} className="space-y-3" noValidate>
      <FormFeedback state={state} />
      <SelectField label={label} name="value" defaultValue={defaultValue} placeholder={placeholder} options={options} />
      <SubmitButton variant="secondary">{submitLabel}</SubmitButton>
    </form>
  );
}
