"use client";

import { useActionState } from "react";
import { Alert } from "@/components/ui/alert";
import { Notice } from "@/components/ui/notice";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextField } from "@/components/ui/text-field";
import type { FormState } from "@/validators/auth";
import { resendVerificationAction } from "./actions";

export function ResendVerificationForm({ email }: { email?: string }) {
  const [state, formAction] = useActionState<FormState, FormData>(resendVerificationAction, {});

  return (
    <form action={formAction} className="space-y-4" noValidate>
      {state.error && <Alert>{state.error}</Alert>}
      {state.success && <Notice tone="success">{state.success}</Notice>}
      <TextField
        label="Email"
        name="email"
        type="email"
        autoComplete="email"
        required
        defaultValue={state.values?.email ?? email}
        error={state.fieldErrors?.email}
      />
      <SubmitButton variant="secondary" pendingLabel="Sending…" className="w-full">
        Send a new code
      </SubmitButton>
    </form>
  );
}
