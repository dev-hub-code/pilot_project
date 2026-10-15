"use client";

import { type FormEvent, useActionState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextField } from "@/components/ui/text-field";
import type { FormState } from "@/validators/form-state";

type Action = (state: FormState, formData: FormData) => Promise<FormState>;

/** Container numbers are capitals (e.g. SLSU1005013): letters are made upper case as they are typed. */
function upperCase(event: FormEvent<HTMLInputElement>) {
  const input = event.currentTarget;
  const upper = input.value.toUpperCase();
  if (upper === input.value) return;
  const { selectionStart, selectionEnd } = input;
  input.value = upper;
  input.setSelectionRange(selectionStart, selectionEnd);
}

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
        autoComplete="off" spellCheck={false} autoCapitalize="characters" onInput={upperCase}
        defaultValue={state.success ? "" : state.values?.containerNumber} error={e.containerNumber} />
      <TextField label="Location (optional)" name="currentLocation" placeholder="e.g. Nhava Sheva (JNPT), Mumbai"
        defaultValue={state.success ? "" : state.values?.currentLocation} error={e.currentLocation} />
      <SubmitButton className="w-full" pendingLabel="Assigning…"
        confirm={`Assign this container to ${investorName}? Their lease starts and rent is credited every hour from the next full hour.`}>
        Assign container
      </SubmitButton>
    </form>
  );
}
