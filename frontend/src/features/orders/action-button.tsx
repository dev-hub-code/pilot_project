"use client";

import { useActionState } from "react";
import { FormFeedback } from "@/components/ui/form-feedback";
import { SubmitButton } from "@/components/ui/submit-button";
import type { FormState } from "@/validators/form-state";

/** A one-click action (pay, cancel) that reports its outcome inline. */
export function ActionButton({ action, label, pendingLabel, variant = "primary", confirm, className }: {
  action: (state: FormState) => Promise<FormState>;
  label: string;
  pendingLabel?: string;
  variant?: "primary" | "secondary" | "quiet" | "danger";
  confirm?: string;
  className?: string;
}) {
  const [state, formAction] = useActionState<FormState>(action, {});
  return (
    <form action={formAction} className="space-y-3">
      <FormFeedback state={state} />
      <SubmitButton variant={variant} confirm={confirm} pendingLabel={pendingLabel ?? "Working…"} className={className}>
        {label}
      </SubmitButton>
    </form>
  );
}
