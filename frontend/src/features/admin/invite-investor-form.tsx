"use client";

import { useActionState } from "react";
import { Network } from "lucide-react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextField } from "@/components/ui/text-field";
import type { FormState } from "@/validators/form-state";
import { inviteInvestorAction } from "./investor-actions";

/** Any employee emails a client a one-time link to open their own network investor account. */
export function InviteInvestorForm() {
  const [state, action] = useActionState<FormState, FormData>(inviteInvestorAction, {});
  const e = state.fieldErrors ?? {};
  // After a successful send the form starts empty for the next client.
  const values = state.success ? {} : (state.values ?? {});
  return (
    <form key={state.success ?? "form"} action={action} className="space-y-5" noValidate>
      <FormFeedback state={state} />
      <TextField label="Client's email" name="email" type="email" autoComplete="off" required
        defaultValue={values.email} error={e.email} />
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label="First name" name="firstName" required defaultValue={values.firstName} error={e.firstName} />
        <TextField label="Last name" name="lastName" required defaultValue={values.lastName} error={e.lastName} />
      </div>
      <label className="flex cursor-pointer items-start gap-3 rounded-xl border border-border bg-background/50 p-4 text-sm transition-colors hover:border-gold/40 has-[:checked]:border-gold/50 has-[:checked]:bg-gold/[0.05]">
        <span aria-hidden="true" className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-gold/10 text-gold-text">
          <Network className="size-4" />
        </span>
        <input type="checkbox" name="signupsJoinNetwork" className="order-last mt-1 size-4"
          defaultChecked={state.values?.signupsJoinNetwork === undefined ? true : state.values.signupsJoinNetwork === "on"} />
        <span className="flex-1">
          <span className="font-medium">People who sign up with their link join the network too</span>
          <span className="block text-xs text-muted">Those people then earn monthly on four levels, and pass this on. Otherwise they join on the online scheme.</span>
        </span>
      </label>
      <div className="flex flex-wrap items-center gap-4 border-t border-border pt-5">
        <SubmitButton>Send invitation</SubmitButton>
        <p className="flex-1 text-xs text-muted">The link works once, for 7 days.</p>
      </div>
    </form>
  );
}
