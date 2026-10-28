"use client";

import Link from "next/link";
import { useActionState } from "react";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { PhoneField } from "@/components/ui/phone-field";
import { TextField } from "@/components/ui/text-field";
import { DEFAULT_PHONE_COUNTRY } from "@/lib/phone";
import type { Country } from "@/types/location";
import { type FormState, PASSWORD_MAX, PASSWORD_MIN, PASSWORD_RULES } from "@/validators/auth";
import { registerAction } from "./actions";

/** @param countries the country codes the mobile number can be in */
export function RegisterForm({ referralCode, countries }: { referralCode?: string; countries: Country[] }) {
  const [state, formAction, pending] = useActionState<FormState, FormData>(registerAction, {});
  const errors = state.fieldErrors ?? {};

  return (
    <form action={formAction} className="space-y-4" noValidate>
      {state.error && <Alert>{state.error}</Alert>}
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField
          label="First name"
          name="firstName"
          autoComplete="given-name"
          required
          defaultValue={state.values?.firstName}
          error={errors.firstName}
        />
        <TextField
          label="Last name"
          name="lastName"
          autoComplete="family-name"
          required
          defaultValue={state.values?.lastName}
          error={errors.lastName}
        />
      </div>
      <TextField
        label="Email"
        name="email"
        type="email"
        autoComplete="email"
        required
        defaultValue={state.values?.email}
        error={errors.email}
      />
      <PhoneField
        label="Mobile number"
        countries={countries}
        placeholder="98765 43210"
        required
        defaultNumber={state.values?.phone}
        defaultCountry={state.values?.phoneCountry || DEFAULT_PHONE_COUNTRY}
        error={errors.phone ?? errors.phoneCountry}
      />
      <TextField
        label="Password"
        name="password"
        type="password"
        autoComplete="new-password"
        minLength={PASSWORD_MIN}
        maxLength={PASSWORD_MAX}
        required
        error={errors.password}
      />
      <TextField
        label="Confirm password"
        name="confirmPassword"
        type="password"
        autoComplete="new-password"
        required
        error={errors.confirmPassword}
      />
      <p className="text-xs text-muted">
        Use {PASSWORD_RULES}.
      </p>
      <TextField
        label="Referral code (optional)"
        name="referralCode"
        autoComplete="off"
        autoCapitalize="characters"
        maxLength={8}
        onInput={(event) => {
          const input = event.currentTarget;
          const caret = input.selectionStart;
          input.value = input.value.toUpperCase();
          input.setSelectionRange(caret, caret);
        }}
        defaultValue={state.values?.referralCode ?? referralCode}
        error={errors.referralCode}
      />
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
        {pending ? "Creating account…" : "Create account"}
      </Button>
      <p className="text-center text-sm text-muted">
        Already have an account?{" "}
        <Link href="/login" className="font-medium text-foreground underline decoration-gold decoration-2 underline-offset-4 hover:text-gold-text">
          Sign in
        </Link>
      </p>
    </form>
  );
}
