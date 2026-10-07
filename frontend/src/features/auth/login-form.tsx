"use client";

import Link from "next/link";
import { useActionState } from "react";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { TextField } from "@/components/ui/text-field";
import type { FormState } from "@/validators/auth";
import { loginAction } from "./actions";

export function LoginForm({ next }: { next?: string }) {
  const [state, formAction, pending] = useActionState<FormState, FormData>(loginAction, {});

  return (
    <form action={formAction} className="space-y-4" noValidate>
      {state.error && <Alert>{state.error}</Alert>}
      {next && <input type="hidden" name="next" value={next} />}
      <TextField
        label="Email"
        name="email"
        type="email"
        autoComplete="username"
        required
        defaultValue={state.values?.email}
        error={state.fieldErrors?.email}
      />
      <TextField
        label="Password"
        name="password"
        type="password"
        autoComplete="current-password"
        required
        error={state.fieldErrors?.password}
      />
      <Button type="submit" className="w-full" disabled={pending}>
        {pending ? "Signing in…" : "Sign in"}
      </Button>
      <p className="text-center text-sm text-muted">
        New to SeaLease?{" "}
        <Link href="/register" className="font-medium text-foreground underline decoration-gold decoration-2 underline-offset-4 hover:text-gold-text">
          Create an account
        </Link>
      </p>
    </form>
  );
}
