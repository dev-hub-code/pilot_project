"use client";

import { useActionState } from "react";
import { Alert } from "@/components/ui/alert";
import { Notice } from "@/components/ui/notice";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextField } from "@/components/ui/text-field";
import type { FormState } from "@/validators/auth";
import { requestPasswordResetAction } from "./actions";

/**
 * Asks for a password reset code. With {@code stay}, it only sends a new code (on the reset page) instead of
 * moving on to it.
 */
export function ForgotPasswordForm({ email, stay = false }: { email?: string; stay?: boolean }) {
  const [state, formAction] = useActionState<FormState, FormData>(requestPasswordResetAction, {});
  return (
    <form action={formAction} className="space-y-4" noValidate>
      {state.error && <Alert>{state.error}</Alert>}
      {state.success && <Notice tone="success">{state.success}</Notice>}
      {stay && <input type="hidden" name="stay" value="1" />}
      {stay && email ? (
        <input type="hidden" name="email" value={email} />
      ) : (
        <TextField label="Email" name="email" type="email" autoComplete="email" required
          defaultValue={state.values?.email ?? email} error={state.fieldErrors?.email} />
      )}
      <SubmitButton variant={stay ? "secondary" : "primary"} pendingLabel="Sending…" className="w-full">
        {stay ? "Send a new code" : "Email me a reset code"}
      </SubmitButton>
    </form>
  );
}
