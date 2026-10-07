"use client";

import { useFormStatus } from "react-dom";
import { Button } from "./button";

interface SubmitButtonProps {
  children: React.ReactNode;
  pendingLabel?: string;
  variant?: "primary" | "secondary" | "quiet" | "danger";
  /** When set, the user must confirm before the form is submitted; a function words it from the form's current values. */
  confirm?: string | ((form: HTMLFormElement) => string);
  className?: string;
}

/** Submit button that disables itself while its form's action runs. */
export function SubmitButton({ children, pendingLabel, variant = "primary", confirm, className }: SubmitButtonProps) {
  const { pending } = useFormStatus();
  return (
    <Button
      type="submit"
      variant={variant}
      disabled={pending}
      className={className}
      onClick={(event) => {
        const form = event.currentTarget.form;
        const message = typeof confirm === "function" ? (form ? confirm(form) : null) : confirm;
        if (message && !window.confirm(message)) event.preventDefault();
      }}
    >
      {pending ? (pendingLabel ?? "Saving…") : children}
    </Button>
  );
}
