"use client";

import { useActionState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SelectField } from "@/components/ui/select-field";
import { SubmitButton } from "@/components/ui/submit-button";
import type { FormState } from "@/validators/form-state";
import { setRentCreditIntervalAction } from "./rent-credit-actions";

export function RentCreditIntervalForm({ intervalMinutes, options }: {
  intervalMinutes: number;
  options: readonly { minutes: number; label: string }[];
}) {
  const [state, action] = useActionState<FormState, FormData>(setRentCreditIntervalAction, {});
  const choices = options.map((o) => ({ value: String(o.minutes), label: o.label.charAt(0).toUpperCase() + o.label.slice(1) }));
  return (
    <form action={action} className="flex flex-wrap items-end gap-3" noValidate>
      <div className="w-full"><FormFeedback state={state} /></div>
      <div className="w-56">
        <SelectField label="Credit rent" name="intervalMinutes" defaultValue={String(intervalMinutes)} options={choices}
          error={state.fieldErrors?.intervalMinutes} />
      </div>
      <SubmitButton variant="secondary"
        confirm="Change how often rent is credited to investors' wallets? It applies from the next run.">Save</SubmitButton>
    </form>
  );
}
