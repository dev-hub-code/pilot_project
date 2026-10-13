"use client";

import { useActionState, useState } from "react";
import { Button } from "@/components/ui/button";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SubmitButton } from "@/components/ui/submit-button";
import { TextField } from "@/components/ui/text-field";
import type { Role } from "@/types/auth";
import type { StaffCredentials } from "@/types/staff";
import type { FormState } from "@/validators/form-state";
import { type CredentialsState, createStaffAction, resetStaffPasswordAction, updateRolesAction } from "./staff-actions";
import { copyText } from "@/utils/clipboard";

function RoleChoices({ roles, selected, error }: { roles: Role[]; selected: string[]; error?: string }) {
  return (
    <fieldset className="space-y-2">
      <legend className="text-sm font-medium text-foreground">Roles</legend>
      <div className="grid gap-2 sm:grid-cols-2">
        {roles.map((r) => (
          <label key={r.id} className="flex items-start gap-3 border border-border px-3 py-2 text-sm">
            <input type="checkbox" name="roles" value={r.name} defaultChecked={selected.includes(r.name)} className="mt-0.5 size-4" />
            <span><span className="font-medium">{r.name}</span><span className="block text-xs text-muted">{r.description}</span></span>
          </label>
        ))}
      </div>
      {error && <p className="text-xs text-rose-600 dark:text-rose-400">{error}</p>}
    </fieldset>
  );
}

/** Shows a temporary password once, with a copy button; it cannot be retrieved after leaving the page. */
export function IssuedCredentials({ credentials }: { credentials: StaffCredentials }) {
  const [copied, setCopied] = useState(false);
  return (
    <div className="space-y-3 border-l-4 border-gold bg-background p-4 text-sm">
      <p className="font-medium">Temporary password for {credentials.firstName} {credentials.lastName} ({credentials.email})</p>
      <div className="flex flex-wrap items-center gap-3">
        <code className="bg-surface px-3 py-2 font-mono text-base tracking-wider">{credentials.temporaryPassword}</code>
        <Button type="button" variant="secondary" onClick={async () => {
          // If copying fails the password stays selectable.
          if (await copyText(credentials.temporaryPassword)) setCopied(true);
        }}>{copied ? "Copied" : "Copy"}</Button>
      </div>
      <p className="text-xs text-muted">
        {credentials.emailed
          ? `We emailed these sign-in details to ${credentials.email}. `
          : "We could not email these sign-in details: share the password privately. "}
        Shown only now. They must replace it at first sign-in, before {new Date(credentials.expiresAt).toUTCString()}.
      </p>
    </div>
  );
}

export function CreateStaffForm({ roles }: { roles: Role[] }) {
  const [state, action] = useActionState<CredentialsState, FormData>(createStaffAction, {});
  const e = state.fieldErrors ?? {};
  if (state.credentials) {
    return (
      <div className="space-y-4">
        <FormFeedback state={state} />
        <IssuedCredentials credentials={state.credentials} />
        <a href={`/admin/users/${state.credentials.userId}`} className="text-sm text-gold-text hover:underline">Open their account</a>
      </div>
    );
  }
  return (
    <form action={action} className="space-y-4" noValidate>
      <FormFeedback state={state} />
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label="First name" name="firstName" defaultValue={state.values?.firstName} error={e.firstName} />
        <TextField label="Last name" name="lastName" defaultValue={state.values?.lastName} error={e.lastName} />
        <div className="sm:col-span-2">
          <TextField label="Work email" name="email" type="email" autoComplete="off" defaultValue={state.values?.email} error={e.email} />
        </div>
      </div>
      <RoleChoices roles={roles} selected={[]} error={e.roles} />
      <SubmitButton>Create employee account</SubmitButton>
      <p className="text-xs text-muted">The employee is emailed their sign-in details, with a temporary password to replace at first sign-in.</p>
    </form>
  );
}

export function RolesForm({ userId, roles, current }: { userId: string; roles: Role[]; current: string[] }) {
  const [state, action] = useActionState<FormState, FormData>(updateRolesAction.bind(null, userId), {});
  return (
    <form action={action} className="space-y-4" noValidate>
      <FormFeedback state={state} />
      <RoleChoices roles={roles} selected={current} />
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
