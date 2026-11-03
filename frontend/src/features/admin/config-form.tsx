"use client";

import { useState, useTransition, type FormEvent, type ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { FormFeedback } from "@/components/ui/form-feedback";
import type { FormState } from "@/validators/form-state";

type Action = (state: FormState, formData: FormData) => Promise<FormState>;

/**
 * A settings form whose fields are rendered by the server page. Submitted without React's form-action reset, so a
 * rejected save keeps what the admin typed; field errors are listed above the button.
 */
export function ConfigForm({ action, children, submitLabel = "Save", className = "space-y-4" }: {
  action: Action;
  children: ReactNode;
  submitLabel?: string;
  className?: string;
}) {
  const [state, setState] = useState<FormState>({});
  const [pending, start] = useTransition();
  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    start(async () => setState(await action(state, data)));
  };
  const errors = Object.values(state.fieldErrors ?? {}).filter(Boolean);
  return (
    <form onSubmit={submit} className={className} noValidate>
      {children}
      <FormFeedback state={state} />
      {errors.length > 0 && (
        <ul className="list-disc pl-5 text-sm text-rose-600 dark:text-rose-400" role="alert">
          {errors.map((e) => <li key={e}>{e}</li>)}
        </ul>
      )}
      <Button type="submit" variant="secondary" disabled={pending}>{pending ? "Saving…" : submitLabel}</Button>
    </form>
  );
}
