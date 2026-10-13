"use client";

import { useActionState } from "react";
import { Alert } from "@/components/ui/alert";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextField } from "@/components/ui/text-field";
import { type FormState, PASSWORD_MAX, PASSWORD_MIN, PASSWORD_RULES } from "@/validators/auth";
import { resetPasswordAction } from "./actions";

/** The 6-digit code from the reset email and the new password. */
export function ResetPasswordForm({ email }: { email?: string }) {
  const [state, formAction] = useActionState<FormState, FormData>(resetPasswordAction, {});
  const e = state.fieldErrors ?? {};
  return (
    <form action={formAction} className="space-y-4" noValidate>
      {state.error && <Alert>{state.error}</Alert>}
      {email ? (
        <input type="hidden" name="email" value={email} />
      ) : (
        <TextField label="Email" name="email" type="email" autoComplete="email" required
          defaultValue={state.values?.email} error={e.email} />
      )}
      <TextField label="Reset code" name="code" inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}"
        maxLength={6} placeholder="6-digit code" required error={e.code} />
      <TextField label="New password" name="newPassword" type="password" autoComplete="new-password"
        minLength={PASSWORD_MIN} maxLength={PASSWORD_MAX} required error={e.newPassword} />
      <TextField label="Confirm new password" name="confirmPassword" type="password" autoComplete="new-password" required
        error={e.confirmPassword} />
      <p className="text-xs text-muted">Use {PASSWORD_RULES}. You will be signed out on every device.</p>
      <SubmitButton pendingLabel="Saving…" className="w-full">Set new password</SubmitButton>
    </form>
  );
}
