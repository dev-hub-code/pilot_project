"use client";

import { useActionState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SelectField } from "@/components/ui/select-field";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextField } from "@/components/ui/text-field";
import { COUNTRY_OPTIONS } from "@/lib/countries";
import type { Profile } from "@/types/user";
import type { FormState } from "@/validators/form-state";
import { updateTaxAction } from "./actions";

export function TaxForm({ profile }: { profile: Profile }) {
  const [state, action] = useActionState<FormState, FormData>(updateTaxAction, {});
  const e = state.fieldErrors ?? {};

  return (
    <form action={action} className="space-y-4" noValidate>
      <FormFeedback state={state} />
      {profile.taxIdMasked && (
        <p className="text-sm text-muted">
          Current tax ID: <span className="font-mono">{profile.taxIdMasked}</span>
          {profile.taxResidencyCountry && ` (${profile.taxResidencyCountry})`}
        </p>
      )}
      <div className="grid gap-4 sm:grid-cols-2">
        <SelectField label="Tax residency" name="taxResidencyCountry" options={COUNTRY_OPTIONS} placeholder="Select…"
          defaultValue={state.values?.taxResidencyCountry ?? profile.taxResidencyCountry ?? ""}
          error={e.taxResidencyCountry} />
        <TextField label={profile.taxIdMasked ? "New tax ID" : "Tax ID"} name="taxId" autoComplete="off"
          error={e.taxId} />
      </div>
      <p className="text-xs text-muted">Stored encrypted; only the last four characters are ever shown.</p>
      <SubmitButton>Save tax information</SubmitButton>
    </form>
  );
}
