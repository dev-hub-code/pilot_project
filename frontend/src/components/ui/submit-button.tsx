"use client";

import { useFormStatus } from "react-dom";
import { Button } from "./button";
import { labelOf, useConfirm } from "./confirm-dialog";

/** Actions that undo or end something get a red confirm button. */
const DESTRUCTIVE = /^(cancel|remove|delete|suspend|close|reject|revoke|deactivate|mark .* failed)/i;

interface SubmitButtonProps {
  children: React.ReactNode;
  pendingLabel?: string;
  variant?: "primary" | "secondary" | "quiet" | "danger";
  /** When set, the user must confirm before the form is submitted; a function words it from the form's current values. */
  confirm?: string | ((form: HTMLFormElement) => string);
  className?: string;
  /** Not ready to submit yet (e.g. what it would send is still being corrected). */
  disabled?: boolean;
}

/** Submit button that disables itself while its form's action runs. */
export function SubmitButton({ children, pendingLabel, variant = "primary", confirm, className, disabled }: SubmitButtonProps) {
  const { pending } = useFormStatus();
  const { ask, dialog } = useConfirm();
  const label = labelOf(children);
  return (
    <>
    <Button
      type="submit"
      variant={variant}
      disabled={pending || disabled}
      className={className}
      onClick={(event) => {
        const button = event.currentTarget;
        const form = button.form;
        const message = typeof confirm === "function" ? (form ? confirm(form) : null) : confirm;
        if (!message || !form) return;
        // Asked in the app's own window; the form is submitted (by this button) once confirmed.
        event.preventDefault();
        void ask(message, { confirmLabel: label, danger: variant === "danger" || DESTRUCTIVE.test(label) })
          .then((confirmed) => { if (confirmed) form.requestSubmit(button); });
      }}
    >
      {pending ? (pendingLabel ?? "Saving…") : children}
    </Button>
    {dialog}
    </>
  );
}
