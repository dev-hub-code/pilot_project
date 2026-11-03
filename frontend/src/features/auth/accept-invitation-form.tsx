"use client";

import Link from "next/link";
import { useActionState } from "react";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { PhoneField } from "@/components/ui/phone-field";
import { TextField } from "@/components/ui/text-field";
import { DEFAULT_PHONE_COUNTRY } from "@/lib/phone";
import type { Country } from "@/types/location";
import type { FormState } from "@/validators/auth";
import { acceptInvitationAction } from "./actions";
import { NewPasswordFields } from "./new-password-fields";

interface Props {
  token: string;
  email: string;
  firstName: string;
  lastName: string;
  countries: Country[];
}

/** The invited client's own details and password; the email address is fixed to the one invited. */
export function AcceptInvitationForm({ token, email, firstName, lastName, countries }: Props) {
  const [state, formAction, pending] = useActionState<FormState, FormData>(acceptInvitationAction.bind(null, token), {});
  const errors = state.fieldErrors ?? {};

  return (
    <form action={formAction} className="space-y-4" noValidate>
      {state.error && <Alert>{state.error}</Alert>}
      <TextField label="Email" name="email" type="email" value={email} readOnly disabled />
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label="First name" name="firstName" autoComplete="given-name" required
          defaultValue={state.values?.firstName ?? firstName} error={errors.firstName} />
        <TextField label="Last name" name="lastName" autoComplete="family-name" required
          defaultValue={state.values?.lastName ?? lastName} error={errors.lastName} />
      </div>
      <PhoneField label="Mobile number" countries={countries} placeholder="98765 43210" required
        defaultNumber={state.values?.phone} defaultCountry={state.values?.phoneCountry || DEFAULT_PHONE_COUNTRY}
        error={errors.phone ?? errors.phoneCountry} />
      <NewPasswordFields errors={errors} />
      <div className="space-y-1">
        <label className="flex items-start gap-3 text-sm">
          <input type="checkbox" name="acceptTerms" className="mt-1 size-4 accent-gold"
            defaultChecked={state.values?.acceptTerms === "on"}
            aria-invalid={errors.acceptTerms ? true : undefined}
            aria-describedby={errors.acceptTerms ? "accept-terms-error" : undefined} />
          <span>
            I have read and accept the{" "}
            <Link href="/terms" target="_blank" className="font-medium underline decoration-gold decoration-2 underline-offset-4 hover:text-gold-text">
              Terms & conditions
            </Link>
          </span>
        </label>
        {errors.acceptTerms && <p id="accept-terms-error" className="text-xs text-rose-600 dark:text-rose-400">{errors.acceptTerms}</p>}
      </div>
      <Button type="submit" className="w-full" disabled={pending}>
        {pending ? "Opening account…" : "Open my account"}
      </Button>
    </form>
  );
}
