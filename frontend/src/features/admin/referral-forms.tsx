"use client";

import { useActionState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextField } from "@/components/ui/text-field";
import type { FormState } from "@/validators/form-state";
import { scheduleRatesAction } from "./referral-actions";

export function ScheduleRatesForm({ current }: { current: number[] }) {
  const [state, action] = useActionState<FormState, FormData>(scheduleRatesAction, {});
  const e = state.fieldErrors ?? {};
  const v = (name: string, fallback?: string) => state.values?.[name] ?? fallback;
  return (
    <form action={action} className="space-y-4" noValidate>
      <FormFeedback state={state} />
      <div className="grid gap-4 sm:grid-cols-4">
        {[1, 2, 3, 4].map((level) => (
          <TextField key={level} label={`Level ${level} (%)`} name={`level${level}`} inputMode="decimal"
            defaultValue={v(`level${level}`, String(current[level - 1] ?? ""))} error={e[`level${level}`]} />
        ))}
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label="Takes effect (UTC, empty = now)" name="effectiveFrom" type="datetime-local"
          defaultValue={v("effectiveFrom")} error={e.effectiveFrom} />
        <TextField label="Reason" name="reason" defaultValue={v("reason")} error={e.reason} />
      </div>
      <SubmitButton confirm="Schedule these referral rates? Rates cannot be changed once they take effect.">Schedule rates</SubmitButton>
    </form>
  );
}
