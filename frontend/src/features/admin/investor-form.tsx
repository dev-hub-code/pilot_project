"use client";

import { useActionState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { PhoneField } from "@/components/ui/phone-field";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextField } from "@/components/ui/text-field";
import { DEFAULT_PHONE_COUNTRY } from "@/lib/phone";
import type { Country } from "@/types/location";
import { createInvestorAction } from "./investor-actions";
import type { CredentialsState } from "./staff-actions";
import { IssuedCredentials } from "./staff-forms";

export function CreateInvestorForm({ countries }: { countries: Country[] }) {
  const [state, action] = useActionState<CredentialsState, FormData>(createInvestorAction, {});
  const e = state.fieldErrors ?? {};
  if (state.credentials) {
    return (
      <div className="space-y-4">
        <FormFeedback state={state} />
        <IssuedCredentials credentials={state.credentials} />
        <a href={`/admin/users/${state.credentials.userId}`} className="text-sm text-gold-text hover:underline">Open their account</a>
      </div>
    );
  }
  return (
    <form action={action} className="space-y-4" noValidate>
      <FormFeedback state={state} />
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label="First name" name="firstName" defaultValue={state.values?.firstName} error={e.firstName} />
        <TextField label="Last name" name="lastName" defaultValue={state.values?.lastName} error={e.lastName} />
        <TextField label="Email" name="email" type="email" autoComplete="off" defaultValue={state.values?.email} error={e.email} />
        <PhoneField label="Mobile number" countries={countries} placeholder="98765 43210" required
          defaultNumber={state.values?.phone} defaultCountry={state.values?.phoneCountry || DEFAULT_PHONE_COUNTRY}
          error={e.phone ?? e.phoneCountry} />
        <TextField label="Referral code of who referred them (optional)" name="referralCode"
          defaultValue={state.values?.referralCode} error={e.referralCode} />
      </div>
      <label className="flex items-start gap-3 border border-border px-3 py-2 text-sm">
        <input type="checkbox" name="signupsJoinNetwork" className="mt-0.5 size-4" />
        <span>
          <span className="font-medium">People who sign up with their link join the network too</span>
          <span className="block text-xs text-muted">Those people then earn monthly on four levels, and pass this on. Otherwise they join on the online scheme.</span>
        </span>
      </label>
      <SubmitButton confirm="Create this network investor? They will earn referral commission every month on four levels.">Create investor account</SubmitButton>
      <p className="text-xs text-muted">The investor is emailed their sign-in details, with a temporary password to replace at first sign-in.</p>
    </form>
  );
}
