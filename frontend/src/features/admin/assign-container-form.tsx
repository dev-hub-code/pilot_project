"use client";

import { useActionState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextField } from "@/components/ui/text-field";
import type { FormState } from "@/validators/form-state";

type Action = (state: FormState, formData: FormData) => Promise<FormState>;

/** Finance types the number of the container it assigns, and optionally where it is. */
export function AssignContainerForm({ action, investorName, typeLabel }: {
  action: Action;
  investorName: string;
  typeLabel: string;
}) {
  const [state, formAction] = useActionState<FormState, FormData>(action, {});
  const e = state.fieldErrors ?? {};
  return (
    <form action={formAction} className="space-y-3" noValidate>
      <FormFeedback state={state} />
      <TextField label={`${typeLabel} container number`} name="containerNumber" placeholder="e.g. SLSU1005013"
        autoComplete="off" spellCheck={false} defaultValue={state.success ? "" : state.values?.containerNumber}
        error={e.containerNumber} />
      <TextField label="Location (optional)" name="currentLocation" placeholder="e.g. Nhava Sheva (JNPT), Mumbai"
        defaultValue={state.success ? "" : state.values?.currentLocation} error={e.currentLocation} />
      <SubmitButton className="w-full" pendingLabel="Assigning…"
        confirm={`Assign this container to ${investorName}? Their lease and monthly payouts start today.`}>
        Assign container
      </SubmitButton>
    </form>
  );
}
