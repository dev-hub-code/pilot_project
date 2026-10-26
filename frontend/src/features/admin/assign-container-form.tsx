"use client";

import { type FormEvent, useActionState, useRef, useState, useTransition } from "react";
import { Button } from "@/components/ui/button";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextField } from "@/components/ui/text-field";
import type { FormState } from "@/validators/form-state";

type Action = (state: FormState, formData: FormData) => Promise<FormState>;
type Generate = () => Promise<{ containerNumber?: string; error?: string }>;

/** Container numbers are capitals (e.g. SLSU1005013): letters are made upper case as they are typed. */
function upperCase(event: FormEvent<HTMLInputElement>) {
  const input = event.currentTarget;
  const upper = input.value.toUpperCase();
  if (upper === input.value) return;
  const { selectionStart, selectionEnd } = input;
  input.value = upper;
  input.setSelectionRange(selectionStart, selectionEnd);
}

/**
 * Finance types the number of the container it assigns (or generates a new one), and optionally where it is.
 */
export function AssignContainerForm({ action, generate, investorName, typeLabel }: {
  action: Action;
  generate: Generate;
  investorName: string;
  typeLabel: string;
}) {
  const [state, formAction] = useActionState<FormState, FormData>(action, {});
  const [generating, startGenerating] = useTransition();
  const [generateError, setGenerateError] = useState<string>();
  const number = useRef<HTMLInputElement>(null);
  const e = state.fieldErrors ?? {};

  function fillGenerated() {
    startGenerating(async () => {
      const result = await generate();
      setGenerateError(result.error);
      if (result.containerNumber && number.current) {
        number.current.value = result.containerNumber;
        number.current.focus();
      }
    });
  }

  return (
    <form action={formAction} className="space-y-3" noValidate>
      <FormFeedback state={state} />
      <div className="flex items-end gap-2">
        <div className="min-w-0 flex-1">
          <TextField ref={number} label={`${typeLabel} container number`} name="containerNumber" placeholder="e.g. SLSU1005013"
            autoComplete="off" spellCheck={false} autoCapitalize="characters" onInput={upperCase}
            defaultValue={state.success ? "" : state.values?.containerNumber} error={e.containerNumber ?? generateError} />
        </div>
        {/* Aligned with the input when there is no error under it. */}
        <Button type="button" variant="quiet" onClick={fillGenerated} disabled={generating}
          className={e.containerNumber || generateError ? "mb-[1.375rem]" : undefined}>
          {generating ? "Generating…" : "Generate"}
        </Button>
      </div>
      <TextField label="Location (optional)" name="currentLocation" placeholder="e.g. Nhava Sheva (JNPT), Mumbai"
        defaultValue={state.success ? "" : state.values?.currentLocation} error={e.currentLocation} />
      <SubmitButton className="w-full" pendingLabel="Assigning…"
        confirm={`Assign this container to ${investorName}? Their lease starts and rent is credited every hour from the next full hour.`}>
        Assign container
      </SubmitButton>
    </form>
  );
}
