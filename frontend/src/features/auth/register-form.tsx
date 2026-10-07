"use client";

import Link from "next/link";
import { useActionState } from "react";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { TextField } from "@/components/ui/text-field";
import { type FormState, PASSWORD_MIN } from "@/validators/auth";
import { registerAction } from "./actions";

export function RegisterForm() {
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
      <TextField
        label="Password"
        name="password"
        type="password"
        autoComplete="new-password"
        minLength={PASSWORD_MIN}
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
        At least {PASSWORD_MIN} characters. A memorable passphrase works well.
      </p>
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
