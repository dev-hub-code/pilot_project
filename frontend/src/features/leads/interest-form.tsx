"use client";

import { useActionState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SelectField } from "@/components/ui/select-field";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextField } from "@/components/ui/text-field";
import { COUNTRY_OPTIONS } from "@/lib/countries";
import type { FormState } from "@/validators/form-state";
import { INTEREST_LABEL } from "./labels";
import { submitInterestAction } from "./public-actions";

export function InterestForm() {
  const [state, action] = useActionState<FormState, FormData>(submitInterestAction, {});
  const e = state.fieldErrors ?? {};
  const v = (name: string) => state.values?.[name];
  if (state.success) return <FormFeedback state={state} />;
  return (
    <form action={action} className="space-y-4" noValidate>
      <FormFeedback state={state} />
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField id="lead-first" label="First name" name="firstName" autoComplete="given-name" defaultValue={v("firstName")} error={e.firstName} />
        <TextField id="lead-last" label="Last name" name="lastName" autoComplete="family-name" defaultValue={v("lastName")} error={e.lastName} />
        <TextField id="lead-email" label="Email" name="email" type="email" autoComplete="email" defaultValue={v("email")} error={e.email} />
        <TextField id="lead-phone" label="Phone (optional)" name="phone" type="tel" autoComplete="tel" defaultValue={v("phone")} error={e.phone} />
        <SelectField id="lead-country" label="Country (optional)" name="country" defaultValue={v("country") ?? ""} placeholder="—"
          options={COUNTRY_OPTIONS} />
        <SelectField id="lead-interest" label="Interested in" name="interest" defaultValue={v("interest") ?? "UNSURE"}
          options={Object.entries(INTEREST_LABEL).map(([value, label]) => ({ value, label }))} />
      </div>
      <div className="space-y-1.5">
        <label htmlFor="lead-message" className="block text-xs font-medium uppercase tracking-[0.08em] text-muted">Message (optional)</label>
        <textarea id="lead-message" name="message" rows={3} maxLength={2000} defaultValue={v("message")}
          className="w-full rounded-none border border-border bg-surface px-3 py-2 text-sm outline-none focus:border-gold focus:ring-1 focus:ring-gold" />
      </div>
      {/* Honeypot: invisible to people and assistive technology, filled in by bots. */}
      <div aria-hidden="true" className="absolute -left-[10000px] h-px w-px overflow-hidden">
        <label htmlFor="lead-website">Website</label>
        <input id="lead-website" name="website" type="text" tabIndex={-1} autoComplete="off" />
      </div>
      <label className="flex items-start gap-3 text-sm">
        <input type="checkbox" name="consent" className="mt-0.5 size-4" defaultChecked={v("consent") === "on"} />
        <span>I agree that SeaLease may contact me about investing.{e.consent && <span className="block text-xs text-rose-600 dark:text-rose-400">{e.consent}</span>}</span>
      </label>
      <SubmitButton pendingLabel="Sending…">Talk to us</SubmitButton>
    </form>
  );
}
