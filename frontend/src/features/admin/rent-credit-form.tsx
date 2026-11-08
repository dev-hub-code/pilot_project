"use client";

import { useActionState } from "react";
import { Check } from "lucide-react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SubmitButton } from "@/components/ui/submit-button";
import type { FormState } from "@/validators/form-state";
import { setRentCreditIntervalAction } from "./rent-credit-actions";

export function RentCreditIntervalForm({ intervalMinutes, options }: {
  intervalMinutes: number;
  options: readonly { minutes: number; label: string }[];
}) {
  const [state, action] = useActionState<FormState, FormData>(setRentCreditIntervalAction, {});
  const error = state.fieldErrors?.intervalMinutes;
  return (
    <form action={action} className="space-y-4" noValidate>
      <FormFeedback state={state} />
      <fieldset aria-describedby={error ? "interval-error" : undefined}>
        <legend className="mb-2 text-sm font-medium">Credit returns</legend>
        <div className="grid gap-2 sm:grid-cols-2 xl:grid-cols-3">
          {options.map((o) => (
            <label key={o.minutes}
              className="group relative flex cursor-pointer items-center gap-3 rounded-xl border border-border bg-background/40 px-4 py-3 text-sm transition-colors hover:border-gold/50 has-[:checked]:border-gold has-[:checked]:bg-gold/10 has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-gold/40">
              <input type="radio" name="intervalMinutes" value={o.minutes} defaultChecked={o.minutes === intervalMinutes} className="peer sr-only" />
              <span aria-hidden="true"
                className="flex size-5 shrink-0 items-center justify-center rounded-full border border-border bg-surface text-transparent transition-colors peer-checked:border-gold peer-checked:bg-gold peer-checked:text-on-gold">
                <Check className="size-3" />
              </span>
              <span className="min-w-0">
                <span className="block font-medium">{o.label.charAt(0).toUpperCase() + o.label.slice(1)}</span>
                {o.minutes === intervalMinutes && <span className="block text-xs text-gold-text">In use now</span>}
              </span>
            </label>
          ))}
        </div>
        {error && <p id="interval-error" role="alert" className="mt-2 text-sm text-rose-600">{error}</p>}
      </fieldset>
      <SubmitButton confirm="Change how often returns are credited to investors' wallets? It applies from the next run.">Save</SubmitButton>
    </form>
  );
}
