"use client";

import Link from "next/link";
import { useActionState, useState } from "react";
import { Button } from "@/components/ui/button";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextField } from "@/components/ui/text-field";
import type { Role } from "@/types/auth";
import { type RoleDepartment, RoleChoices } from "./role-choices";
import type { StaffCredentials } from "@/types/staff";
import type { FormState } from "@/validators/form-state";
import { type CredentialsState, createStaffAction, resetStaffPasswordAction, updateRolesAction } from "./staff-actions";
import { copyText } from "@/utils/clipboard";
import { formatDateTime } from "@/utils/format";
import { EmailText } from "@/components/ui/email-text";
import { Check, Copy, KeyRound, Mail, MailWarning, UserRound } from "lucide-react";

/** Shows a temporary password once, with a copy button; it cannot be retrieved after leaving the page. */
export function IssuedCredentials({ credentials }: { credentials: StaffCredentials }) {
  const [copied, setCopied] = useState(false);
  return (
    <div className="space-y-4 rounded-2xl border border-gold/30 bg-gold/5 p-5 text-sm">
      <div className="flex items-start gap-3">
        <span aria-hidden="true" className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-gold/15 text-gold-text">
          <KeyRound className="size-5" />
        </span>
        <p className="min-w-0">
          <span className="block font-semibold">Temporary password for {credentials.firstName} {credentials.lastName}</span>
          <span className="block text-xs break-all text-muted"><EmailText>{credentials.email}</EmailText></span>
        </p>
      </div>
      <div className="flex flex-wrap items-center gap-2">
        <code className="min-w-0 flex-1 rounded-xl border border-border bg-surface px-4 py-3 font-mono text-lg tracking-wider break-all select-all">{credentials.temporaryPassword}</code>
        <Button type="button" variant="secondary" className="gap-2" onClick={async () => {
          // If copying fails the password stays selectable.
          if (await copyText(credentials.temporaryPassword)) setCopied(true);
        }}>
          {copied ? <Check aria-hidden="true" className="size-4" /> : <Copy aria-hidden="true" className="size-4" />}
          {copied ? "Copied" : "Copy"}
        </Button>
      </div>
      <p className="flex items-start gap-2 text-xs text-muted">
        {credentials.emailed
          ? <Mail aria-hidden="true" className="mt-0.5 size-3.5 shrink-0 text-gold-text" />
          : <MailWarning aria-hidden="true" className="mt-0.5 size-3.5 shrink-0 text-amber-600" />}
        <span>
        {credentials.emailed
          ? `We emailed these sign-in details to ${credentials.email}. `
          : "We could not email these sign-in details: share the password privately. "}
        Shown only now. They must replace it at first sign-in, before {formatDateTime(credentials.expiresAt)}.
        </span>
      </p>
    </div>
  );
}

export function CreateStaffForm({ roles, departments }: { roles: Role[]; departments?: RoleDepartment[] }) {
  const [state, action] = useActionState<CredentialsState, FormData>(createStaffAction, {});
  const e = state.fieldErrors ?? {};
  if (state.credentials) {
    return (
      <div className="space-y-4">
        <FormFeedback state={state} />
        <IssuedCredentials credentials={state.credentials} />
        <div className="flex flex-wrap gap-2">
          <Link href={`/admin/staff/${state.credentials.userId}`}
            className="inline-flex min-h-10 items-center gap-1.5 rounded-lg bg-ink px-4 text-sm font-medium text-on-ink transition-opacity hover:opacity-90 dark:bg-gold dark:text-on-gold">
            <UserRound aria-hidden="true" className="size-4" /> Open their account
          </Link>
        </div>
      </div>
    );
  }
  return (
    <form action={action} className="space-y-6" noValidate>
      <FormFeedback state={state} />
      <fieldset className="space-y-3">
        <legend className="flex items-center gap-2 text-sm font-semibold text-foreground">
          <UserRound aria-hidden="true" className="size-4 text-gold-text" /> Employee
        </legend>
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label="First name" name="firstName" defaultValue={state.values?.firstName} error={e.firstName} />
        <TextField label="Last name" name="lastName" defaultValue={state.values?.lastName} error={e.lastName} />
        <div className="sm:col-span-2">
          <TextField label="Work email" name="email" type="email" autoComplete="off" defaultValue={state.values?.email} error={e.email} />
        </div>
      </div>
      </fieldset>
      <RoleChoices roles={roles} departments={departments} selected={[]} error={e.roles} />
      <div className="border-t border-border pt-5">
        <SubmitButton>Create employee account</SubmitButton>
      </div>
    </form>
  );
}

export function RolesForm({ userId, roles, current, departments }: {
  userId: string; roles: Role[]; current: string[]; departments?: RoleDepartment[];
}) {
  const [state, action] = useActionState<FormState, FormData>(updateRolesAction.bind(null, userId), {});
  return (
    <form action={action} className="space-y-4" noValidate>
      <FormFeedback state={state} />
      <RoleChoices roles={roles} departments={departments} selected={current} />
      <SubmitButton variant="secondary" confirm="Change this user's roles? They are signed out of current sessions.">Save roles</SubmitButton>
    </form>
  );
}

export function ResetPasswordForm({ userId }: { userId: string }) {
  const [state, action] = useActionState<CredentialsState>(resetStaffPasswordAction.bind(null, userId), {});
  return (
    <form action={action} className="space-y-3">
      <FormFeedback state={state} />
      {state.credentials ? (
        <IssuedCredentials credentials={state.credentials} />
      ) : (
        <SubmitButton variant="secondary" confirm="Issue a new temporary password? Their current password stops working and they are signed out everywhere.">
          Issue temporary password
        </SubmitButton>
      )}
    </form>
  );
}
