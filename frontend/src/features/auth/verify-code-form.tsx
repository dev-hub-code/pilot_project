"use client";

import { useActionState } from "react";
import { Alert } from "@/components/ui/alert";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextField } from "@/components/ui/text-field";
import type { FormState } from "@/validators/auth";
import { verifyCodeAction } from "./actions";

/** Enter the 6-digit code from the verification email (the alternative to clicking its link). */
export function VerifyCodeForm({ email }: { email?: string }) {
  const [state, formAction] = useActionState<FormState, FormData>(verifyCodeAction, {});
  const knownEmail = state.values?.email ?? email;

  return (
    <form action={formAction} className="space-y-4" noValidate>
      {state.error && <Alert>{state.error}</Alert>}
      {email ? (
        <input type="hidden" name="email" value={email} />
      ) : (
        <TextField
          label="Email"
          name="email"
          type="email"
          autoComplete="email"
          required
          defaultValue={knownEmail}
          error={state.fieldErrors?.email}
        />
      )}
      <TextField
        label="Verification code"
        name="code"
        inputMode="numeric"
        autoComplete="one-time-code"
        pattern="[0-9]{6}"
        maxLength={6}
        placeholder="6-digit code"
        required
        error={state.fieldErrors?.code}
      />
      <SubmitButton pendingLabel="Checking…" className="w-full">
        Verify email
      </SubmitButton>
    </form>
  );
}
