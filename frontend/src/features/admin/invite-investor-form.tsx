"use client";

import { useActionState } from "react";
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
    <form key={state.success ?? "form"} action={action} className="space-y-4" noValidate>
      <FormFeedback state={state} />
      <TextField label="Client's email" name="email" type="email" autoComplete="off" required
        defaultValue={values.email} error={e.email} />
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label="First name" name="firstName" required defaultValue={values.firstName} error={e.firstName} />
        <TextField label="Last name" name="lastName" required defaultValue={values.lastName} error={e.lastName} />
      </div>
      <label className="flex items-start gap-3 border border-border px-3 py-2 text-sm">
        <input type="checkbox" name="signupsJoinNetwork" className="mt-0.5 size-4"
          defaultChecked={state.values?.signupsJoinNetwork === undefined ? true : state.values.signupsJoinNetwork === "on"} />
        <span>
          <span className="font-medium">People who sign up with their link join the network too</span>
          <span className="block text-xs text-muted">Those people then earn monthly on four levels, and pass this on. Otherwise they join on the online scheme.</span>
        </span>
      </label>
      <SubmitButton>Send invitation</SubmitButton>
      <p className="text-xs text-muted">
        The client gets an email with a link to open a network investor account: they enter their own details and choose a
        password. The link works once, for 7 days. Sending another invitation to the same address replaces the earlier link.
      </p>
    </form>
  );
}
