"use client";

import { KeepValues } from "@/components/ui/keep-values";
import Link from "next/link";
import { useActionState } from "react";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { TextField } from "@/components/ui/text-field";
import type { FormState } from "@/validators/auth";
import { loginAction } from "./actions";

/**
 * The sign-in form, shared by the investor and staff sign-in pages. Anything that differs comes from the page (rendered
 * on the server), so this script holds nothing about either page that the other could show.
 */
export function LoginForm({ next, portal, emailLabel = "Email", footer }: {
  next?: string;
  /** Sent with the sign-in, so the server knows which page it came from; none for investors. */
  portal?: string;
  emailLabel?: string;
  footer?: React.ReactNode;
}) {
  const [state, formAction, pending] = useActionState<FormState, FormData>(loginAction, {});

  return (
    <form action={formAction} className="space-y-4" noValidate>
      <KeepValues state={state} />
      {state.error && (
        <Alert>{state.error}</Alert>
      )}
      {next && <input type="hidden" name="next" value={next} />}
      {portal && <input type="hidden" name="portal" value={portal} />}
      <TextField
        label={emailLabel}
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
      <p className="-mt-2 text-right text-sm">
        <Link href="/forgot-password" className="text-muted underline underline-offset-4 hover:text-foreground">
          Forgot password?
        </Link>
      </p>
      <Button type="submit" className="w-full" disabled={pending}>
        {pending ? "Signing in…" : "Sign in"}
      </Button>
      {footer}
    </form>
  );
}
