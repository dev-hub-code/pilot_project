"use client";

import { useActionState } from "react";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { TextField } from "@/components/ui/text-field";
import { type FormState, PASSWORD_MIN } from "@/validators/auth";
import { changePasswordAction } from "./actions";

export function ChangePasswordForm({ temporary, returnTo }: { temporary: boolean; returnTo?: string }) {
  const [state, formAction, pending] = useActionState<FormState, FormData>(changePasswordAction, {});
  const errors = state.fieldErrors ?? {};
  return (
    <form action={formAction} className="space-y-4" noValidate>
      {state.error && <Alert>{state.error}</Alert>}
      {returnTo && <input type="hidden" name="returnTo" value={returnTo} />}
      <TextField label={temporary ? "Temporary password" : "Current password"} name="currentPassword" type="password"
        autoComplete="current-password" required error={errors.currentPassword} />
      <TextField label="New password" name="newPassword" type="password" autoComplete="new-password" minLength={PASSWORD_MIN}
        required error={errors.newPassword} />
      <TextField label="Confirm new password" name="confirmPassword" type="password" autoComplete="new-password" required
        error={errors.confirmPassword} />
      <p className="text-xs text-muted">At least {PASSWORD_MIN} characters. Other devices will be signed out.</p>
      <Button type="submit" className="w-full" disabled={pending}>{pending ? "Saving…" : "Set new password"}</Button>
    </form>
  );
}
