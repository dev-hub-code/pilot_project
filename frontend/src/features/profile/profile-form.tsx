"use client";

import { useActionState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SelectField } from "@/components/ui/select-field";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextField } from "@/components/ui/text-field";
import { COUNTRY_OPTIONS } from "@/lib/countries";
import type { Country } from "@/types/location";
import type { Profile } from "@/types/user";
import type { FormState } from "@/validators/form-state";
import { DEFAULT_COUNTRY } from "@/validators/profile";
import { AddressLocationFields } from "./address-location-fields";
import { updateProfileAction } from "./actions";
import { DateField } from "@/components/ui/date-field";
import { formatDayMonthYear, latestBirthDate } from "@/utils/format";

/** @param countries for the address: picking one loads its states, then its cities */
export function ProfileForm({ profile, countries }: { profile: Profile; countries: Country[] }) {
  const [state, action] = useActionState<FormState, FormData>(updateProfileAction, {});
  const e = state.fieldErrors ?? {};
  const v = (name: string, fallback: string | null) => state.values?.[name] ?? fallback ?? "";

  return (
    <form action={action} className="space-y-6" noValidate>
      <FormFeedback state={state} />
      <fieldset className="grid gap-4 sm:grid-cols-2">
        <legend className="mb-3 text-sm font-medium text-muted">Personal</legend>
        <TextField label="First name" name="firstName" defaultValue={v("firstName", profile.firstName)}
          readOnly error={e.firstName} autoComplete="given-name" />
        <TextField label="Last name" name="lastName" defaultValue={v("lastName", profile.lastName)}
          readOnly error={e.lastName} autoComplete="family-name" />
        <DateField label="Date of birth" name="dateOfBirth" autoComplete="bday" max={latestBirthDate()}
          defaultValue={v("dateOfBirth", formatDayMonthYear(profile.dateOfBirth))} error={e.dateOfBirth} />
        <SelectField label="Nationality" name="nationality" options={COUNTRY_OPTIONS} placeholder="Select…"
          defaultValue={v("nationality", profile.nationality ?? DEFAULT_COUNTRY)} error={e.nationality} />
        <TextField label="Mobile phone" name="phone" type="tel" placeholder="98765 43210"
          defaultValue={v("phone", profile.phone)} error={e.phone} autoComplete="tel" />
        <TextField label="Email" name="email" value={profile.email} readOnly disabled />
      </fieldset>
      <p className="-mt-3 text-xs text-muted">
        Your name is as given at registration and cannot be changed. Contact support if it needs correcting.
      </p>

      <fieldset className="grid gap-4 sm:grid-cols-2">
        <legend className="mb-3 text-sm font-medium text-muted">Address</legend>
        <TextField label="Address line 1" name="line1" defaultValue={v("line1", profile.address.line1)}
          error={e.line1} autoComplete="address-line1" />
        <TextField label="Address line 2 (optional)" name="line2" defaultValue={v("line2", profile.address.line2)}
          error={e.line2} autoComplete="address-line2" />
        <AddressLocationFields
          countries={countries}
          defaults={{
            country: v("country", profile.address.country ?? DEFAULT_COUNTRY),
            stateRegion: v("stateRegion", profile.address.stateRegion),
            city: v("city", profile.address.city),
          }}
          errors={{ country: e.country, stateRegion: e.stateRegion, city: e.city }} />
        <TextField label="Postal code" name="postalCode" defaultValue={v("postalCode", profile.address.postalCode)}
          error={e.postalCode} autoComplete="postal-code" />
      </fieldset>

      <SubmitButton>Save profile</SubmitButton>
    </form>
  );
}
