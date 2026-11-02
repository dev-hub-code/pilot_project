"use client";

import { useActionState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SelectField } from "@/components/ui/select-field";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextField } from "@/components/ui/text-field";
import type { FormState } from "@/validators/form-state";

type Action = (state: FormState, formData: FormData) => Promise<FormState>;

export function DepartmentForm({ action, code, name, description }: {
  action: Action;
  code?: string;
  name?: string;
  description?: string;
}) {
  const [state, formAction] = useActionState<FormState, FormData>(action, {});
  const v = (field: string, fallback = "") => state.values?.[field] ?? fallback;
  return (
    <form action={formAction} className="space-y-4" noValidate>
      <FormFeedback state={state} />
      <div className="grid gap-4 sm:grid-cols-2">
        {code === undefined && (
          <TextField label="Code" name="code" defaultValue={v("code")} placeholder="e.g. TREASURY" autoComplete="off"
            error={state.fieldErrors?.code} />
        )}
        <TextField label="Name" name="name" defaultValue={v("name", name)} error={state.fieldErrors?.name} />
        <TextField label="Description" name="description" defaultValue={v("description", description)} />
      </div>
      <SubmitButton>{code === undefined ? "Create department" : "Save department"}</SubmitButton>
    </form>
  );
}

export function PersonForm({ action, label, submitLabel, people, primary = false, current, optional = false }: {
  action: Action;
  label: string;
  submitLabel: string;
  people: readonly { value: string; label: string }[];
  /** Offers making this the person's primary department. */
  primary?: boolean;
  current?: string;
  optional?: boolean;
}) {
  const [state, formAction] = useActionState<FormState, FormData>(action, {});
  return (
    <form action={formAction} className="space-y-3" noValidate>
      <FormFeedback state={state} />
      <SelectField label={label} name="userId" options={people} defaultValue={current ?? ""}
        placeholder={optional ? "No one" : "Select…"} />
      {primary && (
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" name="primary" /> This is their primary department
        </label>
      )}
      <SubmitButton variant="secondary">{submitLabel}</SubmitButton>
    </form>
  );
}
