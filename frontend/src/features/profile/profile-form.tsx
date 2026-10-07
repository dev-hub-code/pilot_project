"use client";

import { useActionState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SelectField } from "@/components/ui/select-field";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextField } from "@/components/ui/text-field";
import { COUNTRY_OPTIONS } from "@/lib/countries";
import type { Profile } from "@/types/user";
import type { FormState } from "@/validators/form-state";
import { updateProfileAction } from "./actions";

export function ProfileForm({ profile }: { profile: Profile }) {
  const [state, action] = useActionState<FormState, FormData>(updateProfileAction, {});
  const e = state.fieldErrors ?? {};
  const v = (name: string, fallback: string | null) => state.values?.[name] ?? fallback ?? "";
  const nameLocked = profile.kycStatus === "PENDING" || profile.kycStatus === "APPROVED";

  return (
    <form action={action} className="space-y-6" noValidate>
      <FormFeedback state={state} />
      <fieldset className="grid gap-4 sm:grid-cols-2">
        <legend className="mb-3 text-sm font-medium text-muted">Personal</legend>
        <TextField label="First name" name="firstName" defaultValue={v("firstName", profile.firstName)}
          readOnly={nameLocked} error={e.firstName} autoComplete="given-name" />
        <TextField label="Last name" name="lastName" defaultValue={v("lastName", profile.lastName)}
          readOnly={nameLocked} error={e.lastName} autoComplete="family-name" />
        <TextField label="Date of birth" name="dateOfBirth" type="date" defaultValue={v("dateOfBirth", profile.dateOfBirth)}
          error={e.dateOfBirth} autoComplete="bday" />
        <SelectField label="Nationality" name="nationality" options={COUNTRY_OPTIONS} placeholder="Select…"
          defaultValue={v("nationality", profile.nationality)} error={e.nationality} />
        <TextField label="Mobile phone" name="phone" type="tel" placeholder="+14155550123"
          defaultValue={v("phone", profile.phone)} error={e.phone} autoComplete="tel" />
        <TextField label="Email" name="email" value={profile.email} readOnly disabled />
      </fieldset>
      {nameLocked && (
        <p className="-mt-3 text-xs text-muted">Your name is locked during and after identity verification.</p>
      )}

      <fieldset className="grid gap-4 sm:grid-cols-2">
        <legend className="mb-3 text-sm font-medium text-muted">Address</legend>
        <TextField label="Address line 1" name="line1" defaultValue={v("line1", profile.address.line1)}
          error={e.line1} autoComplete="address-line1" />
        <TextField label="Address line 2" name="line2" defaultValue={v("line2", profile.address.line2)}
          error={e.line2} autoComplete="address-line2" />
        <TextField label="City" name="city" defaultValue={v("city", profile.address.city)} error={e.city}
          autoComplete="address-level2" />
        <TextField label="State / region" name="stateRegion" defaultValue={v("stateRegion", profile.address.stateRegion)}
          error={e.stateRegion} autoComplete="address-level1" />
        <TextField label="Postal code" name="postalCode" defaultValue={v("postalCode", profile.address.postalCode)}
          error={e.postalCode} autoComplete="postal-code" />
        <SelectField label="Country" name="country" options={COUNTRY_OPTIONS} placeholder="Select…"
          defaultValue={v("country", profile.address.country)} error={e["address.country"] ?? e.country} />
      </fieldset>

      <SubmitButton>Save profile</SubmitButton>
    </form>
  );
}
